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
        
        val txtTotal = view.findViewById<TextView>(R.id.txtTodayTotal)
        val txtStats = view.findViewById<TextView>(R.id.txtTodayStats)
        val btnNew = view.findViewById<Button>(R.id.btnNewEntry)

        AppDatabase.getInstance(requireContext()).harvestDao().getSummaryForDate(today).observe(viewLifecycleOwner) { summary: DaySummary? ->
            if (summary != null && summary.entryCount > 0) {
                txtTotal.text = "Total: ${summary.totalBunches} Bunches"
                txtStats.text = "Ripe: ${summary.totalRipe} | Empty: ${summary.totalEmpty}"
            } else {
                txtTotal.text = "Total: 0 Bunches"
                txtStats.text = "No entries today"
            }
        }

        btnNew.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NewEntryFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}