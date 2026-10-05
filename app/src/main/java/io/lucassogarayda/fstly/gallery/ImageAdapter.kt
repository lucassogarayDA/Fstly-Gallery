package io.lucassogarayda.fstly.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File

class ImageAdapter(
    private var fotos: MutableList<File>,
    private var modoSeleccion: Boolean = false,
    private val seleccionados: MutableSet<String> = mutableSetOf(),
    private val onSeleccionCambiada: (() -> Unit)? = null
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val heartIcon: ImageView = view.findViewById(R.id.heartIcon)
        val checkIcon: ImageView = view.findViewById(R.id.checkIcon)
        val selectionOverlay: View = view.findViewById(R.id.selectionOverlay)
        val videoIcon: ImageView = view.findViewById(R.id.videoIcon)
        val videoDuration: TextView = view.findViewById(R.id.videoDuration)
    }

    fun setModoSeleccion(activo: Boolean) {
        if (modoSeleccion == activo) return
        modoSeleccion = activo
        notifyItemRangeChanged(0, itemCount)
    }

    fun actualizarFotos(nuevas: MutableList<File>) {
        fotos = nuevas
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val archivo = fotos[position]
        val context = holder.itemView.context
        val manager = FavoritosManager(context)

        val esVideo = esVideo(archivo)

        if (esVideo) {
            val thumbnail = obtenerMiniatura(archivo)
            if (thumbnail != null) {
                holder.imageView.setImageBitmap(thumbnail)
            } else {
                holder.imageView.load(archivo) {
                    crossfade(true)
                    size(200, 200)
                }
            }
        } else {
            holder.imageView.load(archivo) {
                crossfade(true)
                memoryCacheKey(archivo.absolutePath)
                size(200, 200)
                precision(coil.size.Precision.INEXACT)
            }
        }

        if (esVideo) {
            holder.videoIcon.visibility = View.VISIBLE
            val duracion = obtenerDuracion(archivo)
            if (duracion != null) {
                holder.videoDuration.text = duracion
                holder.videoDuration.visibility = View.VISIBLE
            } else {
                holder.videoDuration.visibility = View.GONE
            }
        } else {
            holder.videoIcon.visibility = View.GONE
            holder.videoDuration.visibility = View.GONE
        }

        if (modoSeleccion) {
            holder.heartIcon.visibility = View.GONE
            holder.checkIcon.visibility = View.VISIBLE
            val seleccionada = seleccionados.contains(archivo.absolutePath)
            holder.selectionOverlay.visibility = if (seleccionada) View.VISIBLE else View.GONE
            holder.checkIcon.alpha = if (seleccionada) 1f else 0.4f
        } else {
            holder.checkIcon.visibility = View.GONE
            holder.selectionOverlay.visibility = View.GONE
            holder.heartIcon.visibility = if (manager.esFavorito(archivo.absolutePath)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        holder.imageView.setOnClickListener {
            if (modoSeleccion) {
                val path = archivo.absolutePath
                if (seleccionados.contains(path)) {
                    seleccionados.remove(path)
                } else {
                    seleccionados.add(path)
                }
                notifyItemChanged(position)
                onSeleccionCambiada?.invoke()
            } else {
                val intent = if (esVideo) {
                    Intent(context, VideoDetailActivity::class.java)
                } else {
                    Intent(context, ImageDetailActivity::class.java)
                }
                intent.putStringArrayListExtra("rutas", ArrayList(fotos.map { it.absolutePath }))
                intent.putExtra("posicion", position)
                context.startActivity(intent)
            }
        }

        holder.imageView.setOnLongClickListener {
            if (!modoSeleccion) {
                seleccionados.add(archivo.absolutePath)
                notifyItemChanged(position)
                onSeleccionCambiada?.invoke()
                true
            } else {
                false
            }
        }
    }

    private fun esVideo(archivo: File): Boolean {
        val ext = archivo.extension.lowercase()
        return ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
    }

    private fun obtenerMiniatura(archivo: File): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(archivo.absolutePath)
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            if (bitmap != null) {
                val ancho = 200
                val alto = (bitmap.height * (200f / bitmap.width)).toInt()
                Bitmap.createScaledBitmap(bitmap, ancho, alto, true)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun obtenerDuracion(archivo: File): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(archivo.absolutePath)
            val duracionMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return null
            retriever.release()

            val segundos = duracionMs / 1000
            val minutos = segundos / 60
            val segs = segundos % 60
            String.format("%d:%02d", minutos, segs)
        } catch (e: Exception) {
            null
        }
    }

    override fun getItemCount(): Int = fotos.size
}
