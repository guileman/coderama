package ar.coderama.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ar.coderama.app.EstadoIndice
import ar.coderama.app.data.Actividad
import ar.coderama.app.data.Indice
import ar.coderama.app.data.LocalidadIdx
import ar.coderama.app.data.Perfil
import ar.coderama.app.data.ProvinciaIdx
import ar.coderama.app.data.Restriccion
import ar.coderama.app.data.Sexo
import ar.coderama.app.logica.Nutricion
import ar.coderama.app.logica.normalizar
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.cos

val PROVINCIAS_FIJAS = listOf(
    "Buenos Aires", "Ciudad Autónoma de Buenos Aires", "Catamarca", "Chaco", "Chubut", "Córdoba", "Corrientes",
    "Entre Ríos", "Formosa", "Jujuy", "La Pampa", "La Rioja", "Mendoza", "Misiones", "Neuquén", "Río Negro",
    "Salta", "San Juan", "San Luis", "Santa Cruz", "Santa Fe", "Santiago del Estero", "Tierra del Fuego", "Tucumán",
)

// ------------------------------------------------------------------ ubicación

@Composable
fun SelectorUbicacion(
    estado: EstadoIndice,
    provinciaActual: String?,
    localidadActual: String?,
    onElegir: (ProvinciaIdx?, String, LocalidadIdx?, String) -> Unit,
    onReintentar: () -> Unit,
) {
    when (estado) {
        EstadoIndice.Cargando -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Cargando provincias y localidades…")
        }
        is EstadoIndice.Error -> UbicacionManual(estado.mensaje, provinciaActual, localidadActual, onElegir, onReintentar)
        is EstadoIndice.Listo -> UbicacionConDatos(estado.indice, provinciaActual, localidadActual, onElegir)
    }
}

@Composable
private fun UbicacionManual(
    mensaje: String,
    provinciaActual: String?,
    localidadActual: String?,
    onElegir: (ProvinciaIdx?, String, LocalidadIdx?, String) -> Unit,
    onReintentar: () -> Unit,
) {
    var prov by remember { mutableStateOf(provinciaActual ?: "") }
    var loc by remember { mutableStateOf(localidadActual ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Aclaracion("$mensaje Podés elegir tu zona igual y los precios aparecen cuando haya datos.")
        TextButton(onClick = onReintentar) { Text("Reintentar") }
        Desplegable("Provincia", prov, PROVINCIAS_FIJAS) { prov = it; onElegir(null, it, null, loc) }
        OutlinedTextField(
            value = loc,
            onValueChange = { loc = it; onElegir(null, prov, null, it) },
            label = { Text("Ciudad o localidad") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun UbicacionConDatos(
    indice: Indice,
    provinciaActual: String?,
    localidadActual: String?,
    onElegir: (ProvinciaIdx?, String, LocalidadIdx?, String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var provincia by remember { mutableStateOf(indice.provincias.firstOrNull { it.nombre == provinciaActual }) }
    var busqueda by remember { mutableStateOf("") }
    var elegida by remember { mutableStateOf(localidadActual) }
    var ubicando by remember { mutableStateOf(false) }
    var avisoGps by remember { mutableStateOf<String?>(null) }

    fun ubicar() {
        ubicando = true
        avisoGps = null
        scope.launch {
            val cliente = LocationServices.getFusedLocationProviderClient(ctx)
            val pos = runCatching { cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
                ?: runCatching { cliente.lastLocation.await() }.getOrNull()
            ubicando = false
            if (pos == null) {
                avisoGps = "No se pudo obtener la ubicación. Elegila de la lista."
                return@launch
            }
            val mejor = indice.provincias.flatMap { p -> p.localidades.map { p to it } }
                .filter { it.second.lat != null && it.second.lon != null }
                .minByOrNull { (_, l) ->
                    val dLat = l.lat!! - pos.latitude
                    val dLon = (l.lon!! - pos.longitude) * cos(Math.toRadians(pos.latitude))
                    dLat * dLat + dLon * dLon
                }
            if (mejor == null) {
                avisoGps = "No hay comercios con ubicación cargada cerca tuyo."
            } else {
                provincia = mejor.first
                elegida = mejor.second.nombre
                onElegir(mejor.first, mejor.first.nombre, mejor.second, mejor.second.nombre)
            }
        }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ubicar() else avisoGps = "Sin permiso de ubicación. Elegí tu zona de la lista."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = {
                if (tienePermiso(ctx)) ubicar() else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ubicando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.MyLocation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Usar mi ubicación")
        }
        avisoGps?.let { Aclaracion(it) }

        Desplegable("Provincia", provincia?.nombre ?: "", indice.provincias.map { it.nombre }) { nombre ->
            provincia = indice.provincias.first { it.nombre == nombre }
            busqueda = ""
        }

        provincia?.let { p ->
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                label = { Text("Buscar ciudad o localidad") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val filtro = normalizar(busqueda)
            val lista = p.localidades
                .filter { filtro.isEmpty() || normalizar(it.nombre).contains(filtro) }
                .sortedByDescending { it.sucursales }
                .take(40)
            Column {
                lista.forEach { l ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                elegida = l.nombre
                                onElegir(p, p.nombre, l, l.nombre)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = elegida == l.nombre, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.nombre, fontWeight = FontWeight.Medium)
                            Text(
                                "${l.sucursales} comercios · ${l.cadenas.size} cadenas",
                                fontSize = 12.sp,
                                color = Colores.gris,
                            )
                        }
                    }
                    HorizontalDivider(color = Colores.linea)
                }
            }
        }
    }
}

private fun tienePermiso(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun Desplegable(etiqueta: String, valor: String, opciones: List<String>, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, fontSize = 11.sp, color = Colores.gris)
                Text(valor.ifEmpty { "Elegir…" }, color = Colores.tinta)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { abierto = false; onElegir(o) })
            }
        }
    }
}

// ------------------------------------------------------------------ cadenas

@Composable
fun SelectorCadenas(
    estado: EstadoIndice,
    provinciaNombre: String?,
    localidadId: String?,
    seleccion: Set<String>,
    onCambio: (Set<String>) -> Unit,
) {
    val indice = (estado as? EstadoIndice.Listo)?.indice
    val localidad = indice?.provincias?.firstOrNull { it.nombre == provinciaNombre }
        ?.localidades?.firstOrNull { it.id == localidadId }
    if (indice == null || localidad == null) {
        Aclaracion("Cuando haya datos de precios para tu zona vas a poder elegir las cadenas. Mientras tanto, se comparan todas.")
        return
    }
    val cadenas = localidad.cadenas.map { it to (indice.cadenas[it] ?: it) }.sortedBy { it.second }
    Column {
        Row {
            TextButton(onClick = { onCambio(cadenas.map { it.first }.toSet()) }) { Text("Todas") }
            TextButton(onClick = { onCambio(emptySet()) }) { Text("Ninguna") }
        }
        cadenas.forEach { (id, nombre) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onCambio(if (id in seleccion) seleccion - id else seleccion + id) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = id in seleccion, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(nombre)
            }
        }
        if (seleccion.isEmpty()) Aclaracion("Si no elegís ninguna, se muestran todas.")
    }
}

