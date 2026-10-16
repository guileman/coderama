package ar.coderama.app.data

import kotlinx.serialization.json.JsonObject

/**
 * Busca el producto en las bases abiertas de Open Food Facts y sus hermanas:
 * alimentos, cosméticos e higiene, y otros productos (limpieza, electro, herramientas).
 */
class Productos {

    private val bases = listOf(
        "https://world.openfoodfacts.org" to Fuente.ALIMENTOS,
        "https://world.openbeautyfacts.org" to Fuente.COSMETICOS,
        "https://world.openproductsfacts.org" to Fuente.PRODUCTOS,
    )

    private val campos = listOf(
        "code", "product_name", "product_name_es", "generic_name_es", "brands", "quantity",
        "image_front_url", "image_url", "categories_tags", "ingredients_text", "ingredients_text_es",
        "allergens_tags", "traces_tags", "additives_tags", "ingredients_tags", "ingredients_analysis_tags",
        "labels_tags", "nova_group", "nutriscore_grade", "nutriments", "serving_quantity",
        "product_quantity", "product_quantity_unit", "url",
    ).joinToString(",")

    suspend fun buscar(codigo: String): Producto? {
        for ((base, fuente) in bases) {
            val p = runCatching { consultar(base, fuente, codigo) }.getOrNull()
            if (p != null) return p
        }
        return null
    }

    private suspend fun consultar(base: String, fuente: Fuente, codigo: String): Producto? {
        val cuerpo = Red.texto("$base/api/v2/product/$codigo.json?fields=$campos") ?: return null
        val raiz = Red.json.parseToJsonElement(cuerpo).obj() ?: return null
        if ((raiz["status"].num() ?: 0.0) < 1.0) return null
        val p = raiz["product"].obj() ?: return null
        val nombre = p["product_name_es"].str() ?: p["product_name"].str() ?: p["generic_name_es"].str()
        if (nombre == null && p["brands"].str() == null) return null
        return convertir(p, fuente, codigo, nombre ?: "Producto $codigo")
    }

    private fun convertir(p: JsonObject, fuente: Fuente, codigo: String, nombre: String): Producto {
        val n = p["nutriments"].obj()
        val nutrientes = n?.let {
            val sodio = it["sodium_100g"].num()?.times(1000)
                ?: it["salt_100g"].num()?.times(400)
            Nutrientes(
                kcal = it["energy-kcal_100g"].num() ?: it["energy_100g"].num()?.div(4.184),
                grasas = it["fat_100g"].num(),
                saturadas = it["saturated-fat_100g"].num(),
                trans = it["trans-fat_100g"].num(),
                carbohidratos = it["carbohydrates_100g"].num(),
                azucares = it["sugars_100g"].num(),
                fibra = it["fiber_100g"].num(),
                proteinas = it["proteins_100g"].num(),
                sodioMg = sodio,
            )
        }?.takeIf { !it.vacio }
        val categorias = p["categories_tags"].strings()
        val unidad = p["product_quantity_unit"].str()?.lowercase()
        val cantidadTexto = p["quantity"].str()
        val esBebida = categorias.any { it.contains("beverages") || it.contains("bebidas") } ||
            unidad == "ml" || unidad == "l" ||
            (cantidadTexto?.lowercase()?.let { Regex("""\d\s*(ml|cc|l)\b""").containsMatchIn(it) } ?: false)
        return Producto(
            codigo = codigo,
            nombre = nombre,
            marca = p["brands"].str()?.split(",")?.firstOrNull()?.trim(),
            cantidad = cantidadTexto,
            imagen = p["image_front_url"].str() ?: p["image_url"].str(),
            fuente = fuente,
            categorias = categorias,
            ingredientes = p["ingredients_text_es"].str() ?: p["ingredients_text"].str(),
            alergenos = p["allergens_tags"].strings(),
            trazas = p["traces_tags"].strings(),
            aditivos = p["additives_tags"].strings(),
            ingredientesTags = p["ingredients_tags"].strings(),
            analisis = p["ingredients_analysis_tags"].strings(),
            sellos = p["labels_tags"].strings(),
            nova = p["nova_group"].num()?.toInt(),
            nutriscore = p["nutriscore_grade"].str()?.takeIf { it.length == 1 },
            nutrientes = nutrientes,
            porcionG = p["serving_quantity"].num()?.takeIf { it > 0 },
            totalG = p["product_quantity"].num()?.takeIf { it > 0 },
            esBebida = esBebida,
            url = p["url"].str(),
        )
    }
}
