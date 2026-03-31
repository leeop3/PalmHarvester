package com.palm.harvester.ui

import android.os.Bundle
import android.view.*
import android.widget.TextView
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
        val adapter = RecordAdapter { entry -> showOptions(entry) }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        AppDatabase.getInstance(requireContext()).harvestDao().getAllEntries().observe(viewLifecycleOwner) { 
            adapter.submitList(it) 
        }
    }

    private fun showOptions(entry: HarvestEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Manage Record")
            .setItems(arrayOf("Delete Entry")) { _, _ ->
                lifecycleScope.launch { AppDatabase.getInstance(requireContext()).harvestDao().delete(entry) }
            }.show()
    }
}

class RecordAdapter(val onLongClick: (HarvestEntry) -> Unit) : ListAdapter<HarvestEntry, RecordAdapter.VH>(Diff()) {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val block: TextView = v.findViewById(R.id.recBlock)
        val stats: TextView = v.findViewById(R.id.recStats)
        val time: TextView = v.findViewById(R.id.recTime)
        val status: TextView = v.findViewById(R.id.recSyncStatus)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_record, p, false))
    override fun onBindViewHolder(h: VH, p: Int) {
        val i = getItem(p)
        h.block.text = "Block: ${i.blockId}"
        h.stats.text = "Ripe: ${i.ripeCount} | Empty: ${i.emptyCount}"
        h.time.text = i.timestamp
        h.status.text = if (i.isSynced) "✅" else "⏳"
        h.itemView.setOnLongClickListener { onLongClick(i); true }
    }
    class Diff : DiffUtil.ItemCallback<HarvestEntry>() {
        override fun areItemsTheSame(o: HarvestEntry, n: HarvestEntry) = o.id == n.id
        override fun areContentsTheSame(o: HarvestEntry, n: HarvestEntry) = o == n
    }
}