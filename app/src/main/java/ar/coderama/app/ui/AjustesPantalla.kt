package ar.coderama.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import ar.coderama.app.AppViewModel
import ar.coderama.app.BuildConfig
import ar.coderama.app.EstadoIndice
import ar.coderama.app.data.Perfil

@Composable
fun PantallaAjustes(vm: AppViewModel, onVolver: () -> Unit) {
    val ajustes by vm.ajustes.collectAsState()
    val indice by vm.indice.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val ctx = LocalContext.current
    val a = ajustes ?: return
    var cambiarZona by remember { mutableStateOf(false) }
    var cambiarCadenas by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Perfil?>(null) }
    var creando by remember { mutableStateOf(false) }

    LaunchedEffect(mensaje) {
        mensaje?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.mensajeVisto() }
    }

    Column(Modifier.fillMaxSize().background(Colores.fondo)) {
        Row(
            Modifier.fillMaxWidth().background(Colores.verdeOscuro).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White) }
            Text("Ajustes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tarjeta(titulo = "Tu zona") {
                Text(listOfNotNull(a.localidadNombre, a.provinciaNombre).joinToString(", ").ifEmpty { "Sin elegir" })
                if (cambiarZona) {
                    SelectorUbicacion(indice, a.provinciaNombre, a.localidadNombre,
                        onElegir = { p, pn, l, ln -> vm.guardarUbicacion(p, pn, l, ln) },
                        onReintentar = { vm.cargarIndice(forzar = true) })
                    TextButton(onClick = { cambiarZona = false }) { Text("Listo") }
                } else {
                    TextButton(onClick = { cambiarZona = true }) { Text("Cambiar zona") }
                }
            }

            Tarjeta(titulo = "Cadenas a comparar") {
                val nombres = (indice as? EstadoIndice.Listo)?.indice?.cadenas
                Text(
                    if (a.cadenas.isEmpty()) "Todas las de tu zona"
                    else a.cadenas.map { nombres?.get(it) ?: it }.sorted().joinToString(", "),
                    color = Colores.gris,
                )
                if (cambiarCadenas) {
                    SelectorCadenas(indice, a.provinciaNombre, a.localidadId, a.cadenas) { vm.guardarCadenas(it) }
                    TextButton(onClick = { cambiarCadenas = false }) { Text("Listo") }
                } else {
                    TextButton(onClick = { cambiarCadenas = true }) { Text("Elegir cadenas") }
                }
            }

            Text("Perfiles", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val enEdicion = editando
            if (creando || enEdicion != null) {
                Tarjeta(titulo = if (enEdicion != null) "Editar ${enEdicion.nombre}" else "Nuevo perfil") {
                    EditorPerfil(
                        inicial = enEdicion,
                        onGuardar = { vm.guardarPerfil(it); creando = false; editando = null },
                        onCancelar = { creando = false; editando = null },
                    )
                    if (enEdicion != null) {
                        TextButton(onClick = { vm.borrarPerfil(enEdicion.id); editando = null }) {
                            Text("Borrar perfil", color = Colores.rojo)
                        }
                    }
                }
            } else {
                a.perfiles.forEach { p -> FilaPerfil(p) { editando = p } }
                OutlinedButton(onClick = { creando = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar persona")
                }
            }

            Tarjeta(titulo = "Datos de precios") {
                val fecha = (indice as? EstadoIndice.Listo)?.indice?.fecha
                Text(
                    when (val e = indice) {
                        is EstadoIndice.Listo -> "Última actualización: $fecha"
                        is EstadoIndice.Error -> e.mensaje
                        EstadoIndice.Cargando -> "Cargando…"
                    },
                    color = Colores.gris,
                )
                OutlinedButton(onClick = { vm.actualizarPrecios() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Actualizar ahora")
                }
            }

            Tarjeta(titulo = "Historial") {
                Text("${a.historial.size} productos escaneados", color = Colores.gris)
                TextButton(onClick = { vm.borrarHistorial() }) { Text("Borrar historial") }
            }

            Tarjeta(titulo = "Acerca de Coderama") {
                Text("Versión ${BuildConfig.VERSION_NAME}", color = Colores.gris)
                Aclaracion(
                    "Precios: Precios Claros – Base SEPA, Secretaría de Comercio (CC BY 4.0). " +
                        "Productos: Open Food Facts, Open Beauty Facts y Open Products Facts (ODbL). " +
                        "Alertas: ANMAT. La información es orientativa y no reemplaza el rótulo del producto ni la consulta con profesionales.",
                )
            }
        }
    }
}
