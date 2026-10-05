package io.lucassogarayda.fstly.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import coil.load
import java.io.File

class ImageDetailAdapter(
    private val fotos: List<File>,
    private val onInteraccion: () -> Unit,
    private val onDobleTap: () -> Unit
) : RecyclerView.Adapter<ImageDetailAdapter.DetailViewHolder>() {

    class DetailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoView: PhotoView = view.findViewById(R.id.photoView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_detail, parent, false)
        return DetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        val archivo = fotos[position]

        holder.photoView.maximumScale = 5f
        holder.photoView.mediumScale = 2f
        holder.photoView.minimumScale = 1f

        holder.photoView.setOnScaleChangeListener { _, _, _ ->
            onInteraccion()
        }

        holder.photoView.setOnMatrixChangeListener {
            onInteraccion()
        }

        holder.photoView.setOnViewTapListener { _, _, _ ->
            onDobleTap()
        }

        holder.photoView.load(archivo) {
            memoryCacheKey(archivo.absolutePath)
            crossfade(false)
        }
    }

    override fun getItemCount(): Int = fotos.size
}
