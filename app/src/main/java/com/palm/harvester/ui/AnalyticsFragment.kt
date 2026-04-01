package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.palm.harvester.R
import com.palm.harvester.data.*
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        
        val calendar = view.findViewById<CalendarView>(R.id.calendarView)
        val txtMonthTotal = view.findViewById<TextView>(R.id.txtMonthTotal)
        val txtMonthStats = view.findViewById<TextView>(R.id.txtMonthStats)
        val txtBlocks = view.findViewById<TextView>(R.id.txtBlockAggregation)

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        db.harvestDao().getSummaryForMonth(currentMonth).observe(viewLifecycleOwner) { summary: DaySummary? ->
            summary?.let {
                txtMonthTotal.text = "Total: ${it.totalBunches} Bunches"
                txtMonthStats.text = "Ripe: ${it.totalRipe} | Empty: ${it.totalEmpty}"
            }
        }

        db.harvestDao().getBlockSummaryForMonth(currentMonth).observe(viewLifecycleOwner) { list ->
            if (!list.isNullOrEmpty()) {
                val sb = StringBuilder()
                list.forEach { 
                    sb.append("• Block ${it.blockId}: ${it.totalBunches} bunches\n") 
                }
                txtBlocks.text = sb.toString()
            } else {
                txtBlocks.text = "No data for this month."
            }
        }

        calendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            showDateDetails(selectedDate)
        }
    }

    private fun showDateDetails(date: String) {
        val db = AppDatabase.getInstance(requireContext())
        db.harvestDao().getSummaryForDate(date).observe(viewLifecycleOwner) { summary ->
            val msg = if (summary != null && summary.entryCount > 0) {
                "Total Bunches: ${summary.totalBunches}\n" +
                "Ripe: ${summary.totalRipe}\n" +
                "Empty: ${summary.totalEmpty}\n" +
                "Entries: ${summary.entryCount}"
            } else {
                "No harvest records for this date."
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Report: $date")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}