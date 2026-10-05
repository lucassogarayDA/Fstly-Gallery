package io.lucassogarayda.fstly.gallery

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var pinInput: EditText
    private lateinit var subtitleText: TextView
    private lateinit var confirmButton: Button
    private lateinit var warningText: TextView

    private var pinTemporal: String? = null
    private var modoCrear = false
    private var procesando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fuentesManager = FuentesManager(this)
        fuentesManager.aplicarFuenteATodo(window.decorView)

        prefs = getSharedPreferences("fstly_prefs", Context.MODE_PRIVATE)
        pinInput = findViewById(R.id.pinInput)
        subtitleText = findViewById(R.id.subtitleText)
        confirmButton = findViewById(R.id.confirmButton)
        warningText = findViewById(R.id.warningText)

        val pinGuardado = prefs.getString("pin", null)

        if (pinGuardado == null) {
            modoCrear = true
            subtitleText.text = getString(R.string.crea_pin)
            warningText.visibility = View.VISIBLE
        } else {
            modoCrear = false
            subtitleText.text = getString(R.string.ingresa_pin)
            warningText.visibility = View.GONE
        }

        pinInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 4 && !procesando) {
                    ocultarTeclado()
                    validarPin(s.toString())
                }
            }
        })

        confirmButton.setOnClickListener {
            val input = pinInput.text.toString()
            if (input.length == 4 && !procesando) {
                ocultarTeclado()
                validarPin(input)
            }
        }

        pinInput.requestFocus()
        Handler(Looper.getMainLooper()).postDelayed({
            mostrarTeclado()
        }, 100)
    }

    private fun mostrarTeclado() {
        val controller = WindowCompat.getInsetsController(window, pinInput)
        controller.show(WindowInsetsCompat.Type.ime())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun ocultarTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(pinInput.windowToken, 0)
        pinInput.clearFocus()
    }

    private fun validarPin(input: String) {
        procesando = true
        val pinGuardado = prefs.getString("pin", null)

        if (modoCrear) {
            if (pinTemporal == null) {
                pinTemporal = input
                subtitleText.text = getString(R.string.confirma_pin)
                subtitleText.setTextColor(ContextCompat.getColor(this, R.color.fstly_text))
                pinInput.text.clear()
                procesando = false
                Handler(Looper.getMainLooper()).postDelayed({
                    pinInput.requestFocus()
                    mostrarTeclado()
                }, 100)
            } else {
                if (pinTemporal == input) {
                    prefs.edit().putString("pin", input).apply()
                    irAGaleria()
                } else {
                    vibrar()
                    shake()
                    subtitleText.text = getString(R.string.pin_no_coincide)
                    subtitleText.setTextColor(Color.RED)
                    pinTemporal = null
                    pinInput.text.clear()
                    subtitleText.postDelayed({
                        subtitleText.text = getString(R.string.crea_pin)
                        subtitleText.setTextColor(ContextCompat.getColor(this, R.color.fstly_text))
                        procesando = false
                        pinInput.requestFocus()
                        mostrarTeclado()
                    }, 1500)
                }
            }
        } else {
            if (input == pinGuardado) {
                irAGaleria()
            } else {
                vibrar()
                shake()
                subtitleText.text = getString(R.string.pin_incorrecto)
                subtitleText.setTextColor(Color.RED)
                pinInput.text.clear()
                subtitleText.postDelayed({
                    subtitleText.text = getString(R.string.ingresa_pin)
                    subtitleText.setTextColor(ContextCompat.getColor(this, R.color.fstly_text))
                    procesando = false
                    pinInput.requestFocus()
                    mostrarTeclado()
                }, 1500)
            }
        }
    }

    private fun shake() {
        val anim = ObjectAnimator.ofFloat(pinInput, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 0f)
        anim.duration = 400
        anim.start()
    }

    private fun vibrar() {
        val vibrador: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrador.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrador.vibrate(200)
        }
    }

    private fun irAGaleria() {
        ocultarTeclado()
        startActivity(Intent(this, GalleryActivity::class.java))
        overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        finish()
    }
}
