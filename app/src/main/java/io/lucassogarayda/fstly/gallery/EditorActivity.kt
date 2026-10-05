package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream

class EditorActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var archivo: File
    private var bitmapActual: Bitmap? = null
    private var rotacion = 0f
    private var volteadoH = false
    private var volteadoV = false
    private var huboCambios = false
    private var seRecorto = false

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            preguntarSalir()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        onBackPressedDispatcher.addCallback(this, backCallback)

        val ruta = intent.getStringExtra("ruta") ?: run {
            finish()
            return
        }
        archivo = File(ruta)

        imageView = findViewById(R.id.imageView)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val saveButton: ImageButton = findViewById(R.id.saveButton)
        val rotateButton: ImageButton = findViewById(R.id.rotateButton)
        val flipButton: ImageButton = findViewById(R.id.flipButton)
        val cropButton: ImageButton = findViewById(R.id.cropButton)

        cargarBitmap()

        backButton.setOnClickListener {
            preguntarSalir()
        }

        rotateButton.setOnClickListener {
            rotacion = (rotacion + 90f) % 360f
            huboCambios = true
            aplicarTransformaciones()
        }

        flipButton.setOnClickListener {
            volteadoH = !volteadoH
            huboCambios = true
            aplicarTransformaciones()
        }

        cropButton.setOnClickListener {
            abrirUCrop()
        }

        saveButton.setOnClickListener {
            preguntarGuardar()
        }
    }

    private fun cargarBitmap() {
        val opciones = BitmapFactory.Options().apply {
            inSampleSize = 2
        }
        bitmapActual = BitmapFactory.decodeFile(archivo.absolutePath, opciones)
        imageView.setImageBitmap(bitmapActual)
    }

    private fun aplicarTransformaciones() {
        val original = bitmapActual ?: return
        val matrix = Matrix()
        matrix.postRotate(rotacion)
        if (volteadoH) matrix.postScale(-1f, 1f)
        if (volteadoV) matrix.postScale(1f, -1f)
        val transformado = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        imageView.setImageBitmap(transformado)
    }

    private fun preguntarSalir() {
        if (huboCambios || seRecorto) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.salir_sin_guardar))
                .setMessage(getString(R.string.mensaje_salir))
                .setPositiveButton(getString(R.string.salir)) { _, _ ->
                    backCallback.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                .setNegativeButton(getString(R.string.quedarme), null)
                .show()
        } else {
            backCallback.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun abrirUCrop() {
        try {
            val uriOrigen = FileProvider.getUriForFile(
                this,
                "io.lucassogarayda.fstly.gallery.fileprovider",
                archivo
            )
            val destino = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
            val uriDestino = Uri.fromFile(destino)

            val opciones = UCrop.Options().apply {
                setCompressionQuality(90)
                setToolbarColor(getColor(R.color.fstly_bg_deep))
                setStatusBarColor(getColor(R.color.fstly_bg_deep))
                setToolbarWidgetColor(getColor(R.color.fstly_primary))
            }

            val intent = UCrop.of(uriOrigen, uriDestino)
                .withOptions(opciones)
                .getIntent(this)

            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val resultado = UCrop.getOutput(data!!)
            if (resultado != null) {
                val bitmap = BitmapFactory.decodeFile(resultado.path)
                bitmapActual = bitmap
                rotacion = 0f
                volteadoH = false
                volteadoV = false
                seRecorto = true
                huboCambios = true
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun preguntarGuardar() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.guardar))
            .setMessage(getString(R.string.pregunta_guardar))
            .setPositiveButton(getString(R.string.sobrescribir)) { _, _ ->
                guardarFoto(true)
            }
            .setNegativeButton(getString(R.string.guardar_copia)) { _, _ ->
                guardarFoto(false)
            }
            .setNeutralButton(getString(R.string.cancelar), null)
            .show()
    }

    private fun guardarFoto(sobrescribir: Boolean) {
        try {
            val original = bitmapActual ?: return
            val matrix = Matrix()
            matrix.postRotate(rotacion)
            if (volteadoH) matrix.postScale(-1f, 1f)
            if (volteadoV) matrix.postScale(1f, -1f)
            val final = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

            val destino = if (sobrescribir) {
                archivo
            } else {
                File(archivo.parent, "edited_${System.currentTimeMillis()}_${archivo.name}")
            }

            FileOutputStream(destino).use { out ->
                final.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            Toast.makeText(this, getString(R.string.foto_guardada), Toast.LENGTH_SHORT).show()
            backCallback.isEnabled = false
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
