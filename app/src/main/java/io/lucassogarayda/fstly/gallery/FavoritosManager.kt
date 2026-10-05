package io.lucassogarayda.fstly.gallery

import android.content.Context
import android.content.SharedPreferences

class FavoritosManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fstly_favoritos", Context.MODE_PRIVATE)

    fun esFavorito(ruta: String): Boolean {
        return prefs.getStringSet("favoritos", emptySet())?.contains(ruta) == true
    }

    fun toggleFavorito(ruta: String): Boolean {
        val actuales = prefs.getStringSet("favoritos", emptySet())?.toMutableSet() ?: mutableSetOf()
        val eraFavorito = actuales.contains(ruta)
        if (eraFavorito) {
            actuales.remove(ruta)
        } else {
            actuales.add(ruta)
        }
        prefs.edit().putStringSet("favoritos", actuales).apply()
        return !eraFavorito
    }

    fun obtenerFavoritos(): Set<String> {
        return prefs.getStringSet("favoritos", emptySet()) ?: emptySet()
    }

    fun limpiar() {
        prefs.edit().remove("favoritos").apply()
    }
}
