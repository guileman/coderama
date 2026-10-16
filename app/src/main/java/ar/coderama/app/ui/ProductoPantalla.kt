package ar.coderama.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.coderama.app.AppViewModel
import ar.coderama.app.EstadoProducto
import ar.coderama.app.Ficha
import ar.coderama.app.data.Ajustes
import ar.coderama.app.data.FilaPrecio
import ar.coderama.app.data.Producto
import ar.coderama.app.logica.Alerta
import ar.coderama.app.logica.Analisis
import ar.coderama.app.logica.Nivel
import ar.coderama.app.logica.Nutricion
import ar.coderama.app.logica.Peligros
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val formatoPesos: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
fun precio(v: Double): String = formatoPesos.format(v)

private val alergenosEs = mapOf(
    "en:gluten" to "Gluten", "en:milk" to "Leche", "en:eggs" to "Huevo", "en:nuts" to "Frutos secos",
    "en:peanuts" to "Maní", "en:soybeans" to "Soja", "en:fish" to "Pescado", "en:crustaceans" to "Crustáceos",
    "en:molluscs" to "Moluscos", "en:celery" to "Apio", "en:mustard" to "Mostaza", "en:sesame-seeds" to "Sésamo",
    "en:sulphur-dioxide-and-sulphites" to "Sulfitos", "en:lupin" to "Lupino",
)

fun limpiarTag(t: String): String =
    alergenosEs[t] ?: t.substringAfter(":").replace("-", " ").replaceFirstChar { it.uppercase() }

@Composable
fun PantallaProducto(
    vm: AppViewModel,
    onVolver: () -> Unit,
    onLeerEtiqueta: (String?) -> Unit,
    onEscanear: () -> Unit,
    onAjustes: () -> Unit,
) {
    val estado by vm.producto.collectAsState()
    val ajustes by vm.ajustes.collectAsState()
    val a = ajustes ?: return

    Column(Modifier.fillMaxSize().background(Colores.fondo)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Colores.verdeOscuro)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text("Producto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onEscanear) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear otro", tint = Color.White)
            }
        }
        when (val e = estado) {
            EstadoProducto.Vacio, EstadoProducto.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Colores.verde)
                    Spacer(Modifier.height(12.dp))
                    Text("Buscando precios, nutrientes y alertas…", color = Colores.gris)
                }
            }
            is EstadoProducto.NoEncontrado -> NoEncontrado(e.codigo, onLeerEtiqueta, onEscanear)
            is EstadoProducto.Listo -> FichaProducto(e.ficha, a, onLeerEtiqueta, onAjustes)
        }
    }
}

