package com.veysel.tvcaster

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Bulunan videolar / kayitli listeler icin basit liste adaptoru.
 * Satira dokunma = onClick, uzun basma = onLongClick.
 */
class VideoAdapter(
    private val items: Map<String, String>,
    private val onClick: (String, String) -> Unit,
    private val onLongClick: (String, String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.titleTv)
        val url: TextView = v.findViewById(R.id.urlTv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items.entries.toList()[position]
        holder.title.text = entry.value
        holder.url.text = entry.key
        holder.itemView.setOnClickListener { onClick(entry.key, entry.value) }
        holder.itemView.setOnLongClickListener {
            onLongClick(entry.key, entry.value)
            true
        }
    }
}
