package com.example.sftest.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import data.local.ScanItem
import com.example.sftest.R

/**
 * Adapter para mostrar la lista de ScanItem.
 * item_scan.xml debe contener:
 *  - TextView @id/tvCode
 *  - TextView @id/tvQty
 *  - ImageButton @id/btnDelete
 *
 * El constructor recibe un callback (ScanItem) -> Unit para borrar el item.
 */
class ScansAdapter(
    private val onDelete: (ScanItem) -> Unit
) : ListAdapter<ScanItem, ScansAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scan, parent, false)
        return ViewHolder(view, onDelete)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View, private val onDelete: (ScanItem) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val tvCode: TextView = itemView.findViewById(R.id.tvCode)
        private val tvQty: TextView = itemView.findViewById(R.id.tvQty)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(item: ScanItem) {
            tvCode.text = item.code
            tvQty.text = "x${item.quantity}"
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ScanItem>() {
        override fun areItemsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
            return oldItem == newItem
        }
    }
}
