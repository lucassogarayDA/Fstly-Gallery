package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PapeleraActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var papeleraManager: PapeleraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_papelera)

        FuentesManager(this).aplicarFuenteATodo(window.decorView)

        papeleraManager = PapeleraManager(this)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val vaciarButton: ImageButton = findViewById(R.id.vaciarButton)

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        vaciarButton.setOnClickListener {
            vaciarPapelera()
        }

        papeleraManager.limpiarViejos()
        mostrarContenido()
    }

    private fun mostrarContenido() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        val archivos = papeleraManager.obtenerArchivosPapelera()

        if (archivos.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = PapeleraAdapter(archivos)
        }
    }

    private fun vaciarPapelera() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.vaciar_papelera))
            .setMessage(getString(R.string.confirmar_vaciar_papelera))
            .setPositiveButton(getString(R.string.si)) { _, _ ->
                val cantidad = papeleraManager.vaciarPapelera()
                if (cantidad > 0) {
                    Toast.makeText(this, getString(R.string.papelera_vaciada, cantidad), Toast.LENGTH_SHORT).show()
                }
                mostrarContenido()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    inner class PapeleraAdapter(private val archivos: List<File>) :
        RecyclerView.Adapter<PapeleraAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val archivo = archivos[position]
            val esVideo = papeleraManager.esVideo(archivo)

            if (esVideo) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(archivo.absolutePath)
                    val bmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    if (bmp != null) holder.imageView.setImageBitmap(bmp)
                } catch (e: Exception) {
                }
            } else {
                val bmp = BitmapFactory.decodeFile(archivo.absolutePath)
                if (bmp != null) holder.imageView.setImageBitmap(bmp)
            }

            holder.imageView.setOnClickListener {
                mostrarOpciones(archivo)
            }
        }

        override fun getItemCount(): Int = archivos.size
    }

    private fun mostrarOpciones(archivo: File) {
        val dias = papeleraManager.diasRestantes(archivo)
        val nombre = papeleraManager.nombreOriginal(archivo)

        val opciones = arrayOf(
            getString(R.string.restaurar),
            getString(R.string.borrar_definitivamente),
            getString(R.string.cancelar)
        )

        AlertDialog.Builder(this)
            .setTitle(nombre)
            .setMessage(getString(R.string.dias_restantes, dias))
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> restaurar(archivo)
                    1 -> borrarDefinitivo(archivo)
                }
            }
            .show()
    }

    private fun restaurar(archivo: File) {
        val destino = papeleraManager.rutaOriginal(archivo)
        if (papeleraManager.restaurarDePapelera(archivo, destino)) {
            Toast.makeText(this, getString(R.string.archivo_restaurado), Toast.LENGTH_SHORT).show()
            mostrarContenido()
        } else {
            Toast.makeText(this, getString(R.string.error_restaurar), Toast.LENGTH_SHORT).show()
        }
    }

    private fun borrarDefinitivo(archivo: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.borrar_definitivamente))
            .setMessage(getString(R.string.confirmar_borrar_definitivo))
            .setPositiveButton(getString(R.string.si)) { _, _ ->
                if (papeleraManager.borrarDePapelera(archivo)) {
                    Toast.makeText(this, getString(R.string.foto_borrada), Toast.LENGTH_SHORT).show()
                    mostrarContenido()
                } else {
                    Toast.makeText(this, getString(R.string.error_borrar), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }
}
