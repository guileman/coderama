package ar.coderama.app.logica

import ar.coderama.app.data.AlertaAnmat
import ar.coderama.app.data.Perfil
import ar.coderama.app.data.Producto
import ar.coderama.app.data.Restriccion
import java.text.Normalizer
import kotlin.math.roundToInt

enum class Nivel { OK, ATENCION, NO }

data class Alerta(val nivel: Nivel, val titulo: String, val detalle: String? = null)

data class ResultadoPerfil(val perfil: Perfil, val nivel: Nivel, val alertas: List<Alerta>, val etiqueta: String)

fun normalizar(t: String?): String {
    if (t == null) return ""
    val sinTildes = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return sinTildes.lowercase().replace(Regex("[^a-z0-9%]+"), " ").trim()
}

private fun contienePalabra(texto: String, palabras: List<String>): String? =
    palabras.firstOrNull { p -> Regex("(^| )${Regex.escape(p)}( |$)").containsMatchIn(texto) }

object Analisis {

    private val edulcorantesTags = listOf(
        "en:e950", "en:e951", "en:e952", "en:e954", "en:e955", "en:e957", "en:e959",
        "en:e960", "en:e960a", "en:e960b", "en:e960c", "en:e961", "en:e962", "en:e969",
    )
    private val edulcorantesTexto = listOf(
        "sucralosa", "aspartamo", "aspartame", "acesulfame", "acesulfame k", "acesulfamo", "sacarina",
        "sacarinato", "ciclamato", "ciclamato de sodio", "stevia", "esteviol", "glucosidos de esteviol",
        "neotamo", "advantamo", "edulcorante", "edulcorantes", "taumatina",
    )
    private val cafeinaTags = listOf("en:caffeine", "en:coffee", "en:guarana", "en:green-tea", "en:mate")
    private val cafeinaTexto = listOf("cafeina", "cafe", "guarana", "te verde", "te negro", "yerba mate", "extracto de cafe")

    // Ingredientes por restricción: etiquetas de Open Food Facts y palabras para texto libre
    private data class Regla(val tags: List<String>, val palabras: List<String>, val nombre: String)

    private val reglas = mapOf(
        Restriccion.SIN_TACC to Regla(
            listOf("en:gluten"),
            listOf("trigo", "avena", "cebada", "centeno", "gluten", "malta", "harina de trigo", "salvado de trigo", "semola"),
            "gluten (TACC)",
        ),
        Restriccion.SIN_LACTOSA to Regla(
            listOf("en:milk"),
            listOf("leche", "lactosa", "suero lacteo", "suero de leche", "manteca", "queso", "crema de leche", "caseinato", "lacteo", "lacteos"),
            "leche / lactosa",
        ),
        Restriccion.SIN_FRUTOS_SECOS to Regla(
            listOf("en:nuts"),
            listOf("almendra", "almendras", "nuez", "nueces", "avellana", "avellanas", "castanas de caju", "pistacho", "frutos secos"),
            "frutos secos",
        ),
        Restriccion.SIN_MANI to Regla(listOf("en:peanuts"), listOf("mani", "cacahuate", "cacahuete"), "maní"),
        Restriccion.SIN_HUEVO to Regla(listOf("en:eggs"), listOf("huevo", "huevos", "clara de huevo", "yema", "albumina"), "huevo"),
        Restriccion.SIN_SOJA to Regla(listOf("en:soybeans"), listOf("soja", "lecitina de soja", "soya"), "soja"),
        Restriccion.SIN_PESCADO_MARISCOS to Regla(
            listOf("en:fish", "en:crustaceans", "en:molluscs"),
            listOf("pescado", "atun", "merluza", "camaron", "camarones", "langostino", "langostinos", "mariscos", "calamar", "mejillon", "anchoa"),
            "pescados o mariscos",
        ),
    )

    private val animales = listOf(
        "carne", "pollo", "cerdo", "vacuno", "jamon", "panceta", "gelatina", "grasa bovina", "pescado", "atun", "anchoa",
    )
    private val derivadosAnimales = listOf("leche", "huevo", "miel", "manteca", "queso", "suero lacteo", "caseinato", "lactosa")

    fun edulcorantes(p: Producto): Boolean =
        p.aditivos.any { it in edulcorantesTags } ||
            p.ingredientesTags.any { it.contains("sweetener") || it.contains("sucralose") || it.contains("aspartame") } ||
            contienePalabra(normalizar(p.ingredientes), edulcorantesTexto) != null

