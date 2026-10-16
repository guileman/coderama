package ar.coderama.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class Sexo(val etiqueta: String) { F("Mujer"), M("Varón") }

@Serializable
enum class Actividad(val etiqueta: String, val detalle: String, val factor: Double, val factorNinos: Double) {
    SEDENTARIA("Sedentaria", "Poco o nada de ejercicio", 1.2, 0.85),
    LIGERA("Ligera", "Ejercicio 1 a 3 días por semana", 1.375, 0.92),
    MODERADA("Moderada", "Ejercicio 3 a 5 días por semana", 1.55, 1.0),
    INTENSA("Intensa", "Ejercicio 6 o 7 días por semana", 1.725, 1.1),
    MUY_INTENSA("Muy intensa", "Deporte exigente o trabajo físico", 1.9, 1.2),
}

@Serializable
enum class Restriccion(val etiqueta: String) {
    SIN_TACC("Sin TACC (celiaquía)"),
    SIN_LACTOSA("Sin lactosa"),
    VEGANO("Vegano"),
    VEGETARIANO("Vegetariano"),
    BAJO_SODIO("Bajo en sodio"),
    BAJO_AZUCAR("Bajo en azúcar / diabetes"),
    BAJO_CARBOHIDRATOS("Bajo en carbohidratos"),
    SIN_FRUTOS_SECOS("Sin frutos secos"),
    SIN_MANI("Sin maní"),
    SIN_HUEVO("Sin huevo"),
    SIN_SOJA("Sin soja"),
    SIN_PESCADO_MARISCOS("Sin pescados ni mariscos"),
}

@Serializable
data class Perfil(
    val id: String,
    val nombre: String,
    val edad: Int,
    val sexo: Sexo,
    val pesoKg: Double,
    val alturaCm: Double,
    val actividad: Actividad,
    val restricciones: Set<Restriccion> = emptySet(),
) {
    val esNino: Boolean get() = edad < 18
}

@Serializable
data class Escaneo(
    val codigo: String,
    val nombre: String,
    val marca: String? = null,
    val imagen: String? = null,
    val fecha: Long,
)

@Serializable
data class Ajustes(
    val onboardingHecho: Boolean = false,
    val provinciaIso: String? = null,
    val provinciaNombre: String? = null,
    val localidadId: String? = null,
    val localidadNombre: String? = null,
    val cadenas: Set<String> = emptySet(),
    val perfiles: List<Perfil> = emptyList(),
    val historial: List<Escaneo> = emptyList(),
)

// ------------------------------------------------------------------ productos

enum class Fuente(val etiqueta: String) {
    ALIMENTOS("Open Food Facts"),
    COSMETICOS("Open Beauty Facts"),
    PRODUCTOS("Open Products Facts"),
    SEPA("Precios Claros (SEPA)"),
    ETIQUETA("Foto de la etiqueta"),
}

/** Valores cada 100 g (o 100 ml). Sodio en miligramos. */
data class Nutrientes(
    val kcal: Double? = null,
    val grasas: Double? = null,
    val saturadas: Double? = null,
    val trans: Double? = null,
    val carbohidratos: Double? = null,
    val azucares: Double? = null,
    val fibra: Double? = null,
    val proteinas: Double? = null,
    val sodioMg: Double? = null,
) {
    val vacio: Boolean
        get() = listOf(kcal, grasas, carbohidratos, proteinas, azucares, sodioMg).all { it == null }
}

data class Producto(
    val codigo: String,
    val nombre: String,
    val marca: String? = null,
    val cantidad: String? = null,
    val imagen: String? = null,
    val fuente: Fuente,
    val categorias: List<String> = emptyList(),
    val ingredientes: String? = null,
    val alergenos: List<String> = emptyList(),
    val trazas: List<String> = emptyList(),
    val aditivos: List<String> = emptyList(),
    val ingredientesTags: List<String> = emptyList(),
    val analisis: List<String> = emptyList(),
    val sellos: List<String> = emptyList(),
    val nova: Int? = null,
    val nutriscore: String? = null,
    val nutrientes: Nutrientes? = null,
    val porcionG: Double? = null,
    val totalG: Double? = null,
    val esBebida: Boolean = false,
    val url: String? = null,
    val alimentoDeclarado: Boolean = false,
) {
    val esAlimento: Boolean
        get() = alimentoDeclarado || fuente == Fuente.ALIMENTOS || (nutrientes != null && !nutrientes.vacio)
}

// ------------------------------------------------------------------ precios

@Serializable
data class LocalidadIdx(
    val id: String,
    val nombre: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val sucursales: Int = 0,
    val productos: Int = 0,
    val cadenas: List<String> = emptyList(),
)

@Serializable
data class ProvinciaIdx(val iso: String, val nombre: String, val localidades: List<LocalidadIdx> = emptyList())

@Serializable
data class Indice(
    val fecha: String = "",
    val cadenas: Map<String, String> = emptyMap(),
    val provincias: List<ProvinciaIdx> = emptyList(),
)

data class FilaPrecio(
    val cadenaId: String,
    val cadena: String,
    val minimo: Double,
    val maximo: Double,
    val sucursales: Int,
    val sucursalMasBarata: String?,
    val direccion: String?,
    val promo: Double?,
    val elegida: Boolean,
)

data class PreciosProducto(
    val descripcion: String,
    val marca: String,
    val fecha: String,
    val filas: List<FilaPrecio>,
)

data class AlertaAnmat(
    val categoria: String,
    val titulo: String,
    val fecha: String?,
    val url: String,
    val claves: List<String>,
)
