package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BovedaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var bovedaManager: BovedaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boveda)

        FuentesManager(this).aplicarFuenteATodo(window.decorView)

        bovedaManager = BovedaManager(this)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        val backButton: ImageButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        val prefs = getSharedPreferences("fstly_prefs", Context.MODE_PRIVATE)
        val pinGuardado = prefs.getString("pin", null)

        if (pinGuardado != null) {
            pedirPin(pinGuardado)
        } else {
            mostrarContenido()
        }
    }

    private fun pedirPin(pinCorrecto: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = "PIN"
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.boveda))
            .setMessage(getString(R.string.ingresa_pin_boveda))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.confirmar)) { _, _ ->
                val pin = input.text.toString()
                if (pin == pinCorrecto) {
                    mostrarContenido()
                } else {
                    Toast.makeText(this, getString(R.string.pin_incorrecto), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancelar)) { _, _ ->
                finish()
            }
            .show()
    }

    private fun mostrarContenido() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        val archivos = bovedaManager.obtenerFotosBoveda()

        if (archivos.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = BovedaAdapter(archivos)
        }
    }

    inner class BovedaAdapter(private val archivos: List<File>) :
        RecyclerView.Adapter<BovedaAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = if (viewType == 1) R.layout.item_video_boveda else R.layout.item_image
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return ViewHolder(view)
        }

        override fun getItemViewType(position: Int): Int {
            return if (bovedaManager.esVideo(archivos[position])) 1 else 0
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val archivo = archivos[position]
            val esVideo = bovedaManager.esVideo(archivo)

            if (esVideo) {
                GlobalScope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        bovedaManager.descifrarArchivo(archivo)
                    }
                    withContext(Dispatchers.Main) {
                        if (bytes != null && bytes.size > 0) {
                            try {
                                val temp = File(cacheDir, "thumb_${System.currentTimeMillis()}.mp4")
                                temp.outputStream().use { it.write(bytes) }
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(temp.absolutePath)
                                val bmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                retriever.release()
                                temp.delete()
                                if (bmp != null) holder.imageView.setImageBitmap(bmp)
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            } else {
                GlobalScope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        bovedaManager.descifrarArchivo(archivo)
                    }
                    withContext(Dispatchers.Main) {
                        if (bytes != null) {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            holder.imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            }

            holder.imageView.setOnClickListener {
                mostrarOpcionesBoveda(archivo, esVideo)
            }
        }

        override fun getItemCount(): Int = archivos.size
    }

    private fun mostrarOpcionesBoveda(archivo: File, esVideo: Boolean) {
        val opciones = if (esVideo) {
            arrayOf(
                getString(R.string.reproducir),
                getString(R.string.sacar_de_boveda),
                getString(R.string.borrar),
                getString(R.string.cancelar)
            )
        } else {
            arrayOf(
                getString(R.string.sacar_de_boveda),
                getString(R.string.borrar),
                getString(R.string.cancelar)
            )
        }

        AlertDialog.Builder(this)
            .setTitle(archivo.name.removeSuffix(".enc"))
            .setItems(opciones) { _, which ->
                if (esVideo) {
                    when (which) {
                        0 -> abrirVideo(archivo)
                        1 -> sacarDeBoveda(archivo)
                        2 -> borrarDeBoveda(archivo)
                    }
                } else {
                    when (which) {
                        0 -> sacarDeBoveda(archivo)
                        1 -> borrarDeBoveda(archivo)
                    }
                }
            }
            .show()
    }

    private fun abrirVideo(archivoCifrado: File) {
        GlobalScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                bovedaManager.descifrarArchivo(archivoCifrado)
            }
            withContext(Dispatchers.Main) {
                if (bytes != null) {
                    try {
                        val nombreOriginal = archivoCifrado.name.removeSuffix(".enc")
                        val temp = File(cacheDir, "temp_$nombreOriginal")
                        temp.outputStream().use { it.write(bytes) }

                        val intent = Intent(this@BovedaActivity, VideoDetailActivity::class.java)
                        intent.putStringArrayListExtra("rutas", arrayListOf(temp.absolutePath))
                        intent.putExtra("posicion", 0)
                        intent.putExtra("es_boveda", true)
                        intent.putExtra("archivo_cifrado", archivoCifrado.absolutePath)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@BovedaActivity, getString(R.string.error_sacar), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@BovedaActivity, getString(R.string.error_sacar), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sacarDeBoveda(archivoCifrado: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sacar_de_boveda))
            .setMessage(getString(R.string.confirmar_sacar_boveda))
            .setPositiveButton(getString(R.string.si)) { _, _ ->
                GlobalScope.launch {
                    val resultado = withContext(Dispatchers.IO) {
                        bovedaManager.descifrarAGuardar(archivoCifrado)
                    }
                    withContext(Dispatchers.Main) {
                        if (resultado != null) {
                            Toast.makeText(
                                this@BovedaActivity,
                                getString(R.string.sacada_de_boveda),
                                Toast.LENGTH_SHORT
                            ).show()
                            mostrarContenido()
                        } else {
                            Toast.makeText(
                                this@BovedaActivity,
                                getString(R.string.error_sacar),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun borrarDeBoveda(archivoCifrado: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.borrar_definitivamente))
            .setMessage(getString(R.string.confirmar_borrar_boveda))
            .setPositiveButton(getString(R.string.si)) { _, _ ->
                if (bovedaManager.borrarDeBoveda(archivoCifrado)) {
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