    fun cafeina(p: Producto): Boolean =
        p.ingredientesTags.any { it in cafeinaTags } ||
            contienePalabra(normalizar(p.ingredientes), cafeinaTexto) != null ||
            normalizar(p.nombre).contains("energizante")

    /** Analiza un producto para un perfil: dieta, calorías y, si es un chico, aptitud infantil. */
    fun evaluar(prod: Producto, perfil: Perfil): ResultadoPerfil {
        val alertas = mutableListOf<Alerta>()
        if (prod.esAlimento) {
            perfil.restricciones.forEach { alertas += restriccion(prod, it) }
            alertas += calorias(prod, perfil)
            if (perfil.esNino) alertas += ninos(prod)
        }
        val nivel = alertas.maxOfOrNull { it.nivel } ?: Nivel.OK
        val etiqueta = if (!prod.esAlimento) "No es alimento" else if (perfil.esNino) {
            when (nivel) { Nivel.OK -> "Apto"; Nivel.ATENCION -> "Consumo ocasional"; Nivel.NO -> "No recomendado" }
        } else {
            when (nivel) { Nivel.OK -> "Va con tu perfil"; Nivel.ATENCION -> "Con cuidado"; Nivel.NO -> "No va con tu dieta" }
        }
        return ResultadoPerfil(perfil, nivel, alertas.sortedByDescending { it.nivel }, etiqueta)
    }