// ------------------------------------------------------------------ perfil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorPerfil(inicial: Perfil?, onGuardar: (Perfil) -> Unit, onCancelar: (() -> Unit)?) {
    var nombre by remember { mutableStateOf(inicial?.nombre ?: "") }
    var edad by remember { mutableStateOf(inicial?.edad?.toString() ?: "") }
    var sexo by remember { mutableStateOf(inicial?.sexo ?: Sexo.F) }
    var peso by remember { mutableStateOf(inicial?.pesoKg?.let { Nutricion.formatearNumero(it) } ?: "") }
    var altura by remember { mutableStateOf(inicial?.alturaCm?.let { Nutricion.formatearNumero(it) } ?: "") }
    var actividad by remember { mutableStateOf(inicial?.actividad ?: Actividad.LIGERA) }
    var restricciones by remember { mutableStateOf(inicial?.restricciones ?: emptySet()) }

    val edadN = edad.toIntOrNull()
    val pesoN = peso.replace(",", ".").toDoubleOrNull()
    val alturaN = altura.replace(",", ".").toDoubleOrNull()
    val valido = nombre.isNotBlank() && edadN != null && edadN in 1..110 &&
        pesoN != null && pesoN in 3.0..400.0 && alturaN != null && alturaN in 40.0..250.0
    val perfil = if (valido) Perfil(
        id = inicial?.id ?: UUID.randomUUID().toString(),
        nombre = nombre.trim(), edad = edadN!!, sexo = sexo, pesoKg = pesoN!!, alturaCm = alturaN!!,
        actividad = actividad, restricciones = restricciones,
    ) else null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero("Edad", edad, Modifier.weight(1f)) { edad = it }
            CampoNumero("Peso (kg)", peso, Modifier.weight(1f)) { peso = it }
            CampoNumero("Altura (cm)", altura, Modifier.weight(1f)) { altura = it }
        }
        Text("Sexo", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sexo.entries.forEach { s ->
                FilterChip(selected = sexo == s, onClick = { sexo = s }, label = { Text(s.etiqueta) })
            }
        }
        Text("Actividad física", fontWeight = FontWeight.SemiBold)
        Actividad.entries.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clickable { actividad = a },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = actividad == a, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(a.etiqueta)
                    Text(a.detalle, fontSize = 12.sp, color = Colores.gris)
                }
            }
        }
        Text("Dieta y restricciones", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Restriccion.entries.forEach { r ->
                FilterChip(
                    selected = r in restricciones,
                    onClick = { restricciones = if (r in restricciones) restricciones - r else restricciones + r },
                    label = { Text(r.etiqueta) },
                )
            }
        }
        perfil?.let {
            val req = Nutricion.requerimientos(it)
            Tarjeta {
                Text("Necesidad estimada: ${req.kcal.toInt()} kcal por día", fontWeight = FontWeight.Bold, color = Colores.verdeOscuro)
                Aclaracion(req.metodo + ". Es un valor orientativo y no reemplaza la consulta con un profesional.")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            onCancelar?.let { TextButton(onClick = it) { Text("Cancelar") } }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Button(onClick = { perfil?.let(onGuardar) }, enabled = perfil != null) {
                Text("Guardar perfil")
            }
        }
    }
}

@Composable
private fun CampoNumero(etiqueta: String, valor: String, modifier: Modifier, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { v -> onCambio(v.filter { it.isDigit() || it == ',' || it == '.' }.take(5)) },
        label = { Text(etiqueta, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
