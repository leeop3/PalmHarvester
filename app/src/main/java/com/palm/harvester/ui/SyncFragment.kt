package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.palm.harvester.R
import com.palm.harvester.data.AppDatabase
import com.palm.harvester.data.HarvestEntry
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncFragment : Fragment(R.layout.fragment_sync) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        
        view.findViewById<Button>(R.id.btnSync).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val unsent: List<HarvestEntry> = db.harvestDao().getUnsentEntries()
                if (unsent.isEmpty()) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "No new data to sync", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                
                try {
                    val py = Python.getInstance().getModule("rns_engine")
                    unsent.forEach { entry: HarvestEntry ->
                        val res = py.callAttr("send_report", "TARGET_ADDR_HERE", "NICKNAME", entry.blockId, entry.ripeCount, entry.emptyCount, entry.latitude, entry.longitude, entry.timestamp, entry.photoBase64)
                        if (res.toString() == "Report Sent") {
                            db.harvestDao().markAsSynced(entry.id)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}