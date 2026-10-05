package io.lucassogarayda.fstly.gallery

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File

class FuentesManager(private val context: Context) {

    companion object {
        val FUENTES_SISTEMA = listOf(
            "sans-serif",
            "sans-serif-light",
            "sans-serif-condensed",
            "sans-serif-black",
            "serif",
            "monospace",
            "casual",
            "cursive"
        )
    }

    private val prefs = context.getSharedPreferences("fstly_prefs", Context.MODE_PRIVATE)
    private val carpetaFuentes = File(context.filesDir, "fonts").apply {
        if (!exists()) mkdirs()
    }

    fun obtenerFuenteActual(): String {
        return prefs.getString("fuente", "sans-serif") ?: "sans-serif"
    }

    fun guardarFuente(nombre: String) {
        prefs.edit().putString("fuente", nombre).apply()
    }

    fun importarFuente(origen: File): String? {
        return try {
            if (!origen.exists()) return null
            val ext = origen.extension.lowercase()
            if (ext != "ttf" && ext != "otf") return null

            val destino = File(carpetaFuentes, origen.name)
            origen.copyTo(destino, overwrite = true)
            destino.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun obtenerFuentesPersonalizadas(): List<File> {
        return try {
            carpetaFuentes.listFiles { file ->
                file.isFile && (file.extension.lowercase() == "ttf" || file.extension.lowercase() == "otf")
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun eliminarFuentePersonalizada(archivo: File): Boolean {
        return try {
            archivo.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun cargarTypeface(nombre: String): Typeface {
        return try {
            if (nombre.startsWith("/")) {
                Typeface.createFromFile(nombre)
            } else {
                Typeface.create(nombre, Typeface.NORMAL)
            }
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    fun aplicarFuente(textView: TextView, conNegrita: Boolean = true) {
        try {
            val typeface = cargarTypeface(obtenerFuenteActual())
            val actual = textView.typeface

            textView.typeface = when {
                actual == null -> typeface
                actual.isBold && actual.isItalic -> Typeface.create(typeface, Typeface.BOLD_ITALIC)
                actual.isBold -> Typeface.create(typeface, Typeface.BOLD)
                actual.isItalic -> Typeface.create(typeface, Typeface.ITALIC)
                conNegrita -> typeface
                else -> typeface
            }
        } catch (e: Exception) {
        }
    }

    fun aplicarFuenteATodo(view: View) {
        val typeface = cargarTypeface(obtenerFuenteActual())

        when (view) {
            is TextView -> {
                val actual = view.typeface
                view.typeface = when {
                    actual == null -> typeface
                    actual.isBold && actual.isItalic -> Typeface.create(typeface, Typeface.BOLD_ITALIC)
                    actual.isBold -> Typeface.create(typeface, Typeface.BOLD)
                    actual.isItalic -> Typeface.create(typeface, Typeface.ITALIC)
                    else -> typeface
                }
            }
            is EditText -> {
                val actual = view.typeface
                view.typeface = when {
                    actual == null -> typeface
                    actual.isBold -> Typeface.create(typeface, Typeface.BOLD)
                    else -> typeface
                }
            }
            is Button -> {
                val actual = view.typeface
                view.typeface = when {
                    actual == null -> typeface
                    actual.isBold -> Typeface.create(typeface, Typeface.BOLD)
                    else -> typeface
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                aplicarFuenteATodo(view.getChildAt(i))
            }
        }
    }
}
