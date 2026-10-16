package ar.coderama.app.logica

import ar.coderama.app.data.Producto

/**
 * Detecta sustancias de cuidado en productos que no son alimentos:
 * cosméticos, higiene personal, limpieza y químicos del hogar.
 * Busca por nombre INCI (en inglés) y por nombre en castellano.
 */
object Peligros {

    private data class Sustancia(val nivel: Nivel, val nombre: String, val motivo: String, val claves: List<String>)

    private val cosmeticos = listOf(
        Sustancia(Nivel.NO, "Mercurio", "Metal tóxico prohibido en cosméticos.", listOf("mercury", "mercurio", "thimerosal", "timerosal", "calomel")),
        Sustancia(Nivel.NO, "Hidroquinona", "Despigmentante restringido; puede irritar y dañar la piel.", listOf("hydroquinone", "hidroquinona")),
        Sustancia(Nivel.NO, "Acetato de plomo", "Compuesto de plomo, tóxico.", listOf("lead acetate", "acetato de plomo")),
        Sustancia(Nivel.ATENCION, "Liberador de formaldehído", "Puede liberar formaldehído, sensibilizante y posible cancerígeno.",
            listOf("formaldehyde", "formaldehido", "formol", "dmdm hydantoin", "quaternium 15", "imidazolidinyl urea", "diazolidinyl urea", "bronopol", "2 bromo 2 nitropropane 1 3 diol", "methenamine")),
        Sustancia(Nivel.ATENCION, "Isotiazolinonas", "Conservantes con alta tasa de alergia de contacto.",
            listOf("methylisothiazolinone", "methylchloroisothiazolinone", "metilisotiazolinona", "metilcloroisotiazolinona")),
        Sustancia(Nivel.ATENCION, "Parabenos de cadena larga", "Restringidos en la Unión Europea por posible disrupción hormonal.",
            listOf("propylparaben", "butylparaben", "isopropylparaben", "isobutylparaben", "propilparabeno", "butilparabeno")),
        Sustancia(Nivel.ATENCION, "Triclosán", "Antibacteriano restringido; posible disruptor hormonal.", listOf("triclosan", "triclocarban")),
        Sustancia(Nivel.ATENCION, "Ftalatos", "Plastificantes con efectos hormonales.", listOf("dibutyl phthalate", "diethylhexyl phthalate", "dehp", "ftalato")),
        Sustancia(Nivel.ATENCION, "BHA", "Antioxidante clasificado como posible cancerígeno.", listOf("bha", "butylated hydroxyanisole", "butilhidroxianisol")),
        Sustancia(Nivel.ATENCION, "Parafenilendiamina", "Tinte capilar muy alergénico.", listOf("p phenylenediamine", "phenylenediamine", "parafenilendiamina", "ppd")),
        Sustancia(Nivel.ATENCION, "Alquitrán de hulla", "Derivado de alquitrán, restringido.", listOf("coal tar", "alquitran de hulla")),
        Sustancia(Nivel.ATENCION, "Oxibenzona", "Filtro solar con potencial alergénico y hormonal.", listOf("oxybenzone", "benzophenone 3", "oxibenzona")),
        Sustancia(Nivel.ATENCION, "Alérgenos de fragancia", "Pueden causar alergia en piel sensible.",
            listOf("limonene", "linalool", "citral", "geraniol", "eugenol", "coumarin", "cinnamal", "hydroxycitronellal", "isoeugenol", "lilial", "butylphenyl methylpropional")),
        Sustancia(Nivel.ATENCION, "Lauril sulfato de sodio", "Tensioactivo que puede irritar piel y mucosas.", listOf("sodium lauryl sulfate", "lauril sulfato de sodio")),
    )

