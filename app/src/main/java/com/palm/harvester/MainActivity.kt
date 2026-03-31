package com.palm.harvester

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.palm.harvester.databinding.ActivityMainBinding
import com.palm.harvester.network.HarvesterService
import com.palm.harvester.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Start Mesh Service
        startService(Intent(this, HarvesterService::class.java))

        // Navigation Logic
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when(item.itemId) {
                R.id.nav_log -> LogFragment()
                R.id.nav_records -> RecordsFragment()
                R.id.nav_analytics -> AnalyticsFragment()
                R.id.nav_sync -> SyncFragment()
                else -> LogFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
            true
        }
        
        // Load default page
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, LogFragment()).commit()
        }
    }
}