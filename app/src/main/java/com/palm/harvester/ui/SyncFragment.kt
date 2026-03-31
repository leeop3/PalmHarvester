package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.palm.harvester.R
import com.palm.harvester.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncFragment : Fragment(R.layout.fragment_sync) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        val txtStats = view.findViewById<TextView>(R.id.txtSyncStats)

        // Observe data to show counts
        db.harvestDao().getAllEntries().observe(viewLifecycleOwner) { list ->
            val pending = list.count { !it.isSynced }
            val synced = list.count { it.isSynced }
            txtStats.text = "Pending: $pending | Synced: $synced"
        }

        view.findViewById<Button>(R.id.btnSync).setOnClickListener {
            // Logic to trigger performSync()
        }
    }
}