@Composable
private fun NoEncontrado(codigo: String, onLeerEtiqueta: (String?) -> Unit, onEscanear: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Colores.gris, modifier = Modifier.size(56.dp))
        Text("No encontramos el código $codigo", fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text(
            "No está en las bases abiertas ni en los precios de tu zona. Sacale una foto a la etiqueta y analizamos los ingredientes y advertencias igual.",
            color = Colores.gris,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { onLeerEtiqueta(codigo) }) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Leer la etiqueta")
        }
        TextButton(onClick = onEscanear) { Text("Escanear otro producto") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FichaProducto(f: Ficha, a: Ajustes, onLeerEtiqueta: (String?) -> Unit, onAjustes: () -> Unit) {
    val p = f.producto
    val resultados = remember(f, a.perfiles) { a.perfiles.map { Analisis.evaluar(p, it) } }
    val pestanas = listOf("Precios", "Nutrición", "Alertas", "Ficha")
    val inicial = when {
        f.precios != null -> 0
        p.esAlimento -> 1
        else -> 2
    }
    var pestana by remember(f) { mutableIntStateOf(inicial) }
    val nAlertas = resultados.count { it.nivel != Nivel.OK } + Peligros.analizar(p, f.textoEtiqueta).alertas.size

    Column(Modifier.fillMaxSize()) {
        // Encabezado del producto
        Column(Modifier.background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(Colores.fondo),
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.imagen != null) {
                        AsyncImage(p.imagen, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                    } else {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Colores.gris, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.nombre, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(p.marca, p.cantidad).joinToString(" · "), color = Colores.gris, fontSize = 14.sp)
                    f.precios?.filas?.firstOrNull { it.elegida }?.let {
                        Text("Desde ${precio(it.minimo)}", color = Colores.verde, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
            if (resultados.isNotEmpty() && p.esAlimento) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    resultados.forEach { r -> ChipNivel("${r.perfil.nombre}: ${r.etiqueta}", r.nivel) }
                }
            }
        }
        TabRow(
            selectedTabIndex = pestana,
            containerColor = Color.White,
            contentColor = Colores.verde,
            indicator = { pos ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[pestana]), color = Colores.verde)
            },
        ) {
            pestanas.forEachIndexed { i, t ->
                Tab(
                    selected = pestana == i,
                    onClick = { pestana = i },
                    text = {
                        Text(
                            if (i == 2 && nAlertas > 0) "$t ($nAlertas)" else t,
                            fontWeight = if (pestana == i) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    selectedContentColor = Colores.verde,
                    unselectedContentColor = Colores.gris,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (pestana) {
                0 -> SeccionPrecios(f, a, onAjustes)
                1 -> SeccionNutricion(p, a, onLeerEtiqueta)
                2 -> SeccionAlertas(f, a, resultados)
                else -> SeccionFicha(f, onLeerEtiqueta)
            }
        }
    }
}

// ------------------------------------------------------------------ precios

@Composable
private fun SeccionPrecios(f: Ficha, a: Ajustes, onAjustes: () -> Unit) {
    if (a.localidadId == null) {
        Tarjeta(titulo = "Elegí tu zona") {
            Text("Para comparar precios necesitamos saber en qué localidad comprás.")
            Button(onClick = onAjustes) { Text("Ir a Ajustes") }
        }
        return
    }
    val pr = f.precios
    if (pr == null) {
        Tarjeta(titulo = "Sin precios en tu zona") {
            Text(
                "Este producto no aparece en los comercios de ${a.localidadNombre} que informan a Precios Claros (SEPA). " +
                    "Suele pasar con productos de almacenes chicos, marcas regionales o artículos que no son de consumo masivo.",
                color = Colores.gris,
            )
        }
        return
    }
    var todas by remember { mutableStateOf(false) }
    val visibles = if (todas) pr.filas else pr.filas.filter { it.elegida }.ifEmpty { pr.filas }
    val ocultas = pr.filas.size - visibles.size
    val mejor = visibles.first()
    val peor = visibles.maxBy { it.maximo }
    val ahorro = peor.maximo - mejor.minimo

    Tarjeta {
        Text("Más barato en ${a.localidadNombre}", color = Colores.gris, fontSize = 13.sp)
        Text(mejor.cadena, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(precio(mejor.minimo), fontWeight = FontWeight.Black, fontSize = 30.sp, color = Colores.verde)
        if (ahorro > 0.5 && visibles.size > 1) {
            Text(
                "Podés ahorrar hasta ${precio(ahorro)} (${(ahorro / peor.maximo * 100).roundToInt()}%) frente al precio más alto.",
                color = Colores.verdeOscuro,
            )
        }
    }
    visibles.forEachIndexed { i, fila -> FilaCadena(fila, i == 0) }
    if (ocultas > 0 || todas) {
        TextButton(onClick = { todas = !todas }) {
            Text(if (todas) "Mostrar solo mis cadenas" else "Ver $ocultas cadenas más")
        }
    }
    Aclaracion("Datos oficiales de Precios Claros (SEPA) del ${pr.fecha}. Son los precios de lista informados por cada comercio; en góndola pueden variar.")
}

@Composable
private fun FilaCadena(f: FilaPrecio, primero: Boolean) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.cadena, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (primero) Etiquetita("Más barato")
                }
                if (f.sucursales > 1 && f.maximo > f.minimo + 0.5) {
                    Text("Hasta ${precio(f.maximo)} · ${f.sucursales} sucursales", fontSize = 13.sp, color = Colores.gris)
                } else {
                    Text("${f.sucursales} sucursal${if (f.sucursales > 1) "es" else ""}", fontSize = 13.sp, color = Colores.gris)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(precio(f.minimo), fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (primero) Colores.verde else Colores.tinta)
                f.promo?.takeIf { it < f.minimo }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = Colores.ambar, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Promo ${precio(it)}", color = Colores.ambar, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (f.sucursalMasBarata != null || f.direccion != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = Colores.gris, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    listOfNotNull(f.sucursalMasBarata, f.direccion).joinToString(" · "),
                    fontSize = 13.sp,
                    color = Colores.gris,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ nutrición

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionNutricion(p: Producto, a: Ajustes, onLeerEtiqueta: (String?) -> Unit) {
    val n = p.nutrientes
    if (!p.esAlimento || n == null) {
        Tarjeta(titulo = if (p.esAlimento) "Sin tabla nutricional" else "No es un alimento") {
            Text(
                if (p.esAlimento) "La base abierta no tiene la información nutricional de este producto."
                else "Este producto no tiene información nutricional.",
                color = Colores.gris,
            )
            if (p.esAlimento) OutlinedButton(onClick = { onLeerEtiqueta(p.codigo) }) { Text("Leer la tabla de la etiqueta") }
        }
        return
    }
    var perfilId by remember { mutableStateOf(a.perfiles.firstOrNull()?.id) }
    val perfil = a.perfiles.firstOrNull { it.id == perfilId } ?: a.perfiles.firstOrNull()
    val opciones = listOfNotNull(
        p.porcionG?.let { "Porción (${Analisis.fmt(it)} ${if (p.esBebida) "ml" else "g"})" to it },
        (if (p.esBebida) "100 ml" else "100 g") to 100.0,
        p.totalG?.let { "Envase (${Analisis.fmt(it)} ${if (p.esBebida) "ml" else "g"})" to it },
    )
    var opcion by remember { mutableIntStateOf(0) }
    val (etiquetaCantidad, gramos) = opciones[opcion.coerceAtMost(opciones.lastIndex)]

    // Sellos
    val sellos = Nutricion.sellos(p)
    Tarjeta(titulo = "Sellos de advertencia") {
        if (sellos.isEmpty()) {
            Text("No corresponden sellos de exceso según sus nutrientes.", color = Colores.verdeOscuro)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sellos.forEach { Octogono(it.texto) }
                if (Analisis.edulcorantes(p)) Etiquetita("CONTIENE EDULCORANTES, NO RECOMENDABLE EN NIÑOS", Color.Black, Color.White)
                if (Analisis.cafeina(p)) Etiquetita("CONTIENE CAFEÍNA. EVITAR EN NIÑOS", Color.Black, Color.White)
            }
        }
        Aclaracion("Estimados con la tabla nutricional y los criterios de la Ley 27.642. El envase tiene la información oficial.")
    }

    if (a.perfiles.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            a.perfiles.forEach { pf ->
                FilterChip(selected = pf.id == perfil?.id, onClick = { perfilId = pf.id }, label = { Text(pf.nombre) })
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, (t, _) ->
            FilterChip(selected = opcion == i, onClick = { opcion = i }, label = { Text(t, fontSize = 12.sp) })
        }
    }

    // Dona de origen de calorías
    val kcal = n.kcal?.times(gramos / 100)
    Nutricion.origenCalorias(n)?.let { (g, c, pr) ->
        Tarjeta(titulo = "¿De dónde vienen las calorías?") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dona(
                    listOf(g.toFloat() to Colores.grasas, c.toFloat() to Colores.carbos, pr.toFloat() to Colores.proteinas),
                    centro = kcal?.roundToInt()?.toString() ?: "–",
                    subtitulo = "kcal",
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Leyenda(Colores.grasas, "Grasas ${(g * 100).roundToInt()}%")
                    Leyenda(Colores.carbos, "Carbohidratos ${(c * 100).roundToInt()}%")
                    Leyenda(Colores.proteinas, "Proteínas ${(pr * 100).roundToInt()}%")
                    Text(etiquetaCantidad, fontSize = 12.sp, color = Colores.gris)
                }
            }
        }
    }

    // Comparación con el requerimiento diario
    if (perfil != null) {
        val req = remember(perfil) { Nutricion.requerimientos(perfil) }
        Tarjeta(titulo = "Cuánto de tu día es") {
            Text("${perfil.nombre} · ${req.kcal.roundToInt()} kcal diarias estimadas · $etiquetaCantidad", fontSize = 13.sp, color = Colores.gris)
            Nutricion.comparar(n, gramos, req).forEach { c ->
                val color = when {
                    c.tipo == Nutricion.Tipo.OBJETIVO -> Colores.carbos
                    c.porcentaje < 0.10 -> Colores.verde
                    c.porcentaje < 0.25 -> Colores.ambar
                    else -> Colores.rojo
                }
                BarraRequerimiento(c.nombre, "${Analisis.fmt(c.cantidad)} ${c.unidad}", c.porcentaje, color)
            }
            Aclaracion("Verde, amarillo y rojo indican cuánto del límite diario consume esta cantidad. En azul, nutrientes que conviene alcanzar.")
        }
    } else {
        Tarjeta { Text("Cargá un perfil en Ajustes para ver cuánto de tu día representa.", color = Colores.gris) }
    }

    Tarjeta(titulo = "Calidad") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.nova?.let { nv ->
                val (txt, niv) = when (nv) {
                    1 -> "Sin procesar o mínimo" to Nivel.OK
                    2 -> "Ingrediente culinario" to Nivel.OK
                    3 -> "Procesado" to Nivel.ATENCION
                    else -> "Ultraprocesado" to Nivel.NO
                }
                ChipNivel("NOVA $nv · $txt", niv)
            }
            p.nutriscore?.let { ns ->
                val niv = when (ns.lowercase()) { "a", "b" -> Nivel.OK; "c" -> Nivel.ATENCION; else -> Nivel.NO }
                ChipNivel("Nutri-Score ${ns.uppercase()}", niv)
            }
        }
        if (p.nova == null && p.nutriscore == null) Text("Sin clasificación disponible.", color = Colores.gris)
    }

    if (p.ingredientes != null || p.alergenos.isNotEmpty()) {
        Tarjeta(titulo = "Ingredientes") {
            if (p.alergenos.isNotEmpty()) {
                Text("Alérgenos: " + p.alergenos.joinToString { limpiarTag(it) }, fontWeight = FontWeight.SemiBold, color = Colores.rojo)
            }
            if (p.trazas.isNotEmpty()) {
                Text("Puede contener: " + p.trazas.joinToString { limpiarTag(it) }, color = Colores.ambar)
            }
            p.ingredientes?.let { Text(it.take(1200), fontSize = 14.sp) }
        }
    }
    Aclaracion("Los requerimientos son estimaciones orientativas y no reemplazan la consulta con un nutricionista o médico.")
}

