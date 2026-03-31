package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.palm.harvester.R
import com.palm.harvest.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.*

class LogFragment : Fragment(R.layout.fragment_log) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        AppDatabase.getInstance(requireContext()).harvestDao().getSummaryForDate(today).observe(viewLifecycleOwner) { summary ->
            if (summary != null) {
                view.findViewById<TextView>(R.id.txtTodayTotal).text = "Total: ${summary.totalBunches} Bunches"
                view.findViewById<TextView>(R.id.txtTodayStats).text = "Ripe: ${summary.totalRipe} | Empty: ${summary.totalEmpty}"
            }
        }
        
        view.findViewById<Button>(R.id.btnNewEntry).setOnClickListener {
            // Logic for New Entry Screen
        }
    }
}