    private fun restriccion(p: Producto, r: Restriccion): List<Alerta> {
        val texto = normalizar(p.ingredientes)
        val n = p.nutrientes
        reglas[r]?.let { regla ->
            val enTags = p.alergenos.any { it in regla.tags }
            val enTexto = contienePalabra(texto, regla.palabras)
            val trazas = p.trazas.any { it in regla.tags } || Regex("puede contener[^.]*").find(texto)?.value
                ?.let { contienePalabra(it, regla.palabras) } != null
            val libre = when (r) {
                Restriccion.SIN_TACC -> p.sellos.any { it == "en:no-gluten" || it == "en:gluten-free" || it.contains("sin-tacc") }
                Restriccion.SIN_LACTOSA -> p.sellos.any { it.contains("lactose-free") || it.contains("sin-lactosa") }
                else -> false
            }
            return when {
                libre -> listOf(Alerta(Nivel.OK, "${r.etiqueta}: el producto lo declara en su rótulo"))
                enTags || enTexto != null -> listOf(
                    Alerta(Nivel.NO, "Contiene ${regla.nombre}", enTexto?.let { "Ingrediente detectado: $it" }),
                )
                trazas -> listOf(Alerta(Nivel.ATENCION, "Puede contener trazas de ${regla.nombre}"))
                p.ingredientes == null && p.alergenos.isEmpty() -> listOf(
                    Alerta(Nivel.ATENCION, "${r.etiqueta}: no hay datos de ingredientes", "Revisá el rótulo del envase."),
                )
                else -> emptyList()
            }
        }
        return when (r) {
            Restriccion.VEGANO -> when {
                p.analisis.contains("en:non-vegan") ||
                    contienePalabra(texto, animales + derivadosAnimales) != null -> listOf(Alerta(Nivel.NO, "No es vegano"))
                p.analisis.contains("en:maybe-vegan") || p.analisis.contains("en:vegan-status-unknown") ->
                    listOf(Alerta(Nivel.ATENCION, "Puede no ser vegano", "Algunos ingredientes pueden ser de origen animal."))
                else -> emptyList()
            }
            Restriccion.VEGETARIANO -> when {
                p.analisis.contains("en:non-vegetarian") || contienePalabra(texto, animales) != null ->
                    listOf(Alerta(Nivel.NO, "No es vegetariano"))
                else -> emptyList()
            }
            Restriccion.BAJO_SODIO -> {
                val s = n?.sodioMg ?: return emptyList()
                when {
                    Nutricion.Sello.SODIO in Nutricion.sellos(p) -> listOf(Alerta(Nivel.NO, "Alto en sodio", "${s.roundToInt()} mg cada 100 g"))
                    s > 120 -> listOf(Alerta(Nivel.ATENCION, "Sodio moderado", "${s.roundToInt()} mg cada 100 g"))
                    else -> emptyList()
                }
            }
            Restriccion.BAJO_AZUCAR -> {
                val a = n?.azucares ?: return emptyList()
                when {
                    Nutricion.Sello.AZUCARES in Nutricion.sellos(p) || a > 15 ->
                        listOf(Alerta(Nivel.NO, "Alto en azúcares", "${fmt(a)} g cada 100 g"))
                    a > 5 -> listOf(Alerta(Nivel.ATENCION, "Azúcares moderados", "${fmt(a)} g cada 100 g"))
                    else -> emptyList()
                }
            }
            Restriccion.BAJO_CARBOHIDRATOS -> {
                val c = n?.carbohidratos ?: return emptyList()
                when {
                    c > 15 -> listOf(Alerta(Nivel.NO, "Alto en carbohidratos", "${fmt(c)} g cada 100 g"))
                    c > 5 -> listOf(Alerta(Nivel.ATENCION, "Carbohidratos moderados", "${fmt(c)} g cada 100 g"))
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun calorias(p: Producto, perfil: Perfil): List<Alerta> {
        val kcal100 = p.nutrientes?.kcal ?: return emptyList()
        val req = Nutricion.requerimientos(perfil).kcal
        val lista = mutableListOf<Alerta>()
        p.porcionG?.let { g ->
            val pct = kcal100 * g / 100 / req
            if (pct >= 0.25) lista += Alerta(
                Nivel.ATENCION,
                "Una porción es el ${(pct * 100).roundToInt()}% de tus calorías diarias",
                "Porción de ${fmt(g)} g = ${(kcal100 * g / 100).roundToInt()} kcal de ${req.roundToInt()} kcal/día",
            )
        }
        p.totalG?.let { g ->
            val pct = kcal100 * g / 100 / req
            when {
                pct >= 1.0 -> lista += Alerta(
                    Nivel.NO,
                    "El envase completo supera tus calorías del día",
                    "${(kcal100 * g / 100).roundToInt()} kcal en total (${(pct * 100).roundToInt()}% de ${req.roundToInt()} kcal)",
                )
                pct >= 0.5 -> lista += Alerta(
                    Nivel.ATENCION,
                    "El envase completo es el ${(pct * 100).roundToInt()}% de tus calorías diarias",
                    "${(kcal100 * g / 100).roundToInt()} kcal en total",
                )
            }
        }
        return lista
    }

    private fun ninos(p: Producto): List<Alerta> {
        val lista = mutableListOf<Alerta>()
        if (edulcorantes(p)) lista += Alerta(
            Nivel.NO, "Contiene edulcorantes", "La ley de etiquetado indica que no se recomienda su consumo en niños.",
        )
        if (cafeina(p)) lista += Alerta(
            Nivel.NO, "Contiene cafeína", "La ley de etiquetado indica evitarla en niños.",
        )
        val sellos = Nutricion.sellos(p)
        when {
            sellos.size >= 2 -> lista += Alerta(Nivel.NO, "Tiene ${sellos.size} sellos de exceso", sellos.joinToString { it.texto.lowercase() })
            sellos.size == 1 -> lista += Alerta(Nivel.ATENCION, "Tiene un sello de exceso", sellos.first().texto.lowercase())
        }
        if (p.nova == 4) lista += Alerta(Nivel.ATENCION, "Ultraprocesado", "Clasificación NOVA 4. Mejor de manera ocasional.")
        if (lista.isEmpty()) lista += Alerta(Nivel.OK, "Sin advertencias para niños")
        return lista
    }

    /** Alertas de la ANMAT que podrían corresponder a este producto (por marca y nombre). */
    fun coincidenciasAnmat(p: Producto, alertas: List<AlertaAnmat>): List<AlertaAnmat> {
        val texto = " ${normalizar(p.nombre + " " + (p.marca ?: ""))} "
        val marca = normalizar(p.marca)
        return alertas.filter { a ->
            a.claves.any { clave ->
                val palabras = clave.split(" ").filter { it.length >= 3 }
                palabras.isNotEmpty() && palabras.all { texto.contains(" $it ") } &&
                    (marca.isEmpty() || palabras.any { marca.split(" ").contains(it) })
            }
        }
    }

    fun fmt(v: Double): String =
        if (v >= 10 || v == v.roundToInt().toDouble()) v.roundToInt().toString()
        else String.format(java.util.Locale("es", "AR"), "%.1f", v)
}
