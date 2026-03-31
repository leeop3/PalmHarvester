package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.palm.harvester.R
import com.palm.harvest.data.AppDatabase
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
                val unsent = db.harvestDao().getUnsentEntries()
                if (unsent.isEmpty()) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "No new data to sync", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                
                val py = Python.getInstance().getModule("rns_engine")
                // Simplified sync loop
                unsent.forEach { entry ->
                    val res = py.callAttr("send_report", "BASE_ADDR", "NICK", entry.blockId, entry.ripeCount, entry.emptyCount, entry.latitude, entry.longitude, entry.timestamp, entry.photoBase64)
                    if (res.toString() == "Report Sent") {
                        db.harvestDao().markAsSynced(entry.id)
                    }
                }
            }
        }
    }
}