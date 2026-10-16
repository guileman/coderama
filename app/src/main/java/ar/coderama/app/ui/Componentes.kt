package ar.coderama.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.coderama.app.logica.Alerta
import ar.coderama.app.logica.Nivel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Colores.linea),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (titulo != null) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            contenido()
        }
    }
}

fun Nivel.icono(): ImageVector = when (this) {
    Nivel.OK -> Icons.Filled.CheckCircle
    Nivel.ATENCION -> Icons.Filled.Warning
    Nivel.NO -> Icons.Filled.Error
}

@Composable
fun FilaAlerta(a: Alerta) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(a.nivel.fondo())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(a.nivel.icono(), contentDescription = null, tint = a.nivel.color(), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(a.titulo, fontWeight = FontWeight.SemiBold, color = Colores.tinta)
            a.detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Colores.gris) }
        }
    }
}

@Composable
fun ChipNivel(texto: String, nivel: Nivel, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(nivel.fondo())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(nivel.icono(), contentDescription = null, tint = nivel.color(), modifier = Modifier.size(16.dp))
        Text(texto, color = nivel.color(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** Sello octogonal negro como los de la Ley de Etiquetado Frontal. */
@Composable
fun Octogono(texto: String, tamano: Dp = 74.dp) {
    Box(Modifier.size(tamano), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            fun octo(radio: Float) = Path().apply {
                for (i in 0 until 8) {
                    val ang = Math.toRadians(22.5 + i * 45.0)
                    val p = Offset(c.x + radio * cos(ang).toFloat(), c.y + radio * sin(ang).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(octo(r), Color.Black)
            drawPath(octo(r * 0.9f), Color.White, style = Stroke(width = r * 0.05f))
        }
        Text(
            texto,
            color = Color.White,
            fontSize = (tamano.value / 8.2).sp,
            lineHeight = (tamano.value / 7.2).sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = tamano * 0.16f),
        )
    }
}

/** Barra horizontal: cuánto del requerimiento diario cubre una cantidad. */
@Composable
fun BarraRequerimiento(nombre: String, valor: String, porcentaje: Double, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(nombre, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(valor, color = Colores.gris, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text("${(porcentaje * 100).toInt()}%", fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(Colores.linea),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(porcentaje.coerceIn(0.0, 1.0).toFloat().coerceAtLeast(0.02f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Gráfico de dona con segmentos (fracción, color). */
@Composable
fun Dona(segmentos: List<Pair<Float, Color>>, centro: String, subtitulo: String, tamano: Dp = 130.dp) {
    Box(Modifier.size(tamano), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val grosor = size.minDimension * 0.16f
            val arco = Size(size.width - grosor, size.height - grosor)
            val origen = Offset(grosor / 2, grosor / 2)
            drawArc(Colores.linea, 0f, 360f, false, origen, arco, style = Stroke(grosor))
            var inicio = -90f
            segmentos.forEach { (fraccion, color) ->
                val barrido = fraccion * 360f
                if (barrido > 0.5f) {
                    drawArc(color, inicio, (barrido - 1.5f).coerceAtLeast(0.5f), false, origen, arco, style = Stroke(grosor, cap = StrokeCap.Butt))
                }
                inicio += barrido
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centro, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Colores.tinta)
            Text(subtitulo, fontSize = 11.sp, color = Colores.gris)
        }
    }
}

@Composable
fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(texto, fontSize = 13.sp)
    }
}

@Composable
fun Etiquetita(texto: String, fondo: Color = Colores.verdeClaro, color: Color = Colores.verdeOscuro) {
    Text(
        texto,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fondo)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Aclaracion(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = Colores.gris)
}
