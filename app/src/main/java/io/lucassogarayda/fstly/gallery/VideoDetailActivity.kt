package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoDetailActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var archivo: File
    private var esBoveda = false
    private var archivoCifrado: File? = null
    private lateinit var papeleraManager: PapeleraManager

    private val borrarLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, getString(R.string.foto_borrada), Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        } else {
            Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
        }
    }

    private val borrarOriginalLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, getString(R.string.movida_a_boveda), Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        } else {
            Toast.makeText(this, getString(R.string.error_mover), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detail)

        FuentesManager(this).aplicarFuenteATodo(window.decorView)
        papeleraManager = PapeleraManager(this)

        val rutas = intent.getStringArrayListExtra("rutas") ?: arrayListOf()
        val posicion = intent.getIntExtra("posicion", 0)
        esBoveda = intent.getBooleanExtra("es_boveda", false)
        val rutaCifrada = intent.getStringExtra("archivo_cifrado")
        if (rutaCifrada != null) archivoCifrado = File(rutaCifrada)

        if (rutas.isEmpty() || posicion >= rutas.size) {
            finish()
            return
        }
        archivo = File(rutas[posicion])

        playerView = findViewById(R.id.playerView)
        val topBar: View = findViewById(R.id.topBar)
        val closeButton: ImageButton = findViewById(R.id.closeButton)
        val vaultButton: ImageButton = findViewById(R.id.vaultButton)
        val shareButton: ImageButton = findViewById(R.id.shareButton)
        val deleteButton: ImageButton = findViewById(R.id.deleteButton)
        val videoName: TextView = findViewById(R.id.videoName)

        videoName.text = archivo.name

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(archivo)))
        player.prepare()
        player.playWhenReady = true

        playerView.setOnClickListener {
            topBar.visibility = if (topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        closeButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        vaultButton.setOnClickListener {
            if (esBoveda) {
                Toast.makeText(this, R.string.ya_en_boveda, Toast.LENGTH_SHORT).show()
            } else {
                moverABoveda()
            }
        }

        shareButton.setOnClickListener {
            compartirVideo()
        }

        deleteButton.setOnClickListener {
            if (esBoveda && archivoCifrado != null) {
                confirmarBorrarDeBoveda()
            } else {
                preguntarBorrar()
            }
        }
    }

    private fun preguntarBorrar() {
        val opciones = arrayOf(
            getString(R.string.mover_a_papelera),
            getString(R.string.borrar_directo),
            getString(R.string.cancelar)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.borrar))
            .setMessage(getString(R.string.pregunta_borrar))
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> moverAPapelera()
                    1 -> confirmarBorrar()
                }
            }
            .show()
    }

    private fun moverAPapelera() {
        if (papeleraManager.moverAPapelera(archivo)) {
            Toast.makeText(this, getString(R.string.movida_a_papelera), Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        } else {
            Toast.makeText(this, getString(R.string.error_mover_papelera), Toast.LENGTH_SHORT).show()
        }
    }

    private fun moverABoveda() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.mover_a_boveda))
            .setMessage(getString(R.string.aviso_boveda))
            .setPositiveButton(getString(R.string.entendido)) { _, _ ->
                lifecycleScope.launch {
                    val manager = BovedaManager(this@VideoDetailActivity)
                    val movida = withContext(Dispatchers.IO) {
                        manager.moverABoveda(archivo)
                    }

                    if (movida) {
                        val uri = withContext(Dispatchers.IO) {
                            manager.obtenerUriOriginal(archivo)
                        }
                        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                            borrarOriginalLauncher.launch(
                                IntentSenderRequest.Builder(deleteRequest.intentSender).build()
                            )
                        } else {
                            Toast.makeText(this@VideoDetailActivity, getString(R.string.movida_a_boveda), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this@VideoDetailActivity, getString(R.string.error_mover), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancelar), null)
            .show()
    }

    private fun confirmarBorrarDeBoveda() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.borrar_definitivamente))
            .setMessage(getString(R.string.confirmar_borrar_boveda))
            .setPositiveButton(getString(R.string.si)) { _, _ ->
                if (archivoCifrado != null && archivoCifrado!!.delete()) {
                    Toast.makeText(this, getString(R.string.foto_borrada), Toast.LENGTH_SHORT).show()
                    finish()
                    overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
                } else {
                    Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun compartirVideo() {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "io.lucassogarayda.fstly.gallery.fileprovider",
                archivo
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.compartir)))
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarBorrar() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.borrar_definitivamente))
            .setMessage(getString(R.string.confirmar_borrar_definitivo))
            .setPositiveButton(getString(R.string.si)) { _, _ -> borrarVideo() }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun borrarVideo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val cursor = contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.DATA} = ?",
                    arrayOf(archivo.absolutePath),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        val contentUri = android.content.ContentUris.withAppendedId(collection, id)
                        val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(contentUri))
                        borrarLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
                    } else {
                        Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                if (archivo.delete()) {
                    Toast.makeText(this, getString(R.string.foto_borrada), Toast.LENGTH_SHORT).show()
                    finish()
                    overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
                } else {
                    Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        if (esBoveda && archivo.exists()) {
            archivo.delete()
        }
    }
}
