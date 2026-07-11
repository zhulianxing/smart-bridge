package com.smartbridge.tunnel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ServerAdapter(
    private val context: Context,
    private val servers: List<ServerInfo>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<ServerAdapter.VH>() {

    private var selected = 0

    fun getSelected(): ServerInfo = servers[selected]

    fun setSelected(pos: Int) {
        val old = selected
        selected = pos
        notifyItemChanged(old)
        notifyItemChanged(selected)
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v as MaterialCardView
        val flag: TextView = v.findViewById(R.id.tvFlag)
        val city: TextView = v.findViewById(R.id.tvCity)
        val host: TextView = v.findViewById(R.id.tvHost)
        val check: ImageView = v.findViewById(R.id.ivCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_server, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = servers[position]
        holder.flag.text = s.flag
        holder.city.text = s.city
        holder.host.text = "${s.host}:${s.port}"

        if (position == selected) {
            holder.card.strokeColor = context.getColor(R.color.card_stroke_selected)
            holder.card.strokeWidth = 3
            holder.check.visibility = View.VISIBLE
        } else {
            holder.card.strokeColor = context.getColor(R.color.card_stroke_default)
            holder.card.strokeWidth = 1
            holder.check.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            setSelected(position)
            onSelect(position)
        }
    }

    override fun getItemCount() = servers.size
}