    private val hogar = listOf(
        Sustancia(Nivel.ATENCION, "Hipoclorito de sodio (lavandina)", "Corrosivo e irritante. NUNCA mezclar con amoníaco, ácidos (vinagre, antisarro, limpiadores de baño) ni alcohol: libera gases tóxicos.",
            listOf("hipoclorito", "sodium hypochlorite", "lavandina", "cloro activo", "dicloroisocianurato")),
        Sustancia(Nivel.ATENCION, "Amoníaco", "Irritante. NUNCA mezclar con lavandina: forma cloraminas tóxicas.", listOf("amoniaco", "ammonia", "hidroxido de amonio", "ammonium hydroxide")),
        Sustancia(Nivel.NO, "Ácido clorhídrico (muriático)", "Muy corrosivo. NUNCA mezclar con lavandina: libera cloro gaseoso.", listOf("acido clorhidrico", "acido muriatico", "hydrochloric acid")),
        Sustancia(Nivel.ATENCION, "Ácidos limpiadores", "Corrosivos/irritantes. No mezclar con lavandina.", listOf("acido fosforico", "acido sulfamico", "acido citrico al", "phosphoric acid", "acido oxalico")),
        Sustancia(Nivel.NO, "Hidróxido de sodio (soda cáustica)", "Muy corrosivo: quemaduras graves en piel y ojos.", listOf("hidroxido de sodio", "soda caustica", "sodium hydroxide", "hidroxido de potasio", "potassium hydroxide")),
        Sustancia(Nivel.ATENCION, "Peróxido de hidrógeno", "Oxidante. No mezclar con vinagre ni con otros limpiadores.", listOf("peroxido de hidrogeno", "agua oxigenada", "hydrogen peroxide")),
        Sustancia(Nivel.ATENCION, "Solventes inflamables", "Inflamables y nocivos por inhalación. Usar con ventilación, lejos del fuego.",
            listOf("aguarras", "thinner", "tolueno", "toluene", "xileno", "xylene", "acetona", "acetone", "nafta", "kerosene", "querosene", "hexano", "butano", "propano")),
        Sustancia(Nivel.ATENCION, "Insecticidas", "Tóxicos. Lejos de niños, mascotas, alimentos y peceras.",
            listOf("cipermetrina", "permetrina", "tetrametrina", "praletrina", "imidacloprid", "deltametrina", "transflutrina", "fipronil", "cypermethrin", "permethrin")),
        Sustancia(Nivel.ATENCION, "Rodenticidas", "Muy tóxicos si se ingieren.", listOf("brodifacoum", "bromadiolona", "difenacoum", "raticida")),
        Sustancia(Nivel.ATENCION, "Metanol", "Tóxico si se ingiere o inhala.", listOf("metanol", "methanol", "alcohol metilico")),
    )

    private val pictogramas = mapOf(
        "corrosivo" to "Corrosivo: puede causar quemaduras graves.",
        "inflamable" to "Inflamable: mantener lejos del fuego y del calor.",
        "toxico" to "Tóxico: peligroso si se ingiere, inhala o toca.",
        "veneno" to "Veneno: mantener fuera del alcance de los niños.",
        "nocivo" to "Nocivo para la salud.",
        "irritante" to "Irritante para piel, ojos o vías respiratorias.",
        "peligro" to "El rótulo indica PELIGRO: leer las precauciones.",
    )

    data class Resultado(val alertas: List<Alerta>, val esQuimicoDelHogar: Boolean)

    fun analizar(p: Producto, textoExtra: String? = null): Resultado {
        val texto = " " + normalizar(listOfNotNull(p.ingredientes, p.nombre, textoExtra, p.categorias.joinToString(" ")).joinToString(" ")) + " "
        val alertas = mutableListOf<Alerta>()
        var quimico = false
        for (s in cosmeticos + hogar) {
            val hallada = s.claves.firstOrNull { texto.contains(" ${normalizar(it)} ") }
            if (hallada != null) {
                alertas += Alerta(s.nivel, s.nombre, s.motivo)
                if (s in hogar) quimico = true
            }
        }
        for ((palabra, motivo) in pictogramas) {
            if (texto.contains(" $palabra ")) {
                alertas += Alerta(Nivel.ATENCION, "Rótulo: ${palabra.replaceFirstChar { it.uppercase() }}", motivo)
                quimico = true
            }
        }
        if (!quimico && p.categorias.any { c -> listOf("cleaning", "limpieza", "detergent", "bleach", "household").any { c.contains(it) } }) {
            quimico = true
        }
        return Resultado(alertas.distinctBy { it.titulo }.sortedByDescending { it.nivel }, quimico)
    }
}
