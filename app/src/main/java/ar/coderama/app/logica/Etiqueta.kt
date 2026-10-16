package ar.coderama.app.logica

import ar.coderama.app.data.Fuente
import ar.coderama.app.data.Nutrientes
import ar.coderama.app.data.Producto

/** Datos de la etiqueta de eficiencia energética (IRAM) de electrodomésticos. */
data class EficienciaEnergetica(val clase: String?, val consumoKwh: String?)

/**
 * Interpreta el texto leído de una foto de etiqueta. Es una lectura aproximada:
 * el reconocimiento de texto puede equivocarse, sobre todo con números.
 */
object Etiqueta {

    private fun numero(s: String?): Double? = s?.replace(",", ".")?.toDoubleOrNull()

    fun aProducto(texto: String, codigo: String?): Producto {
        val plano = texto.replace('\n', ' ')
        val bajo = plano.lowercase()
        val ingredientes = Regex("ingredientes?\\s*:?(.{10,800}?)(?:informaci[oó]n nutricional|valor energ|conservar|elaborado|$)", RegexOption.IGNORE_CASE)
            .find(plano)?.groupValues?.get(1)?.trim()
        return Producto(
            codigo = codigo ?: "etiqueta",
            nombre = texto.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)?.ifBlank { null } ?: "Producto de la etiqueta",
            fuente = Fuente.ETIQUETA,
            ingredientes = ingredientes ?: plano,
            nutrientes = nutrientes(bajo),
            porcionG = Regex("porci[oó]n\\s*(?:de)?\\s*:?\\s*(\\d+[.,]?\\d*)\\s*(g|ml)").find(bajo)?.groupValues?.get(1)?.let(::numero),
            esBebida = Regex("\\d\\s*(ml|cc)\\b").containsMatchIn(bajo),
            alimentoDeclarado = Regex("ingredientes|informaci[oó]n nutricional|valor energ|sin tacc").containsMatchIn(bajo),
        ).let { p ->
            // En Argentina la tabla viene por porción: se pasa a 100 g si se conoce la porción
            val porcion = p.porcionG
            val n = p.nutrientes
            if (porcion != null && porcion > 0 && n != null) {
                val f = 100.0 / porcion
                p.copy(
                    nutrientes = Nutrientes(
                        kcal = n.kcal?.times(f), grasas = n.grasas?.times(f), saturadas = n.saturadas?.times(f),
                        trans = n.trans?.times(f), carbohidratos = n.carbohidratos?.times(f),
                        azucares = n.azucares?.times(f), fibra = n.fibra?.times(f),
                        proteinas = n.proteinas?.times(f), sodioMg = n.sodioMg?.times(f),
                    ),
                )
            } else {
                p.copy(nutrientes = null)
            }
        }
    }

    private fun nutrientes(t: String): Nutrientes? {
        fun g(patron: String, unidad: String = "g"): Double? =
            Regex("$patron[^0-9]{0,25}(\\d+[.,]?\\d*)\\s*$unidad").find(t)?.groupValues?.get(1)?.let(::numero)
        val n = Nutrientes(
            kcal = g("valor energ[eé]tico", "kcal") ?: g("energ[ií]a", "kcal"),
            carbohidratos = g("carbohidratos"),
            azucares = g("az[uú]cares"),
            proteinas = g("prote[ií]nas"),
            grasas = g("grasas totales"),
            saturadas = g("grasas saturadas"),
            trans = g("grasas trans"),
            fibra = g("fibra"),
            sodioMg = g("sodio", "mg"),
        )
        return n.takeIf { !it.vacio }
    }

    fun eficiencia(texto: String): EficienciaEnergetica? {
        val t = texto.lowercase()
        val clase = Regex("clase\\s*(?:de eficiencia)?\\s*:?\\s*([a-g]\\+{0,3})(?![a-z])").find(t)?.groupValues?.get(1)?.uppercase()
        val consumo = Regex("(\\d+[.,]?\\d*)\\s*kwh\\s*/?\\s*(a[nñ]o|mes)?").find(t)?.let { m ->
            m.groupValues[1] + " kWh" + when {
                m.groupValues[2].startsWith("a") -> "/año"
                m.groupValues[2] == "mes" -> "/mes"
                else -> ""
            }
        }
        if (clase == null && consumo == null) return null
        return EficienciaEnergetica(clase, consumo)
    }
}
