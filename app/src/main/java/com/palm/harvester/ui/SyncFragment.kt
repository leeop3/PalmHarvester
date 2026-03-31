package com.palm.harvester.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.palm.harvester.R
import com.palm.harvester.data.*
import kotlinx.coroutines.*

class SyncFragment : Fragment(R.layout.fragment_sync) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = AppDatabase.getInstance(requireContext())
        val txtStats = view.findViewById<TextView>(R.id.txtSyncStats)
        val btnSync = view.findViewById<Button>(R.id.btnSync)

        db.harvestDao().getAllEntries().observe(viewLifecycleOwner) { list ->
            val pending = list.count { !it.isSynced }
            val synced = list.count { it.isSynced }
            txtStats.text = "Pending: $pending | Synced: $synced"
            btnSync.isEnabled = pending > 0
        }

        btnSync.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
            val baseAddr = prefs.getString("base_addr", "") ?: ""
            val nickname = prefs.getString("nickname", "Harvester") ?: "Harvester"

            if (baseAddr.length < 10) {
                Toast.makeText(context, "Set Base Station Address in Settings", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val unsent = db.harvestDao().getUnsentEntries()
                val py = Python.getInstance().getModule("rns_engine")
                
                withContext(Dispatchers.Main) { btnSync.text = "Syncing..."; btnSync.isEnabled = false }

                unsent.forEach { entry ->
                    try {
                        val res = py.callAttr("send_report", baseAddr, nickname, entry.blockId, 
                            entry.ripeCount, entry.emptyCount, entry.latitude, entry.longitude, 
                            entry.timestamp, entry.photoBase64)
                        
                        if (res.toString() == "Report Sent") {
                            db.harvestDao().markAsSynced(entry.id)
                        }
                    } catch (e: Exception) { }
                }

                withContext(Dispatchers.Main) { 
                    btnSync.text = "SYNC NOW"
                    btnSync.isEnabled = true
                    Toast.makeText(context, "Sync Finished", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}