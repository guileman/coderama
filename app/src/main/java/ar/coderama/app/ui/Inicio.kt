package ar.coderama.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.coderama.app.AppViewModel
import ar.coderama.app.data.Escaneo
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PantallaInicio(
    vm: AppViewModel,
    onEscanear: () -> Unit,
    onEtiqueta: () -> Unit,
    onCodigo: (String) -> Unit,
    onAjustes: () -> Unit,
) {
    val ajustes by vm.ajustes.collectAsState()
    val a = ajustes ?: return
    var codigo by remember { mutableStateOf("") }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Colores.fondo)
            .navigationBarsPadding(),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Colores.verdeOscuro, Colores.verde)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Coderama", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = Colores.amarillo, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(
                                listOfNotNull(a.localidadNombre, a.provinciaNombre).joinToString(", ").ifEmpty { "Sin zona elegida" },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                            )
                        }
                    }
                    IconButton(onClick = onAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White)
                    }
                }
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Botón principal
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Colores.amarillo)
                        .clickable(onClick = onEscanear)
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Colores.tinta),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Colores.amarillo, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text("Escanear producto", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Colores.tinta)
                        Text("Código de barras o QR", color = Colores.tinta.copy(alpha = 0.7f))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable(onClick = onEtiqueta)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = Colores.verde)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Leer una etiqueta", fontWeight = FontWeight.Bold)
                        Text("Si el producto no aparece, sacale una foto al rótulo", fontSize = 13.sp, color = Colores.gris)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Colores.gris)
                }
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { v -> codigo = v.filter { it.isDigit() }.take(14) },
                    placeholder = { Text("O escribí el código de barras") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (codigo.length >= 8) IconButton(onClick = { onCodigo(codigo) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Buscar")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (codigo.length >= 8) onCodigo(codigo) }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Colores.linea,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (a.perfiles.isNotEmpty()) {
                    Text(
                        "Perfiles: " + a.perfiles.joinToString { it.nombre },
                        fontSize = 13.sp,
                        color = Colores.gris,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Últimos escaneos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (a.historial.isEmpty()) {
                    Aclaracion("Todavía no escaneaste nada. Los productos que mires van a aparecer acá.")
                }
            }
        }
        items(a.historial, key = { it.codigo }) { e ->
            FilaHistorial(e) { onCodigo(e.codigo) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FilaHistorial(e: Escaneo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (e.imagen != null) {
                AsyncImage(model = e.imagen, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Colores.gris)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.nombre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(e.marca, SimpleDateFormat("dd/MM HH:mm", Locale("es", "AR")).format(Date(e.fecha))).joinToString(" · "),
                fontSize = 13.sp,
                color = Colores.gris,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Colores.gris)
    }
}
