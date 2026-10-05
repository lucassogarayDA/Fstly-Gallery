package io.lucassogarayda.fstly.gallery

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File

data class Carpeta(
    val nombre: String,
    val fotos: List<File>
)

class FolderAdapter(private val carpetas: List<Carpeta>) :
    RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.folderCover)
        val nombre: TextView = view.findViewById(R.id.folderName)
        val contador: TextView = view.findViewById(R.id.folderCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val carpeta = carpetas[position]
        val context = holder.itemView.context

        holder.nombre.text = carpeta.nombre
        holder.contador.text = context.getString(R.string.elementos, carpeta.fotos.size)

        val portada = carpeta.fotos.firstOrNull()
        if (portada != null) {
            holder.cover.load(portada) {
                crossfade(true)
                memoryCacheKey(portada.absolutePath)
                size(300, 300)
                precision(coil.size.Precision.INEXACT)
            }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, FolderActivity::class.java)
            intent.putExtra("carpeta_nombre", carpeta.nombre)
            intent.putStringArrayListExtra("rutas", ArrayList(carpeta.fotos.map { it.absolutePath }))
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = carpetas.size
}
