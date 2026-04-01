package com.palm.harvester.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.palm.harvester.R
import com.palm.harvester.data.*
import kotlinx.coroutines.launch

class RecordsFragment : Fragment(R.layout.fragment_records) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvRecords)
        val adapter = RecordAdapter { entry -> confirmDelete(entry) }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        AppDatabase.getInstance(requireContext()).harvestDao().getAllEntries().observe(viewLifecycleOwner) { 
            adapter.submitList(it) 
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
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_record, p, false))
    override fun onBindViewHolder(h: VH, p: Int) {
        val i = getItem(p)
        h.block.text = i.blockId
        h.stats.text = "Ripe: ${i.ripeCount} | Empty: ${i.emptyCount} | Total: ${i.ripeCount + i.emptyCount}"
        h.gps.text = "GPS: ${String.format("%.4f", i.latitude)}, ${String.format("%.4f", i.longitude)} | ${i.timestamp}"
        
        if (i.photoBase64.isNotEmpty()) {
            val bytes = Base64.decode(i.photoBase64, Base64.DEFAULT)
            h.img.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        } else {
            h.img.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        
        h.btnDel.setOnClickListener { onDeleteClick(i) }
    }
    class Diff : DiffUtil.ItemCallback<HarvestEntry>() {
        override fun areItemsTheSame(o: HarvestEntry, n: HarvestEntry) = o.id == n.id
        override fun areContentsTheSame(o: HarvestEntry, n: HarvestEntry) = o == n
    }
}