package com.example.sftest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SimpleMapAdapter :
    ListAdapter<Map<String, Any>, SimpleMapAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Map<String, Any>>() {
            override fun areItemsTheSame(old: Map<String, Any>, new: Map<String, Any>): Boolean {
                return (old["Id"]?.toString() ?: "") == (new["Id"]?.toString() ?: "")
            }
            override fun areContentsTheSame(old: Map<String, Any>, new: Map<String, Any>) = old == new
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        // Forma simple: "Campo1: valor1 · Campo2: valor2"
        holder.tv.text = item.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
    }
}
