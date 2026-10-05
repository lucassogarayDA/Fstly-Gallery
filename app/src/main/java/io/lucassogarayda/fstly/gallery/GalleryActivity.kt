package io.lucassogarayda.fstly.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var fab: FloatingActionButton
    private lateinit var fabView: FloatingActionButton
    private lateinit var fabSearch: FloatingActionButton
    private lateinit var searchView: SearchView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView

    private val fotosTodas = mutableListOf<File>()
    private val carpetasTodas = mutableListOf<Carpeta>()
    private val seleccionados = mutableSetOf<String>()

    private var adapterFotos: ImageAdapter? = null
    private var adapterCarpetas: FolderAdapter? = null

    private var modoCarpetas = false
    private var busquedaActiva = false
    private var modoSeleccion = false
    private var tiempoEnBackground: Long = 0L

    private val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        FuentesManager(this).aplicarFuenteATodo(window.decorView)

        val prefs = getSharedPreferences("fstly_prefs", Context.MODE_PRIVATE)
        modoCarpetas = prefs.getBoolean("modo_carpetas", false)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        loadingBar = findViewById(R.id.loadingBar)
        fab = findViewById(R.id.fab)
        fabView = findViewById(R.id.fabView)
        fabSearch = findViewById(R.id.fabSearch)
        searchView = findViewById(R.id.searchView)
        selectionBar = findViewById(R.id.selectionBar)
        selectionCount = findViewById(R.id.selectionCount)

        searchView.isIconifiedByDefault = false
        searchView.setOnCloseListener {
            cerrarBusqueda()
            true
        }

        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.setItemViewCacheSize(20)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 30)

        configurarLayout()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (busquedaActiva || modoSeleccion) return
                if (dy > 0) {
                    fab.hide(); fabView.hide(); fabSearch.hide()
                } else if (dy < 0) {
                    fab.show(); fabView.show(); fabSearch.show()
                }
            }
        })

        fab.setOnClickListener {
            val popup = PopupMenu(this, fab)
            popup.menu.add(0, 1, 0, getString(R.string.favoritos))
            popup.menu.add(0, 2, 1, getString(R.string.boveda))
            popup.menu.add(0, 3, 2, getString(R.string.ajustes))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        startActivity(Intent(this, FavoritosActivity::class.java))
                        overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
                        true
                    }
                    2 -> {
                        startActivity(Intent(this, BovedaActivity::class.java))
                        overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
                        true
                    }
                    3 -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        overridePendingTransition(R.anim.fstly_fade_in, R.anim.fstly_fade_out)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        fabView.setOnClickListener {
            if (busquedaActiva) cerrarBusqueda()
            if (modoSeleccion) salirModoSeleccion()
            modoCarpetas = !modoCarpetas
            prefs.edit().putBoolean("modo_carpetas", modoCarpetas).apply()
            configurarLayout()
            aplicarFiltro()
        }

        fabSearch.setOnClickListener {
            if (busquedaActiva) {
                cerrarBusqueda()
            } else {
                abrirBusqueda()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                aplicarFiltro()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                aplicarFiltro()
                return true
            }
        })

        findViewById<ImageButton>(R.id.selectionClose).setOnClickListener {
            salirModoSeleccion()
        }

        findViewById<ImageButton>(R.id.selectionFavorite).setOnClickListener {
            marcarFavoritos()
        }

        findViewById<ImageButton>(R.id.selectionShare).setOnClickListener {
            compartirSeleccionados()
        }

        findViewById<ImageButton>(R.id.selectionDelete).setOnClickListener {
            confirmarBorrarSeleccionados()
        }

        if (tienePermisos()) {
            cargarFotos()
        } else {
            ActivityCompat.requestPermissions(this, permisos, 100)
        }
    }

    override fun onResume() {
        super.onResume()
        tiempoEnBackground = 0L
        FuentesManager(this).aplicarFuenteATodo(window.decorView)
        if (tienePermisos()) cargarFotos()
    }

    private fun abrirBusqueda() {
        busquedaActiva = true
        searchView.visibility = View.VISIBLE
        fabView.hide()
        fabSearch.setImageResource(R.drawable.ic_arrow_back)
        searchView.requestFocus()
    }

    private fun cerrarBusqueda() {
        busquedaActiva = false
        searchView.setQuery("", false)
        searchView.visibility = View.GONE
        fabView.show()
        fabSearch.show()
        fabSearch.setImageResource(R.drawable.ic_search)
        aplicarFiltro()
    }

    private fun entrarModoSeleccion() {
        if (modoSeleccion) return
        modoSeleccion = true
        selectionBar.visibility = View.VISIBLE
        fab.hide()
        fabView.hide()
        fabSearch.hide()
        actualizarContadorSeleccion()
        adapterFotos?.setModoSeleccion(true)
    }

    private fun salirModoSeleccion() {
        modoSeleccion = false
        seleccionados.clear()
        selectionBar.visibility = View.GONE
        fab.show()
        fabView.show()
        fabSearch.show()
        adapterFotos?.setModoSeleccion(false)
        actualizarContadorSeleccion()
    }

    private fun actualizarContadorSeleccion() {
        selectionCount.text = getString(R.string.seleccionados, seleccionados.size)
    }

    private fun marcarFavoritos() {
        val manager = FavoritosManager(this)
        val cantidad = seleccionados.size
        seleccionados.forEach { ruta ->
            if (!manager.esFavorito(ruta)) {
                manager.toggleFavorito(ruta)
            }
        }
        Toast.makeText(this, getString(R.string.favoritos_marcados, cantidad), Toast.LENGTH_SHORT).show()
        salirModoSeleccion()
    }

    private fun compartirSeleccionados() {
        try {
            val uris = ArrayList<Uri>()
            seleccionados.forEach { ruta ->
                val uri = FileProvider.getUriForFile(
                    this,
                    "io.lucassogarayda.fstly.gallery.fileprovider",
                    File(ruta)
                )
                uris.add(uri)
            }

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.compartir)))
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarBorrarSeleccionados() {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.borrar))
            .setMessage(getString(R.string.confirmar_borrar_multiples, seleccionados.size))
            .setPositiveButton(getString(R.string.si)) { _, _ -> moverSeleccionadosAPapelera() }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun moverSeleccionadosAPapelera() {
        val papeleraManager = PapeleraManager(this)
        var movidos = 0

        seleccionados.forEach { ruta ->
            if (papeleraManager.moverAPapelera(File(ruta))) movidos++
        }

        val mensaje = if (movidos == 1) {
            getString(R.string.movida_a_papelera)
        } else {
            getString(R.string.fotos_borradas, movidos)
        }
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        salirModoSeleccion()
        cargarFotos()
    }

    private fun esVideo(archivo: File): Boolean {
        val ext = archivo.extension.lowercase()
        return ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
    }

    private fun configurarLayout() {
        if (modoCarpetas) {
            recyclerView.layoutManager = GridLayoutManager(this, 2)
            fabView.setImageResource(R.drawable.ic_photo_library)
        } else {
            val lm = GridLayoutManager(this, 3)
            lm.initialPrefetchItemCount = 9
            recyclerView.layoutManager = lm
            fabView.setImageResource(R.drawable.ic_folder)
        }
    }

    override fun onStop() {
        super.onStop()
        tiempoEnBackground = System.currentTimeMillis()
    }

    override fun onRestart() {
        super.onRestart()
        val tiempoFuera = System.currentTimeMillis() - tiempoEnBackground
        if (tiempoFuera > 5 * 60 * 1000) imageLoader.memoryCache?.clear()
    }

    private fun tienePermisos(): Boolean {
        return permisos.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()
            && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            cargarFotos()
        } else {
            emptyText.visibility = TextView.VISIBLE
        }
    }

    private fun cargarFotos() {
        loadingBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                val listaFotos = mutableListOf<File>()
                val mapaCarpetas = mutableMapOf<String, MutableList<File>>()
                val boveda = BovedaManager(this@GalleryActivity)

                val projection = arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME                )

                val cursorImg = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )
                cursorImg?.use {
                    val colData = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val colBucket = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    while (it.moveToNext()) {
                        val path = it.getString(colData) ?: continue
                        val file = File(path)
                        if (boveda.esBoveda(file)) continue
                        val bucket = it.getString(colBucket) ?: "Otras"
                        listaFotos.add(file)
                        mapaCarpetas.getOrPut(bucket) { mutableListOf() }.add(file)
                    }
                }

                val cursorVid = contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
                )
                cursorVid?.use {
                    val colData = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val colBucket = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    while (it.moveToNext()) {
                        val path = it.getString(colData) ?: continue
                        val file = File(path)
                        if (boveda.esBoveda(file)) continue
                        val bucket = it.getString(colBucket) ?: "Otras"
                        listaFotos.add(file)
                        mapaCarpetas.getOrPut(bucket) { mutableListOf() }.add(file)
                    }
                }

                Pair(listaFotos, mapaCarpetas)
            }

            fotosTodas.clear()
            fotosTodas.addAll(resultado.first)

            carpetasTodas.clear()
            resultado.second.forEach { (nombre, lista) ->
                carpetasTodas.add(Carpeta(nombre, lista))
            }
            carpetasTodas.sortByDescending { it.fotos.size }

            loadingBar.visibility = View.GONE
            aplicarFiltro()
        }
    }

    private fun aplicarFiltro() {
        val query = if (busquedaActiva) searchView.query.toString().lowercase() else ""

        if (modoCarpetas) {
            val filtradas = if (query.isEmpty()) {
                carpetasTodas
            } else {
                carpetasTodas.filter { it.nombre.lowercase().contains(query) }
            }
            if (filtradas.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapterCarpetas = FolderAdapter(filtradas)
                recyclerView.adapter = adapterCarpetas
                animarGrilla()
            }
        } else {
            val filtradas = if (query.isEmpty()) {
                fotosTodas
            } else {
                fotosTodas.filter { it.name.lowercase().contains(query) }
            }
            if (filtradas.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapterFotos = ImageAdapter(
                    fotos = filtradas.toMutableList(),
                    modoSeleccion = modoSeleccion,
                    seleccionados = seleccionados,
                    onSeleccionCambiada = {
                        actualizarContadorSeleccion()
                        if (seleccionados.isEmpty()) {
                            salirModoSeleccion()
                        } else if (!modoSeleccion) {
                            entrarModoSeleccion()
                        }
                    }
                )
                recyclerView.adapter = adapterFotos
                animarGrilla()
            }
        }
    }

    private fun animarGrilla() {
        val anim = AnimationUtils.loadLayoutAnimation(this@GalleryActivity, R.anim.fstly_layout_fade_in)
        recyclerView.layoutAnimation = anim
        recyclerView.scheduleLayoutAnimation()
    }
}
