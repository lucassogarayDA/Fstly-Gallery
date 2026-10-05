package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class FuentesActivity : AppCompatActivity() {

    private lateinit var fuentesManager: FuentesManager
    private lateinit var listaSistema: LinearLayout
    private lateinit var listaPersonalizadas: LinearLayout
    private lateinit var sinFuentesText: TextView
    private lateinit var btnFuenteSistema: Button

    private val importarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                importarFuente(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuentes)

        fuentesManager = FuentesManager(this)

        listaSistema = findViewById(R.id.listaSistema)
        listaPersonalizadas = findViewById(R.id.listaPersonalizadas)
        sinFuentesText = findViewById(R.id.sinFuentesText)
        btnFuenteSistema = findViewById(R.id.btnFuenteSistema)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val titleText: TextView = findViewById(R.id.titleText)
        val importarButton: Button = findViewById(R.id.importarButton)

        FuentesManager(this).aplicarFuente(titleText)

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        btnFuenteSistema.setOnClickListener {
            fuentesManager.guardarFuente("sans-serif")
            mostrarFuentes()
            Toast.makeText(this, getString(R.string.fuente_aplicada), Toast.LENGTH_SHORT).show()
        }

        importarButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importarLauncher.launch(intent)
        }

        mostrarFuentes()
    }

    private fun mostrarFuentes() {
        listaSistema.removeAllViews()
        listaPersonalizadas.removeAllViews()

        val fuenteActual = fuentesManager.obtenerFuenteActual()

        FuentesManager.FUENTES_SISTEMA.forEach { nombre ->
            agregarItem(listaSistema, nombre, nombre, fuenteActual == nombre) {
                fuentesManager.guardarFuente(nombre)
                mostrarFuentes()
                Toast.makeText(this, getString(R.string.fuente_aplicada), Toast.LENGTH_SHORT).show()
            }
        }

        val personalizadas = fuentesManager.obtenerFuentesPersonalizadas()
        if (personalizadas.isEmpty()) {
            sinFuentesText.visibility = TextView.VISIBLE
        } else {
            sinFuentesText.visibility = TextView.GONE
            personalizadas.forEach { archivo ->
                agregarItem(
                    listaPersonalizadas,
                    archivo.nameWithoutExtension,
                    archivo.absolutePath,
                    fuenteActual == archivo.absolutePath,
                    onEliminar = {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.eliminar_fuente))
                            .setMessage(getString(R.string.confirmar_eliminar_fuente, archivo.name))
                            .setPositiveButton(getString(R.string.si)) { _, _ ->
                                if (fuentesManager.eliminarFuentePersonalizada(archivo)) {
                                    if (fuenteActual == archivo.absolutePath) {
                                        fuentesManager.guardarFuente("sans-serif")
                                    }
                                    mostrarFuentes()
                                }
                            }
                            .setNegativeButton(getString(R.string.no), null)
                            .show()
                    }
                ) {
                    fuentesManager.guardarFuente(archivo.absolutePath)
                    mostrarFuentes()
                    Toast.makeText(this, getString(R.string.fuente_aplicada), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun agregarItem(
        contenedor: LinearLayout,
        titulo: String,
        valor: String,
        seleccionado: Boolean,
        onEliminar: (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)
            isClickable = true
            isFocusable = true
        }

        val textView = TextView(this).apply {
            text = titulo
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@FuentesActivity, R.color.fstly_text))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            try {
                val typeface = fuentesManager.cargarTypeface(valor)
                this.typeface = typeface
            } catch (e: Exception) {
            }
        }

        if (seleccionado) {
            textView.setTextColor(ContextCompat.getColor(this@FuentesActivity, R.color.fstly_primary))
            layout.setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        layout.addView(textView)

        if (onEliminar != null) {
            val eliminar = TextView(this).apply {
                text = "✕"
                textSize = 18f
                setTextColor(ContextCompat.getColor(this@FuentesActivity, R.color.fstly_accent))
                setPadding(24, 0, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { onEliminar() }
            }
            layout.addView(eliminar)
        }

        layout.setOnClickListener { onClick() }
        contenedor.addView(layout)
    }

    private fun importarFuente(uri: Uri) {
        try {
            val nombre = obtenerNombreArchivo(uri) ?: "fuente_${System.currentTimeMillis()}.ttf"
            val temp = File(cacheDir, nombre)

            contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val resultado = fuentesManager.importarFuente(temp)
            temp.delete()

            if (resultado != null) {
                Toast.makeText(this, getString(R.string.fuente_importada), Toast.LENGTH_SHORT).show()
                mostrarFuentes()
            } else {
                Toast.makeText(this, getString(R.string.error_importar_fuente), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_importar_fuente), Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerNombreArchivo(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) it.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
