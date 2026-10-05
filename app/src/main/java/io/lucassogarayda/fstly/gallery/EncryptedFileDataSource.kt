package io.lucassogarayda.fstly.gallery

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class EncryptedFileDataSource(
    private val archivoCifrado: File,
    private val clave: javax.crypto.SecretKey,
    private val listener: TransferListener? = null
) : BaseDataSource(false) {

    companion object {
        private const val TRANSFORM = "AES/CTR/NoPadding"
        private const val IV_SIZE = 16
        private const val BUFFER_SIZE = 32 * 1024
    }

    private var randomAccessFile: RandomAccessFile? = null
    private var posicion: Long = 0
    private var bytesRestantes: Long = 0
    private val iv = ByteArray(IV_SIZE)

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        if (!archivoCifrado.exists()) {
            throw java.io.FileNotFoundException("No existe: ${archivoCifrado.absolutePath}")
        }

        randomAccessFile = RandomAccessFile(archivoCifrado, "r")

        randomAccessFile!!.seek(0)
        randomAccessFile!!.readFully(iv)

        val tamañoTotal = randomAccessFile!!.length()
        val tamañoDescifrado = tamañoTotal - IV_SIZE

        posicion = dataSpec.position
        bytesRestantes = tamañoDescifrado - posicion

        if (bytesRestantes < 0) {
            throw IllegalArgumentException("Posición fuera de rango")
        }

        transferStarted(dataSpec)

        return bytesRestantes
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRestantes == 0L) return C.RESULT_END_OF_INPUT

        val aLeer = minOf(length.toLong(), bytesRestantes).toInt()

        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT

        val bloqueInicial = posicion / 16
        val offsetEnBloque = (posicion % 16).toInt()

        val ivAjustado = ByteArray(IV_SIZE)
        System.arraycopy(iv, 0, ivAjustado, 0, IV_SIZE)

        var carry: Long = bloqueInicial
        for (i in (IV_SIZE - 1) downTo 0) {
            val suma = (ivAjustado[i].toInt() and 0xFF) + (carry and 0xFF).toInt()
            ivAjustado[i] = suma.toByte()
            carry = (carry shr 8) + (suma shr 8)
        }

        val cipher = Cipher.getInstance(TRANSFORM)
        val spec = IvParameterSpec(ivAjustado)
        cipher.init(Cipher.DECRYPT_MODE, clave, spec)

        val bloqueOffset = (bloqueInicial * 16).toLong()
        raf.seek(IV_SIZE + bloqueOffset)
        val bytesLeidos = raf.read(buffer, offset, aLeer)
        if (bytesLeidos <= 0) return C.RESULT_END_OF_INPUT

        val descifrado = cipher.update(buffer, offset, bytesLeidos)

        if (descifrado != null) {
            System.arraycopy(descifrado, 0, buffer, offset, descifrado.size)

            if (offsetEnBloque > 0 && descifrado.size >= offsetEnBloque) {
                System.arraycopy(buffer, offset + offsetEnBloque, buffer, offset, descifrado.size - offsetEnBloque)
                posicion += (descifrado.size - offsetEnBloque)
                bytesRestantes -= (descifrado.size - offsetEnBloque)
                bytesTransferred(descifrado.size - offsetEnBloque)
                return descifrado.size - offsetEnBloque
            }

            posicion += descifrado.size
            bytesRestantes -= descifrado.size
            bytesTransferred(descifrado.size)
            return descifrado.size
        }

        return 0
    }

    override fun getUri(): Uri = Uri.fromFile(archivoCifrado)

    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (e: Exception) {
        } finally {
            randomAccessFile = null
            transferEnded()
        }
    }
}
