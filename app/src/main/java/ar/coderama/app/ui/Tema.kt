package ar.coderama.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ar.coderama.app.logica.Nivel

object Colores {
    val verde = Color(0xFF1B7F4B)
    val verdeOscuro = Color(0xFF0F5132)
    val verdeClaro = Color(0xFFDDF0E4)
    val amarillo = Color(0xFFF2B705)
    val ambar = Color(0xFFE08A00)
    val ambarClaro = Color(0xFFFFF1D6)
    val rojo = Color(0xFFC62828)
    val rojoClaro = Color(0xFFFDE2E1)
    val fondo = Color(0xFFF6F7F2)
    val tinta = Color(0xFF1C2421)
    val gris = Color(0xFF6B7570)
    val linea = Color(0xFFE2E6E0)

    // Colores del gráfico de origen de calorías
    val grasas = Color(0xFFE08A00)
    val carbos = Color(0xFF3A7BD5)
    val proteinas = Color(0xFF1B7F4B)
}

fun Nivel.color(): Color = when (this) {
    Nivel.OK -> Colores.verde
    Nivel.ATENCION -> Colores.ambar
    Nivel.NO -> Colores.rojo
}

fun Nivel.fondo(): Color = when (this) {
    Nivel.OK -> Colores.verdeClaro
    Nivel.ATENCION -> Colores.ambarClaro
    Nivel.NO -> Colores.rojoClaro
}

@Composable
fun TemaCoderama(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Colores.verde,
            onPrimary = Color.White,
            primaryContainer = Colores.verdeClaro,
            onPrimaryContainer = Colores.verdeOscuro,
            secondary = Colores.amarillo,
            onSecondary = Colores.tinta,
            background = Colores.fondo,
            surface = Color.White,
            onSurface = Colores.tinta,
            onBackground = Colores.tinta,
            surfaceVariant = Color(0xFFEFF2EC),
            outline = Colores.linea,
            error = Colores.rojo,
        ),
        content = contenido,
    )
}
