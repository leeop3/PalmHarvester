package com.palm.harvester.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.palm.harvester.R
import com.palm.harvester.data.*
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        val txtDate = view.findViewById<TextView>(R.id.txtSelectedDate)
        val txtDayStats = view.findViewById<TextView>(R.id.txtDayStats)
        val txtMonthStats = view.findViewById<TextView>(R.id.txtMonthStats)
        val db = AppDatabase.getInstance(requireContext())

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthSdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        // Initial Month stats
        db.harvestDao().getSummaryForMonth(monthSdf.format(Date())).observe(viewLifecycleOwner) { sum ->
            txtMonthStats.text = "This Month: ${sum?.totalBunches ?: 0} Bunches\n(Ripe: ${sum?.totalRipe ?: 0} | Empty: ${sum?.totalEmpty ?: 0})"
        }

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            val selectedDate = sdf.format(cal.time)
            
            txtDate.text = "Date: $selectedDate"
            
            db.harvestDao().getSummaryForDate(selectedDate).observe(viewLifecycleOwner) { sum ->
                if (sum != null && sum.entryCount > 0) {
                    txtDayStats.text = "Total: ${sum.totalBunches} | Ripe: ${sum.totalRipe} | Empty: ${sum.totalEmpty}\nEntries: ${sum.entryCount}"
                } else {
                    txtDayStats.text = "No records for this date."
                }
            }
        }
    }
}