package io.lucassogarayda.fstly.gallery

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PapeleraManager(private val context: Context) {

    companion object {
        private const val DIAS_RETENCION = 30
    }

    private val carpetaPapelera: File
        get() {
            val carpeta = File(context.getExternalFilesDir(null), ".fstly_trash")
            if (!carpeta.exists()) carpeta.mkdirs()
            return carpeta
        }

    fun moverAPapelera(archivo: File): Boolean {
        return try {
            if (!archivo.exists()) return false

            val destino = File(carpetaPapelera, "${System.currentTimeMillis()}_${archivo.name}")

            FileInputStream(archivo).use { input ->
                FileOutputStream(destino).use { output ->
                    input.copyTo(output)
                }
            }

            if (archivo.delete()) {
                true
            } else {
                destino.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restaurarDePapelera(archivoPapelera: File, destinoOriginal: File): Boolean {
        return try {
            if (!archivoPapelera.exists()) return false

            destinoOriginal.parentFile?.mkdirs()

            FileInputStream(archivoPapelera).use { input ->
                FileOutputStream(destinoOriginal).use { output ->
                    input.copyTo(output)
                }
            }

            archivoPapelera.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun obtenerArchivosPapelera(): List<File> {
        return try {
            carpetaPapelera.listFiles { file ->
                file.isFile
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun borrarDePapelera(archivo: File): Boolean {
        return try {
            archivo.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun vaciarPapelera(): Int {
        return try {
            var borrados = 0
            carpetaPapelera.listFiles()?.forEach { file ->
                if (file.delete()) borrados++
            }
            borrados
        } catch (e: Exception) {
            0
        }
    }

    fun limpiarViejos(): Int {
        return try {
            var borrados = 0
            val ahora = System.currentTimeMillis()
            val limite = ahora - (DIAS_RETENCION * 24 * 60 * 60 * 1000L)

            carpetaPapelera.listFiles()?.forEach { file ->
                if (file.lastModified() < limite) {
                    if (file.delete()) borrados++
                }
            }
            borrados
        } catch (e: Exception) {
            0
        }
    }

    fun nombreOriginal(archivo: File): String {
        return try {
            val nombre = archivo.name
            val guionBajo = nombre.indexOf('_')
            if (guionBajo > 0) nombre.substring(guionBajo + 1) else nombre
        } catch (e: Exception) {
            archivo.name
        }
    }

    fun esVideo(archivo: File): Boolean {
        val nombre = nombreOriginal(archivo).lowercase()
        return nombre.endsWith(".mp4") || nombre.endsWith(".mkv") ||
                nombre.endsWith(".webm") || nombre.endsWith(".avi") ||
                nombre.endsWith(".mov") || nombre.endsWith(".3gp") ||
                nombre.endsWith(".m4v")
    }

    fun rutaOriginal(archivo: File): File {
        val nombre = nombreOriginal(archivo)
        val esVideo = esVideo(archivo)

        val directorio = if (esVideo) {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        } else {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        }

        return File(directorio, nombre)
    }

    fun diasRestantes(archivo: File): Int {
        val ahora = System.currentTimeMillis()
        val creacion = archivo.lastModified()
        val transcurridos = (ahora - creacion) / (24 * 60 * 60 * 1000L)
        val restantes = DIAS_RETENCION - transcurridos
        return maxOf(0, restantes.toInt())
    }
}
