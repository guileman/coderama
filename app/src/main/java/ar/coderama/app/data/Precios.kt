package ar.coderama.app.data

import android.content.Context
import ar.coderama.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import java.io.File

/**
 * Precios del SEPA ya procesados por GitHub Actions y publicados en GitHub Pages.
 * Se descarga solo el archivo de la localidad elegida y se guarda en el teléfono.
 */
class Precios(context: Context) {

    private val base = BuildConfig.DATA_BASE_URL.trimEnd('/') + "/"
    private val carpeta = File(context.filesDir, "precios").apply { mkdirs() }
    private val mutex = Mutex()

    private var cacheIndice: Indice? = null
    private var localidadCargada: String? = null
    private var productos: Map<String, JsonArray> = emptyMap()
    private var sucursales: List<List<String>> = emptyList()
    private var fechaLocalidad: String = ""

    val configurado: Boolean get() = !base.contains("USUARIO")

    suspend fun indice(forzar: Boolean = false): Indice? = mutex.withLock {
        val archivo = File(carpeta, "index.json")
        cacheIndice?.takeIf { !forzar && !viejo(archivo, 3) }?.let { return@withLock it }
        val texto = descargarOCache("index.json", archivo, horas = 3, forzar = forzar) ?: return@withLock null
        cacheIndice = runCatching { Red.json.decodeFromString(Indice.serializer(), texto) }.getOrNull()
        cacheIndice
    }

    suspend fun buscar(codigo: String, ajustes: Ajustes): PreciosProducto? {
        val iso = ajustes.provinciaIso ?: return null
        val loc = ajustes.localidadId ?: return null
        val idx = indice() ?: return null
        cargarLocalidad(iso, loc, idx.fecha)
        val entrada = productos[normalizarCodigo(codigo)] ?: return null
        val descripcion = entrada.getOrNull(0).str() ?: ""
        val marca = entrada.getOrNull(1).str() ?: ""
        val filas = entrada.getOrNull(2).arr()?.mapNotNull { f ->
            val a = f.arr() ?: return@mapNotNull null
            val cadenaId = a.getOrNull(0).str() ?: return@mapNotNull null
            val suc = a.getOrNull(4).num()?.toInt()?.let { sucursales.getOrNull(it) }
            FilaPrecio(
                cadenaId = cadenaId,
                cadena = idx.cadenas[cadenaId] ?: cadenaId,
                minimo = a.getOrNull(1).num() ?: return@mapNotNull null,
                maximo = a.getOrNull(2).num() ?: 0.0,
                sucursales = a.getOrNull(3).num()?.toInt() ?: 1,
                sucursalMasBarata = suc?.getOrNull(1)?.takeIf { it.isNotBlank() },
                direccion = suc?.getOrNull(2)?.takeIf { it.isNotBlank() },
                promo = a.getOrNull(5).num(),
                elegida = ajustes.cadenas.isEmpty() || cadenaId in ajustes.cadenas,
            )
        }?.sortedBy { it.promo?.coerceAtMost(it.minimo) ?: it.minimo } ?: emptyList()
        if (filas.isEmpty()) return null
        return PreciosProducto(descripcion, marca, fechaLocalidad, filas)
    }

    suspend fun actualizar(ajustes: Ajustes) {
        indice(forzar = true)
        val iso = ajustes.provinciaIso ?: return
        val loc = ajustes.localidadId ?: return
        mutex.withLock { localidadCargada = null }
        File(carpeta, "$iso-$loc.json").delete()
        cargarLocalidad(iso, loc, cacheIndice?.fecha ?: "")
    }

    private suspend fun cargarLocalidad(iso: String, loc: String, fechaIndice: String) = mutex.withLock {
        val clave = "$iso/$loc"
        if (localidadCargada == clave && (fechaIndice.isEmpty() || fechaIndice == fechaLocalidad)) return@withLock
        val archivo = File(carpeta, "$iso-$loc.json")
        val texto = descargarOCache("p/$iso/$loc.json", archivo, horas = 20, forzar = false) ?: return@withLock
        withContext(Dispatchers.Default) {
            val raiz = Red.json.parseToJsonElement(texto).obj() ?: return@withContext
            fechaLocalidad = raiz["fecha"].str() ?: ""
            sucursales = raiz["sucursales"].arr()?.map { s -> s.arr()?.map { it.str() ?: "" } ?: emptyList() }
                ?: emptyList()
            productos = raiz["productos"].obj()?.mapValues { it.value as JsonArray } ?: emptyMap()
            localidadCargada = clave
        }
        // Si el índice es más nuevo que el archivo guardado, se vuelve a bajar la próxima vez
        if (fechaIndice.isNotEmpty() && fechaLocalidad.isNotEmpty() && fechaIndice > fechaLocalidad) {
            archivo.setLastModified(0)
        }
    }

    private suspend fun descargarOCache(ruta: String, archivo: File, horas: Int, forzar: Boolean): String? =
        withContext(Dispatchers.IO) {
            if (!forzar && archivo.exists() && !viejo(archivo, horas)) return@withContext archivo.readText()
            val bajado = runCatching { Red.texto(base + ruta) }.getOrNull()
            if (bajado != null) {
                archivo.writeText(bajado)
                bajado
            } else if (archivo.exists()) {
                archivo.readText()
            } else {
                null
            }
        }

    private fun viejo(archivo: File, horas: Int): Boolean =
        !archivo.exists() || System.currentTimeMillis() - archivo.lastModified() > horas * 3_600_000L

    suspend fun alertasAnmat(): List<AlertaAnmat> {
        val archivo = File(carpeta, "alertas.json")
        val texto = descargarOCache("alertas.json", archivo, horas = 24, forzar = false) ?: return emptyList()
        val raiz = runCatching { Red.json.parseToJsonElement(texto).obj() }.getOrNull() ?: return emptyList()
        return raiz["alertas"].arr()?.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            AlertaAnmat(
                categoria = o["categoria"].str() ?: "",
                titulo = o["titulo"].str() ?: return@mapNotNull null,
                fecha = o["fecha"].str(),
                url = o["url"].str() ?: "",
                claves = o["claves"].strings(),
            )
        } ?: emptyList()
    }
}
