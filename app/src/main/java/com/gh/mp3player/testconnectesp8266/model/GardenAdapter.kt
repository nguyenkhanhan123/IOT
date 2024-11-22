package com.gh.mp3player.testconnectesp8266.model

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TableRow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gh.mp3player.testconnectesp8266.R

class GardenAdapter(
    private val list: List<Garden>,
    private val context: Context,
    private val event: View.OnClickListener,
    private val event2: View.OnClickListener
) : RecyclerView.Adapter<GardenAdapter.GardenHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GardenHolder {
        val v: View = LayoutInflater.from(context).inflate(R.layout.item, parent, false)
        return GardenHolder(v)
    }

    override fun onBindViewHolder(holder: GardenHolder, position: Int) {
        val s: Garden = list[position]
        holder.tv.text = s.name
        holder.tr.tag = s.name
        holder.iv.tag = s.name
        if (s.status) {
            holder.iv2.setImageResource(R.drawable.greenwifi)
            holder.tr.setOnClickListener {
                    v ->
                event2.onClick(v)
            }
        }
        else{
            holder.iv2.setImageResource(R.drawable.wifi)
        }

        holder.iv.setOnClickListener { v ->
            event.onClick(v)
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class GardenHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tv: TextView = itemView.findViewById(R.id.text)
        var tr: TableRow = itemView.findViewById(R.id.garden)
        var iv: ImageView = itemView.findViewById(R.id.exit)
        var iv2: ImageView = itemView.findViewById(R.id.status)
    }
}
