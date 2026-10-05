package io.lucassogarayda.fstly.gallery

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FolderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder)

        val nombre = intent.getStringExtra("carpeta_nombre") ?: "Carpeta"
        val rutas = intent.getStringArrayListExtra("rutas") ?: arrayListOf()
        val fotos = rutas.map { File(it) }

        val backButton: ImageButton = findViewById(R.id.backButton)
        val titleText: TextView = findViewById(R.id.titleText)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)

        titleText.text = nombre
        FuentesManager(this).aplicarFuente(titleText)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.setItemViewCacheSize(20)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 30)
        recyclerView.adapter = ImageAdapter(fotos.toMutableList())

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
        }
    }
}
