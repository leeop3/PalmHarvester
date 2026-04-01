package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.palm.harvester.R
import com.palm.harvester.data.AppDatabase
import com.palm.harvester.data.DaySummary
import java.text.SimpleDateFormat
import java.util.*

class LogFragment : Fragment(R.layout.fragment_log) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val db = AppDatabase.getInstance(requireContext())

        val txtTotal = view.findViewById<TextView>(R.id.txtTodayTotal)
        val txtStats = view.findViewById<TextView>(R.id.txtTodayStats)
        val txtGps = view.findViewById<TextView>(R.id.txtLastGps)

        // 1. Observe Today's Totals
        db.harvestDao().getSummaryForDate(today).observe(viewLifecycleOwner) { summary: DaySummary? ->
            if (summary != null && summary.entryCount > 0) {
                txtTotal.text = "${summary.totalBunches} Bunches"
                txtStats.text = "Ripe: ${summary.totalRipe} | Empty: ${summary.totalEmpty}"
            } else {
                txtTotal.text = "0 Bunches"
                txtStats.text = "No records today"
                txtGps.text = "GPS: No data"
            }
        }

        // 2. Observe all entries to find the latest GPS
        db.harvestDao().getAllEntries().observe(viewLifecycleOwner) { entries ->
            if (!entries.isNullOrEmpty()) {
                val latest = entries.first()
                txtGps.text = String.format("Last Location: %.5f, %.5f", latest.latitude, latest.longitude)
            }
        }

        view.findViewById<Button>(R.id.btnNewEntry).setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NewEntryFragment())
                .addToBackStack(null).commit()
        }
    }
}