// ------------------------------------------------------------------ alertas

@Composable
private fun SeccionAlertas(f: Ficha, a: Ajustes, resultados: List<ar.coderama.app.logica.ResultadoPerfil>) {
    val p = f.producto
    val ctx = LocalContext.current
    val peligros = remember(f) { Peligros.analizar(p, f.textoEtiqueta) }
    val anmat = remember(f) { Analisis.coincidenciasAnmat(p, f.anmat) }
    val hayNinos = a.perfiles.any { it.edad < 13 }

    if (anmat.isNotEmpty()) {
        Tarjeta(titulo = "Alertas de la ANMAT") {
            Aclaracion("Posibles coincidencias por marca y nombre. Revisá si el lote o la presentación coinciden.")
            anmat.forEach { al ->
                Row(
                    Modifier.fillMaxWidth().clickable { abrir(ctx, al.url) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(al.titulo, fontWeight = FontWeight.SemiBold, color = Colores.rojo)
                        al.fecha?.let { Text(it, fontSize = 12.sp, color = Colores.gris) }
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Colores.gris)
                }
            }
        }
    }

    if (p.esAlimento) {
        if (resultados.isEmpty()) {
            Tarjeta { Text("Cargá perfiles en Ajustes para recibir alertas de dieta y calorías.", color = Colores.gris) }
        }
        resultados.forEach { r ->
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.perfil.nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (r.perfil.esNino) Text("Perfil infantil · ${r.perfil.edad} años", fontSize = 12.sp, color = Colores.gris)
                    }
                    ChipNivel(r.etiqueta, r.nivel)
                }
                if (r.alertas.isEmpty()) {
                    FilaAlerta(Alerta(Nivel.OK, "Sin alertas para este perfil"))
                } else {
                    r.alertas.forEach { FilaAlerta(it) }
                }
            }
        }
    }

    if (peligros.alertas.isNotEmpty() || peligros.esQuimicoDelHogar) {
        Tarjeta(titulo = "Sustancias de cuidado") {
            peligros.alertas.forEach { FilaAlerta(it) }
            if (peligros.esQuimicoDelHogar) {
                FilaAlerta(
                    Alerta(
                        Nivel.ATENCION,
                        "No mezclar productos de limpieza",
                        "Lavandina + amoníaco, + ácidos (vinagre, antisarro) o + alcohol liberan gases tóxicos. Usar de a uno, con ventilación.",
                    ),
                )
                if (hayNinos) {
                    FilaAlerta(Alerta(Nivel.ATENCION, "Hay chicos en casa", "Guardalo cerrado, en alto y en su envase original. Nunca lo trasvases a botellas de bebida."))
                }
            }
            Aclaracion("Detección por ingredientes y palabras del rótulo. Ante una intoxicación: Centro Nacional de Intoxicaciones 0800-333-0160.")
        }
    }

    if (anmat.isEmpty() && (!p.esAlimento || resultados.isEmpty()) && peligros.alertas.isEmpty() && !peligros.esQuimicoDelHogar) {
        Tarjeta {
            FilaAlerta(Alerta(Nivel.OK, "Sin alertas", "No encontramos sustancias de cuidado ni avisos de la ANMAT para este producto."))
            if (p.ingredientes == null) Aclaracion("No hay lista de ingredientes en la base. Podés leer la etiqueta para analizarla.")
        }
    }
}

