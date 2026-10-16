package ar.coderama.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "coderama")

/** Guarda la configuración del usuario dentro del teléfono. */
class Almacen(private val context: Context) {
    private val clave = stringPreferencesKey("ajustes")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val ajustes: Flow<Ajustes> = context.dataStore.data.map { prefs ->
        prefs[clave]?.let { runCatching { json.decodeFromString(Ajustes.serializer(), it) }.getOrNull() } ?: Ajustes()
    }

    suspend fun actualizar(cambio: (Ajustes) -> Ajustes) {
        context.dataStore.edit { prefs ->
            val actual = prefs[clave]?.let {
                runCatching { json.decodeFromString(Ajustes.serializer(), it) }.getOrNull()
            } ?: Ajustes()
            prefs[clave] = json.encodeToString(Ajustes.serializer(), cambio(actual))
        }
    }
}
