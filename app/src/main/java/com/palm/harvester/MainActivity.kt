package com.palm.harvester

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.palm.harvester.databinding.ActivityMainBinding
import com.palm.harvester.network.HarvesterService
import com.palm.harvester.ui.*
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        startService(Intent(this, HarvesterService::class.java))

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
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.fragment_container, LogFragment()).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Settings").setIcon(android.R.drawable.ic_menu_preferences).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 2, 1, "Connect RNode").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { showSettings(); true }
            2 -> { showDevicePicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettings() {
        val prefs = getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val editNick = view.findViewById<EditText>(R.id.setNick)
        val editBase = view.findViewById<EditText>(R.id.setBase)
        val editFreq = view.findViewById<EditText>(R.id.setFreq)
        
        editNick.setText(prefs.getString("nickname", ""))
        editBase.setText(prefs.getString("base_addr", ""))
        editFreq.setText(prefs.getInt("freq", 433000000).toString())

        AlertDialog.Builder(this).setTitle("Profile & Radio")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().apply {
                    putString("nickname", editNick.text.toString())
                    putString("base_addr", editBase.text.toString())
                    putInt("freq", editFreq.text.toString().toIntOrNull() ?: 433000000)
                    apply()
                }
                Toast.makeText(this, "Settings Saved. Reconnect RNode to apply.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showDevicePicker() {
        val btManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val paired = btManager.adapter.bondedDevices.toList()
        AlertDialog.Builder(this).setTitle("Select RNode")
            .setItems(paired.map { it.name }.toTypedArray()) { _, i ->
                val intent = Intent(this, HarvesterService::class.java).apply {
                    action = HarvesterService.ACTION_CONNECT
                    putExtra(HarvesterService.EXTRA_DEVICE, paired[i])
                }
                startService(intent)
            }.show()
    }
}