// ------------------------------------------------------------------ ficha

@Composable
private fun SeccionFicha(f: Ficha, onLeerEtiqueta: (String?) -> Unit) {
    val p = f.producto
    val ctx = LocalContext.current
    val consulta = listOfNotNull(p.marca, p.nombre).joinToString(" ").ifBlank { p.codigo }

    Tarjeta(titulo = "Datos del producto") {
        Dato("Código", p.codigo)
        Dato("Nombre", p.nombre)
        p.marca?.let { Dato("Marca", it) }
        p.cantidad?.let { Dato("Contenido", it) }
        f.precios?.descripcion?.takeIf { it.isNotBlank() && it != p.nombre }?.let { Dato("Descripción comercial", it) }
        p.categorias.takeIf { it.isNotEmpty() }?.let { cats ->
            Dato("Categoría", cats.takeLast(3).joinToString(" › ") { limpiarTag(it) })
        }
        Dato("Fuente", p.fuente.etiqueta)
    }

    f.eficiencia?.let { ef ->
        Tarjeta(titulo = "Eficiencia energética") {
            ef.clase?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        it,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (it.startsWith("A")) Colores.verde else if (it <= "C") Colores.ambar else Colores.rojo)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Clase de eficiencia")
                }
            }
            ef.consumoKwh?.let { Dato("Consumo declarado", it) }
            Aclaracion("Leído de la foto de la etiqueta. Verificá los valores en el producto.")
        }
    }

    Tarjeta(titulo = "Más información") {
        BotonLink("Buscar ficha técnica") { abrir(ctx, "https://www.google.com/search?q=" + Uri.encode("$consulta ficha técnica")) }
        BotonLink("Ver en Mercado Libre") { abrir(ctx, "https://listado.mercadolibre.com.ar/" + Uri.encode(consulta.replace(" ", "-"))) }
        p.url?.let { u -> BotonLink("Ver en ${p.fuente.etiqueta}") { abrir(ctx, u) } }
        OutlinedButton(onClick = { onLeerEtiqueta(p.codigo) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Leer etiqueta o rótulo")
        }
        Aclaracion("En electrodomésticos, fotografiá la etiqueta de eficiencia energética para ver la clase y el consumo.")
    }

    f.textoEtiqueta?.let { t ->
        var ver by remember { mutableStateOf(false) }
        Tarjeta(titulo = "Texto leído de la etiqueta") {
            Text(if (ver) t else t.take(240) + if (t.length > 240) "…" else "", fontSize = 13.sp, color = Colores.gris)
            if (t.length > 240) TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ver menos" else "Ver todo") }
        }
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Column {
        Text(nombre, fontSize = 12.sp, color = Colores.gris)
        Text(valor, fontWeight = FontWeight.Medium)
        HorizontalDivider(Modifier.padding(top = 6.dp), color = Colores.linea)
    }
}

@Composable
private fun BotonLink(texto: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Colores.verde, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(texto, color = Colores.verde, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.None, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Colores.gris, modifier = Modifier.size(18.dp))
    }
}

fun abrir(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
