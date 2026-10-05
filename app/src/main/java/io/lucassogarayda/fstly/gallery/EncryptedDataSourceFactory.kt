package io.lucassogarayda.fstly.gallery

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import javax.crypto.SecretKey

class EncryptedDataSourceFactory(
    private val archivoCifrado: File,
    private val clave: SecretKey
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return EncryptedFileDataSource(archivoCifrado, clave)
    }
}
