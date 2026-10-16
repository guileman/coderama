package ar.coderama.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Red {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Devuelve el cuerpo como texto, o null si el servidor responde 404. */
    suspend fun texto(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Coderama/0.1 (Android; app de comparacion de precios)")
            .build()
        cliente.newCall(req).execute().use { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> throw IOException("HTTP ${r.code}")
                else -> r.body?.string()
            }
        }
    }
}

// ---------------------------------------------------------- ayudas para JSON

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray

fun JsonElement?.str(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content.trim().takeIf { it.isNotEmpty() }
}

fun JsonElement?.num(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.doubleOrNull ?: p.content.replace(",", ".").toDoubleOrNull()
}

fun JsonElement?.strings(): List<String> = arr()?.mapNotNull { it.str() } ?: emptyList()

/** Normaliza un código de barras para compararlo con el SEPA (sin ceros a la izquierda). */
fun normalizarCodigo(codigo: String): String = codigo.filter { it.isDigit() }.trimStart('0')
