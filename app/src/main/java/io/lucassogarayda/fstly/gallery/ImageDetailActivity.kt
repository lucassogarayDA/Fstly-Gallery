package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageDetailActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var topBar: View
    private lateinit var positionText: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var favoriteButton: ImageButton
    private var barraVisible = true
    private var interaccionManual = false
    private lateinit var fotos: List<File>
    private lateinit var favoritosManager: FavoritosManager
    private lateinit var bovedaManager: BovedaManager
    private lateinit var papeleraManager: PapeleraManager

    private val handler = Handler(Looper.getMainLooper())
    private val mostrarRunnable = Runnable { mostrarBarra() }

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
        setContentView(R.layout.activity_image_detail)

        FuentesManager(this).aplicarFuenteATodo(window.decorView)

        favoritosManager = FavoritosManager(this)
        bovedaManager = BovedaManager(this)
        papeleraManager = PapeleraManager(this)

        val rutas = intent.getStringArrayListExtra("rutas") ?: arrayListOf()
        val posicionInicial = intent.getIntExtra("posicion", 0)
        fotos = rutas.map { File(it) }

        viewPager = findViewById(R.id.viewPager)
        topBar = findViewById(R.id.topBar)
        positionText = findViewById(R.id.positionText)
        menuButton = findViewById(R.id.menuButton)
        favoriteButton = findViewById(R.id.favoriteButton)
        val closeButton: ImageButton = findViewById(R.id.closeButton)

        val adapter = ImageDetailAdapter(
            fotos,
            onInteraccion = {
                interaccionManual = true
                ocultarBarra()
                reiniciarTimer()
            },
            onDobleTap = {
                if (interaccionManual) {
                    interaccionManual = false
                    mostrarBarra()
                } else {
                    interaccionManual = true
                    ocultarBarra()
                }
                reiniciarTimer()
            }
        )
        viewPager.adapter = adapter
        viewPager.setCurrentItem(posicionInicial, false)

        actualizarContador(posicionInicial, fotos.size)
        actualizarEstrella(posicionInicial)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                actualizarContador(position, fotos.size)
                actualizarEstrella(position)
                interaccionManual = false
                mostrarBarra()
                reiniciarTimer()
            }
        })

        closeButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        favoriteButton.setOnClickListener {
            val foto = fotos[viewPager.currentItem]
            favoritosManager.toggleFavorito(foto.absolutePath)
            actualizarEstrella(viewPager.currentItem)
            reiniciarTimer()
        }

        menuButton.setOnClickListener {
            mostrarMenu()
        }
    }

    private fun actualizarEstrella(posicion: Int) {
        val foto = fotos[posicion]
        val esFav = favoritosManager.esFavorito(foto.absolutePath)
        favoriteButton.setImageResource(
            if (esFav) R.drawable.ic_star_filled else R.drawable.ic_star_empty
        )
    }

    private fun mostrarMenu() {
        val popup = PopupMenu(this, menuButton)
        popup.menu.add(0, 1, 0, getString(R.string.info))
        popup.menu.add(0, 2, 1, getString(R.string.compartir))
        popup.menu.add(0, 3, 2, getString(R.string.editar))
        popup.menu.add(0, 4, 3, getString(R.string.mover_a_boveda))
        popup.menu.add(0, 5, 4, getString(R.string.borrar))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { mostrarInfo(); true }
                2 -> { compartirFoto(); true }
                3 -> { editarFoto(); true }
                4 -> { moverABoveda(); true }
                5 -> { preguntarBorrar(); true }
                else -> false
            }
        }
        popup.show()
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
        val foto = fotos[viewPager.currentItem]
        if (papeleraManager.moverAPapelera(foto)) {
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
                val foto = fotos[viewPager.currentItem]

                lifecycleScope.launch {
                    val movida = withContext(Dispatchers.IO) {
                        bovedaManager.moverABoveda(foto)
                    }

                    if (movida) {
                        val uri = withContext(Dispatchers.IO) {
                            bovedaManager.obtenerUriOriginal(foto)
                        }
                        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                            borrarOriginalLauncher.launch(
                                IntentSenderRequest.Builder(deleteRequest.intentSender).build()
                            )
                        } else {
                            Toast.makeText(this@ImageDetailActivity, getString(R.string.movida_a_boveda), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this@ImageDetailActivity, getString(R.string.error_mover), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancelar), null)
            .show()
    }

    private fun editarFoto() {
        val foto = fotos[viewPager.currentItem]
        val intent = Intent(this, EditorActivity::class.java)
        intent.putExtra("ruta", foto.absolutePath)
        startActivity(intent)
        overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
    }

    private fun mostrarInfo() {
        val pos = viewPager.currentItem
        val foto = fotos[pos]

        val tamaño = foto.length() / 1024
        val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(foto.lastModified()))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.info))
            .setMessage("Nombre: ${foto.name}\nTamaño: ${tamaño} KB\nFecha: $fecha\nRuta: ${foto.absolutePath}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun compartirFoto() {
        val pos = viewPager.currentItem
        val foto = fotos[pos]

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "io.lucassogarayda.fstly.gallery.fileprovider",
                foto
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
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
            .setPositiveButton(getString(R.string.si)) { _, _ -> borrarFoto() }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun borrarFoto() {
        val pos = viewPager.currentItem
        val foto = fotos[pos]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val cursor = contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.DATA} = ?",
                    arrayOf(foto.absolutePath),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
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
                if (foto.delete()) {
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(mostrarRunnable)
    }

    private fun reiniciarTimer() {
        handler.removeCallbacks(mostrarRunnable)
        handler.postDelayed(mostrarRunnable, 3000)
    }

    private fun ocultarBarra() {
        if (!barraVisible) return
        barraVisible = false
        topBar.animate().alpha(0f).setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { topBar.visibility = View.GONE }.start()
    }

    private fun mostrarBarra() {
        if (barraVisible) return
        barraVisible = true
        topBar.visibility = View.VISIBLE
        topBar.alpha = 0f
        topBar.animate().alpha(1f).setDuration(250)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun actualizarContador(posicion: Int, total: Int) {
        positionText.text = "${posicion + 1} / $total"
    }
}
