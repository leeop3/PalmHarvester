package com.palm.harvester.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.palm.harvester.R
import com.palm.harvester.data.*
import kotlinx.coroutines.launch

class RecordsFragment : Fragment(R.layout.fragment_records) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvRecords)
        val adapter = RecordAdapter { entry -> confirmDelete(entry) }
        rv?.layoutManager = LinearLayoutManager(requireContext())
        rv?.adapter = adapter

        AppDatabase.getInstance(requireContext()).harvestDao().getAllEntries().observe(viewLifecycleOwner) { data ->
            data?.let { adapter.submitList(it) }
        }
    }

    private fun confirmDelete(entry: HarvestEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Record?")
            .setMessage("This will remove the entry from your logs.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { AppDatabase.getInstance(requireContext()).harvestDao().delete(entry) }
            }
            .setNegativeButton("Cancel", null).show()
    }
}

class RecordAdapter(val onDeleteClick: (HarvestEntry) -> Unit) : ListAdapter<HarvestEntry, RecordAdapter.VH>(Diff()) {
    
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val block: TextView = v.findViewById(R.id.recBlock)
        val stats: TextView = v.findViewById(R.id.recStats)
        val gps: TextView = v.findViewById(R.id.recGps)
        val img: ImageView = v.findViewById(R.id.recImg)
        val btnDel: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.block.text = "Block: ${item.blockId}"
        holder.stats.text = "Ripe: ${item.ripeCount} | Empty: ${item.emptyCount} | Total: ${item.ripeCount + item.emptyCount}"
        holder.gps.text = "GPS: ${String.format("%.4f", item.latitude)}, ${String.format("%.4f", item.longitude)} | ${item.timestamp}"
        
        if (item.photoBase64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(item.photoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.img.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.img.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.img.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        
        holder.btnDel.setOnClickListener { onDeleteClick(item) }
    }

    class Diff : DiffUtil.ItemCallback<HarvestEntry>() {
        override fun areItemsTheSame(old: HarvestEntry, new: HarvestEntry) = old.id == new.id
        override fun areContentsTheSame(old: HarvestEntry, new: HarvestEntry) = old == new
    }
}