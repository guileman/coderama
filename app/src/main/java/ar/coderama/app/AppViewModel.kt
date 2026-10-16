package ar.coderama.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.coderama.app.data.AlertaAnmat
import ar.coderama.app.data.Ajustes
import ar.coderama.app.data.Almacen
import ar.coderama.app.data.Escaneo
import ar.coderama.app.data.Fuente
import ar.coderama.app.data.Indice
import ar.coderama.app.data.LocalidadIdx
import ar.coderama.app.data.Perfil
import ar.coderama.app.data.Precios
import ar.coderama.app.data.PreciosProducto
import ar.coderama.app.data.Producto
import ar.coderama.app.data.Productos
import ar.coderama.app.data.ProvinciaIdx
import ar.coderama.app.logica.EficienciaEnergetica
import ar.coderama.app.logica.Etiqueta
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Ficha(
    val producto: Producto,
    val precios: PreciosProducto?,
    val anmat: List<AlertaAnmat>,
    val eficiencia: EficienciaEnergetica? = null,
    val textoEtiqueta: String? = null,
)

sealed interface EstadoProducto {
    data object Vacio : EstadoProducto
    data object Cargando : EstadoProducto
    data class Listo(val ficha: Ficha) : EstadoProducto
    data class NoEncontrado(val codigo: String) : EstadoProducto
}

sealed interface EstadoIndice {
    data object Cargando : EstadoIndice
    data class Listo(val indice: Indice) : EstadoIndice
    data class Error(val mensaje: String) : EstadoIndice
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val almacen = Almacen(app)
    private val productos = Productos()
    val precios = Precios(app)

    val ajustes: StateFlow<Ajustes?> = almacen.ajustes
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _producto = MutableStateFlow<EstadoProducto>(EstadoProducto.Vacio)
    val producto: StateFlow<EstadoProducto> = _producto.asStateFlow()

    private val _indice = MutableStateFlow<EstadoIndice>(EstadoIndice.Cargando)
    val indice: StateFlow<EstadoIndice> = _indice.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    private var ultimoCodigo: String? = null

    init {
        cargarIndice()
    }

    fun cargarIndice(forzar: Boolean = false) {
        viewModelScope.launch {
            _indice.value = EstadoIndice.Cargando
            val idx = runCatching { precios.indice(forzar) }.getOrNull()
            _indice.value = when {
                idx != null && idx.provincias.isNotEmpty() -> EstadoIndice.Listo(idx)
                !precios.configurado -> EstadoIndice.Error("La app no tiene configurada la dirección de los datos de precios.")
                else -> EstadoIndice.Error("Todavía no hay datos de precios publicados o no hay conexión.")
            }
        }
    }

    // ------------------------------------------------------------ productos

    fun cargar(codigo: String) {
        if (codigo == ultimoCodigo && _producto.value is EstadoProducto.Listo) return
        ultimoCodigo = codigo
        viewModelScope.launch {
            _producto.value = EstadoProducto.Cargando
            val a = ajustes.value ?: almacen.ajustes.first()
            val prod = async { runCatching { productos.buscar(codigo) }.getOrNull() }
            val prec = async { runCatching { precios.buscar(codigo, a) }.getOrNull() }
            val anmat = async { runCatching { precios.alertasAnmat() }.getOrDefault(emptyList()) }
            val p = prod.await()
            val pr = prec.await()
            val producto = p ?: pr?.let {
                Producto(codigo = codigo, nombre = it.descripcion.ifBlank { "Producto $codigo" }, marca = it.marca.ifBlank { null }, fuente = Fuente.SEPA)
            }
            if (producto == null) {
                _producto.value = EstadoProducto.NoEncontrado(codigo)
                return@launch
            }
            _producto.value = EstadoProducto.Listo(Ficha(producto, pr, anmat.await()))
            almacen.actualizar { aj ->
                val nuevo = Escaneo(codigo, producto.nombre, producto.marca, producto.imagen, System.currentTimeMillis())
                aj.copy(historial = (listOf(nuevo) + aj.historial.filter { it.codigo != codigo }).take(30))
            }
        }
    }

    fun analizarEtiqueta(texto: String, codigo: String?) {
        ultimoCodigo = null
        viewModelScope.launch {
            _producto.value = EstadoProducto.Cargando
            val a = ajustes.value ?: almacen.ajustes.first()
            val pr = codigo?.let { runCatching { precios.buscar(it, a) }.getOrNull() }
            val anmat = runCatching { precios.alertasAnmat() }.getOrDefault(emptyList())
            var prod = Etiqueta.aProducto(texto, codigo)
            if (pr != null && prod.nombre == "Producto de la etiqueta") prod = prod.copy(nombre = pr.descripcion)
            _producto.value = EstadoProducto.Listo(
                Ficha(prod, pr, anmat, eficiencia = Etiqueta.eficiencia(texto), textoEtiqueta = texto),
            )
        }
    }

    // ------------------------------------------------------------ ajustes

    fun guardarUbicacion(provincia: ProvinciaIdx?, provinciaNombre: String, localidad: LocalidadIdx?, localidadNombre: String) {
        viewModelScope.launch {
            almacen.actualizar { a ->
                val cambio = a.localidadId != localidad?.id
                a.copy(
                    provinciaIso = provincia?.iso,
                    provinciaNombre = provinciaNombre,
                    localidadId = localidad?.id,
                    localidadNombre = localidadNombre,
                    cadenas = if (cambio) localidad?.cadenas?.toSet() ?: emptySet() else a.cadenas,
                )
            }
            ultimoCodigo = null
        }
    }

    fun guardarCadenas(cadenas: Set<String>) {
        viewModelScope.launch { almacen.actualizar { it.copy(cadenas = cadenas) }; ultimoCodigo = null }
    }

    fun guardarPerfil(perfil: Perfil) {
        viewModelScope.launch {
            almacen.actualizar { a ->
                val existe = a.perfiles.any { it.id == perfil.id }
                a.copy(perfiles = if (existe) a.perfiles.map { if (it.id == perfil.id) perfil else it } else a.perfiles + perfil)
            }
        }
    }

    fun borrarPerfil(id: String) {
        viewModelScope.launch { almacen.actualizar { a -> a.copy(perfiles = a.perfiles.filter { it.id != id }) } }
    }

    fun terminarOnboarding() {
        viewModelScope.launch { almacen.actualizar { it.copy(onboardingHecho = true) } }
    }

    fun borrarHistorial() {
        viewModelScope.launch { almacen.actualizar { it.copy(historial = emptyList()) } }
    }

    fun actualizarPrecios() {
        viewModelScope.launch {
            val a = ajustes.value ?: return@launch
            _mensaje.value = "Actualizando precios…"
            val ok = runCatching { precios.actualizar(a) }.isSuccess
            cargarIndice()
            ultimoCodigo = null
            _mensaje.value = if (ok) "Precios actualizados" else "No se pudo actualizar. Revisá la conexión."
        }
    }

    fun mensajeVisto() {
        _mensaje.value = null
    }
}
