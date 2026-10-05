package io.lucassogarayda.fstly.gallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class BovedaManager(private val context: Context) {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "fstly_vault_key"
        private const val TRANSFORM = "AES/CTR/NoPadding"
        private const val IV_SIZE = 16
        private const val EXTENSION = ".enc"
    }

    private val carpetaBoveda: File
        get() {
            val carpeta = File(context.getExternalFilesDir(null), ".fstly_vault")
            if (!carpeta.exists()) carpeta.mkdirs()
            return carpeta
        }

    private fun obtenerClave(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) {
            return (keyStore.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_CTR)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun moverABoveda(archivoOriginal: File): Boolean {
        return try {
            if (!archivoOriginal.exists()) return false

            val clave = obtenerClave()
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, clave)
            val iv = cipher.iv

            val destino = File(carpetaBoveda, archivoOriginal.name + EXTENSION)

            FileOutputStream(destino).use { out ->
                out.write(iv)

                archivoOriginal.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var leidos: Int
                    while (input.read(buffer).also { leidos = it } > 0) {
                        val cifrado = cipher.update(buffer, 0, leidos)
                        if (cifrado != null) out.write(cifrado)
                    }
                }

                val final = cipher.doFinal()
                if (final != null) out.write(final)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun obtenerUriOriginal(archivo: File): Uri? {
        return try {
            val esVideo = esVideo(archivo)
            val collection = if (esVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val dataColumn = if (esVideo) {
                MediaStore.Video.Media.DATA
            } else {
                MediaStore.Images.Media.DATA
            }
            val idColumn = if (esVideo) {
                MediaStore.Video.Media._ID
            } else {
                MediaStore.Images.Media._ID
            }

            val cursor = context.contentResolver.query(
                collection,
                arrayOf(idColumn),
                "$dataColumn = ?",
                arrayOf(archivo.absolutePath),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(idColumn))
                    android.content.ContentUris.withAppendedId(collection, id)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun descifrarArchivo(archivoCifrado: File): ByteArray? {
        return try {
            if (!archivoCifrado.exists()) return null
            val clave = obtenerClave()
            val bytes = archivoCifrado.readBytes()
            if (bytes.size < IV_SIZE) return null
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val datosCifrados = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            val spec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, clave, spec)
            cipher.doFinal(datosCifrados)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun descifrarAArchivo(archivoCifrado: File, destino: File): Boolean {
        return try {
            if (!archivoCifrado.exists()) return false

            val clave = obtenerClave()
            val bytes = archivoCifrado.readBytes()
            if (bytes.size < IV_SIZE) return false

            val iv = bytes.copyOfRange(0, IV_SIZE)
            val datosCifrados = bytes.copyOfRange(IV_SIZE, bytes.size)

            val cipher = Cipher.getInstance(TRANSFORM)
            val spec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, clave, spec)

            FileOutputStream(destino).use { out ->
                var offset = 0
                val chunkSize = 64 * 1024
                while (offset < datosCifrados.size) {
                    val fin = minOf(offset + chunkSize, datosCifrados.size)
                    val chunk = datosCifrados.copyOfRange(offset, fin)
                    val descifrado = cipher.update(chunk)
                    if (descifrado != null) out.write(descifrado)
                    offset = fin
                }
                val final = cipher.doFinal()
                if (final != null) out.write(final)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun descifrarAGuardar(archivoCifrado: File): File? {
        return try {
            val nombreOriginal = archivoCifrado.name.removeSuffix(EXTENSION)
            val esVideo = esVideo(File(nombreOriginal))

            val directorio = if (esVideo) {
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            } else {
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            }

            val destino = File(directorio, nombreOriginal)

            if (descifrarAArchivo(archivoCifrado, destino)) {
                archivoCifrado.delete()
                destino
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun obtenerFotosBoveda(): List<File> {
        return try {
            carpetaBoveda.listFiles { file ->
                file.isFile && file.name.endsWith(EXTENSION)
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun esBoveda(archivo: File): Boolean {
        return archivo.absolutePath.contains("/.fstly_vault/")
    }

    fun esVideo(archivo: File): Boolean {
        val nombre = archivo.name.lowercase()
        return nombre.endsWith(".mp4") || nombre.endsWith(".mkv") ||
                nombre.endsWith(".webm") || nombre.endsWith(".avi") ||
                nombre.endsWith(".mov") || nombre.endsWith(".3gp") ||
                nombre.endsWith(".m4v") || nombre.endsWith(".mp4.enc") ||
                nombre.endsWith(".mkv.enc") || nombre.endsWith(".webm.enc") ||
                nombre.endsWith(".avi.enc") || nombre.endsWith(".mov.enc") ||
                nombre.endsWith(".3gp.enc") || nombre.endsWith(".m4v.enc")
    }

    fun borrarDeBoveda(archivoCifrado: File): Boolean {
        return try {
            archivoCifrado.delete()
        } catch (e: Exception) {
            false
        }
    }
}
