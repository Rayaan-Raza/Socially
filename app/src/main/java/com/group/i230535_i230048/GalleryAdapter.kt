package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class GalleryAdapter(
    private val items: MutableList<Uri>,
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryVH>() {

    inner class GalleryVH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.img)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_thumb, parent, false)
        if (v.parent != null) {
            throw IllegalStateException("Inflated item view already has a parent: ${v.parent}")
        }
        return GalleryVH(v)
    }

    override fun onBindViewHolder(holder: GalleryVH, position: Int) {
        val uri = items[position]

        holder.thumb.setImageDrawable(null)

        holder.thumb.setImageURI(uri)

        holder.itemView.setOnClickListener {
            onClick(uri)
        }
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<Uri>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
