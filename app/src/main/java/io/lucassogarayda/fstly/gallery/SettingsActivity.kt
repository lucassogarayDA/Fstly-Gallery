package io.lucassogarayda.fstly.gallery

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        FuentesManager(this).aplicarFuenteATodo(window.decorView)

        val backButton: ImageButton = findViewById(R.id.backButton)
        val changePinButton: Button = findViewById(R.id.changePinButton)
        val fontsButton: Button = findViewById(R.id.fontsButton)
        val papeleraButton: Button = findViewById(R.id.papeleraButton)
        val versionText: TextView = findViewById(R.id.versionText)

        versionText.text = "Fstly Gallery ${BuildConfig.VERSION_NAME}"

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        fontsButton.setOnClickListener {
            startActivity(Intent(this, FuentesActivity::class.java))
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        papeleraButton.setOnClickListener {
            startActivity(Intent(this, PapeleraActivity::class.java))
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }

        changePinButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.cambiar_pin))
                .setMessage(getString(R.string.aviso_cambiar_pin))
                .setPositiveButton(getString(R.string.entendido)) { _, _ ->
                    val prefs = getSharedPreferences("fstly_prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("pin").apply()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton(getString(R.string.cancelar), null)
                .show()
        }
    }
}
