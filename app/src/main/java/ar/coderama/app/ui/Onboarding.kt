package ar.coderama.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.coderama.app.AppViewModel
import ar.coderama.app.data.Perfil
import ar.coderama.app.logica.Nutricion

@Composable
fun PantallaOnboarding(vm: AppViewModel, onFin: () -> Unit) {
    val ajustes by vm.ajustes.collectAsState()
    val indice by vm.indice.collectAsState()
    var paso by remember { mutableIntStateOf(0) }
    var editando by remember { mutableStateOf<Perfil?>(null) }
    var creando by remember { mutableStateOf(false) }
    val a = ajustes ?: return
    val total = 4

    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.fondo)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (paso > 0) {
            LinearProgressIndicator(
                progress = { paso / (total - 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Colores.verde,
                trackColor = Colores.linea,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (paso) {
                0 -> Bienvenida()
                1 -> {
                    Encabezado("¿Dónde comprás?", "Usamos tu zona para comparar precios en los comercios cercanos.")
                    SelectorUbicacion(
                        estado = indice,
                        provinciaActual = a.provinciaNombre,
                        localidadActual = a.localidadNombre,
                        onElegir = { p, pn, l, ln -> vm.guardarUbicacion(p, pn, l, ln) },
                        onReintentar = { vm.cargarIndice(forzar = true) },
                    )
                }
                2 -> {
                    Encabezado("¿Qué cadenas querés comparar?", "Podés cambiarlo cuando quieras desde Ajustes.")
                    SelectorCadenas(indice, a.provinciaNombre, a.localidadId, a.cadenas) { vm.guardarCadenas(it) }
                }
                3 -> {
                    Encabezado(
                        "¿Para quién comprás?",
                        "Cargá un perfil por persona. Con edad, peso, altura y actividad estimamos las calorías y nutrientes que necesita cada uno.",
                    )
                    val enEdicion = editando
                    if (creando || enEdicion != null) {
                        Tarjeta(titulo = if (enEdicion != null) "Editar perfil" else "Nuevo perfil") {
                            EditorPerfil(
                                inicial = enEdicion,
                                onGuardar = { vm.guardarPerfil(it); creando = false; editando = null },
                                onCancelar = { creando = false; editando = null },
                            )
                        }
                    } else {
                        a.perfiles.forEach { p -> FilaPerfil(p) { editando = p } }
                        OutlinedButton(onClick = { creando = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (a.perfiles.isEmpty()) "Agregar mi perfil" else "Agregar otra persona")
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (paso > 0) OutlinedButton(onClick = { paso-- }) { Text("Atrás") } else Spacer(Modifier.size(1.dp))
            val puede = when (paso) {
                1 -> !a.provinciaNombre.isNullOrBlank()
                3 -> a.perfiles.isNotEmpty() && !creando && editando == null
                else -> true
            }
            Button(
                onClick = {
                    if (paso < total - 1) paso++ else { vm.terminarOnboarding(); onFin() }
                },
                enabled = puede,
            ) { Text(if (paso == 0) "Empezar" else if (paso < total - 1) "Siguiente" else "Listo") }
        }
    }
}

@Composable
private fun Bienvenida() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Colores.verde),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Colores.amarillo, modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Coderama", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Colores.verdeOscuro)
        Text("Escaneá, compará y cuidate", color = Colores.gris, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
    }
    Beneficio(Icons.Filled.LocalGroceryStore, "Precios", "Compará lo que cuesta en los súper de tu zona, con datos oficiales de Precios Claros.")
    Beneficio(Icons.Filled.Restaurant, "Nutrición", "Gráficos y cuánto aporta de lo que necesitás por día, con los sellos de la ley de etiquetado.")
    Beneficio(Icons.Filled.HealthAndSafety, "Alertas", "Avisos por tu dieta, para los chicos y sobre sustancias peligrosas en limpieza y cosmética.")
}

@Composable
private fun Beneficio(icono: ImageVector, titulo: String, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Colores.verdeClaro),
            contentAlignment = Alignment.Center,
        ) { Icon(icono, contentDescription = null, tint = Colores.verde) }
        Column {
            Text(titulo, fontWeight = FontWeight.Bold)
            Text(texto, color = Colores.gris, fontSize = 14.sp)
        }
    }
}

@Composable
fun Encabezado(titulo: String, subtitulo: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Colores.tinta)
        Text(subtitulo, color = Colores.gris)
    }
}

@Composable
fun FilaPerfil(p: Perfil, onEditar: () -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (p.esNino) Colores.ambarClaro else Colores.verdeClaro),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.nombre.take(1).uppercase(), fontWeight = FontWeight.Bold, color = if (p.esNino) Colores.ambar else Colores.verde)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.nombre, fontWeight = FontWeight.Bold)
                Text(
                    "${p.edad} años · ${Nutricion.kcalAproximadas(p)} kcal/día" +
                        if (p.restricciones.isNotEmpty()) " · ${p.restricciones.size} restricciones" else "",
                    fontSize = 13.sp,
                    color = Colores.gris,
                )
            }
            IconButton(onClick = onEditar) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = Colores.gris) }
        }
    }
}
