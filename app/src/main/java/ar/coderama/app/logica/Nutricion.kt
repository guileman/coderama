package ar.coderama.app.logica

import ar.coderama.app.data.Nutrientes
import ar.coderama.app.data.Perfil
import ar.coderama.app.data.Producto
import ar.coderama.app.data.Sexo
import kotlin.math.roundToInt

/** Requerimientos diarios estimados. Las grasas saturadas, azúcares y sodio son límites máximos. */
data class Requerimientos(
    val kcal: Double,
    val proteinasG: Double,
    val grasasG: Double,
    val saturadasMaxG: Double,
    val carbohidratosG: Double,
    val azucaresMaxG: Double,
    val fibraG: Double,
    val sodioMaxMg: Double,
    val metodo: String,
)

object Nutricion {

    // Necesidad energética de niños y adolescentes con actividad moderada (kcal/día),
    // valores aproximados de las tablas FAO/OMS/UNU 2004, por año de edad (1 a 17).
    private val ninos = mapOf(
        Sexo.M to listOf(950, 1125, 1250, 1350, 1475, 1575, 1700, 1825, 1975, 2150, 2350, 2550, 2775, 3000, 3175, 3325, 3400),
        Sexo.F to listOf(850, 1050, 1150, 1250, 1325, 1425, 1550, 1700, 1850, 2000, 2150, 2275, 2375, 2450, 2500, 2500, 2500),
    )

    fun requerimientos(p: Perfil): Requerimientos {
        val (kcal, metodo) = if (p.edad >= 18) {
            val tmb = 10 * p.pesoKg + 6.25 * p.alturaCm - 5 * p.edad + if (p.sexo == Sexo.M) 5 else -161
            tmb * p.actividad.factor to "Fórmula de Mifflin-St Jeor × nivel de actividad"
        } else {
            val tabla = ninos.getValue(p.sexo)
            val base = tabla[(p.edad.coerceIn(1, 17)) - 1]
            base * p.actividad.factorNinos to "Tablas FAO/OMS de requerimiento energético por edad"
        }
        val sodio = if (p.edad >= 18) 2000.0 else (2000.0 * kcal / 2000.0).coerceIn(800.0, 2000.0)
        return Requerimientos(
            kcal = kcal,
            proteinasG = maxOf(kcal * 0.15 / 4, if (p.edad >= 18) 0.8 * p.pesoKg else 0.0),
            grasasG = kcal * 0.30 / 9,
            saturadasMaxG = kcal * 0.10 / 9,
            carbohidratosG = kcal * 0.55 / 4,
            azucaresMaxG = kcal * 0.10 / 4,
            fibraG = kcal / 1000 * 14,
            sodioMaxMg = sodio,
            metodo = metodo,
        )
    }

    fun kcalAproximadas(p: Perfil): Int = requerimientos(p).kcal.roundToInt()

    fun formatearNumero(v: Double): String =
        if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else v.toString().replace(".", ",")

    // ---------------------------------------------------------- sellos octogonales

    enum class Sello(val texto: String) {
        AZUCARES("EXCESO EN AZÚCARES"),
        SODIO("EXCESO EN SODIO"),
        GRASAS_SATURADAS("EXCESO EN GRASAS SATURADAS"),
        GRASAS_TOTALES("EXCESO EN GRASAS TOTALES"),
        CALORIAS("EXCESO EN CALORÍAS"),
    }

    /**
     * Estimación de los sellos de la Ley 27.642 de Etiquetado Frontal según el
     * perfil de nutrientes de la OPS. Se usa el azúcar total porque las bases
     * abiertas no informan azúcares agregados, así que puede marcar de más.
     */
    fun sellos(prod: Producto): List<Sello> {
        val n = prod.nutrientes ?: return emptyList()
        val kcal = n.kcal ?: return emptyList()
        val lista = mutableListOf<Sello>()
        if (kcal > 0) {
            n.azucares?.let { if (it * 4 >= kcal * 0.10) lista += Sello.AZUCARES }
            n.grasas?.let { if (it * 9 >= kcal * 0.30) lista += Sello.GRASAS_TOTALES }
            n.saturadas?.let { if (it * 9 >= kcal * 0.10) lista += Sello.GRASAS_SATURADAS }
        }
        n.sodioMg?.let { sodio ->
            // La regla de 1 mg por kcal no aplica a productos casi sin calorías (ej. gaseosas light)
            val porKcal = kcal >= 4 && sodio / kcal >= 1.0
            val porCien = if (prod.esBebida) sodio >= 40 else sodio >= 300
            if (porKcal || porCien) lista += Sello.SODIO
        }
        if (prod.esBebida) {
            if (kcal >= 25) lista += Sello.CALORIAS
        } else if (kcal >= 275) {
            lista += Sello.CALORIAS
        }
        return lista
    }

    // ---------------------------------------------------------- comparación

    enum class Tipo { LIMITE, OBJETIVO }

    data class Comparacion(
        val nombre: String,
        val cantidad: Double,
        val unidad: String,
        val porcentaje: Double,
        val tipo: Tipo,
    )

    /** Cuánto del requerimiento diario cubre una cantidad de gramos del producto. */
    fun comparar(n: Nutrientes, gramos: Double, r: Requerimientos): List<Comparacion> {
        val f = gramos / 100.0
        fun fila(nombre: String, valor: Double?, unidad: String, ref: Double, tipo: Tipo) =
            valor?.let { Comparacion(nombre, it * f, unidad, it * f / ref, tipo) }
        return listOfNotNull(
            fila("Calorías", n.kcal, "kcal", r.kcal, Tipo.LIMITE),
            fila("Azúcares", n.azucares, "g", r.azucaresMaxG, Tipo.LIMITE),
            fila("Grasas totales", n.grasas, "g", r.grasasG, Tipo.LIMITE),
            fila("Grasas saturadas", n.saturadas, "g", r.saturadasMaxG, Tipo.LIMITE),
            fila("Sodio", n.sodioMg, "mg", r.sodioMaxMg, Tipo.LIMITE),
            fila("Carbohidratos", n.carbohidratos, "g", r.carbohidratosG, Tipo.OBJETIVO),
            fila("Proteínas", n.proteinas, "g", r.proteinasG, Tipo.OBJETIVO),
            fila("Fibra", n.fibra, "g", r.fibraG, Tipo.OBJETIVO),
        )
    }

    /** Porcentaje de calorías que aportan grasas, carbohidratos y proteínas. */
    fun origenCalorias(n: Nutrientes): Triple<Double, Double, Double>? {
        val g = (n.grasas ?: 0.0) * 9
        val c = (n.carbohidratos ?: 0.0) * 4
        val p = (n.proteinas ?: 0.0) * 4
        val total = g + c + p
        if (total <= 0) return null
        return Triple(g / total, c / total, p / total)
    }
}
