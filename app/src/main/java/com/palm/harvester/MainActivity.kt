package com.palm.harvester

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.palm.harvester.databinding.ActivityMainBinding
import com.palm.harvester.network.HarvesterService
import com.palm.harvester.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val btPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) showDevicePicker()
        else Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
    }

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
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, LogFragment()).commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val settingsItem = menu.add(0, 1, 0, "Settings")
        settingsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        val connectItem = menu.add(0, 2, 1, "Connect RNode")
        connectItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { showSettings(); true }
            2 -> { checkBtAndShowPicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkBtAndShowPicker() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)

        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            showDevicePicker()
        } else {
            btPermissionLauncher.launch(perms)
        }
    }

    private fun showDevicePicker() {
        val btManager = getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val paired = adapter.bondedDevices.toList()
        if (paired.isEmpty()) {
            Toast.makeText(this, "No paired RNodes found", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this).setTitle("Select RNode")
            .setItems(paired.map { it.name ?: it.address }.toTypedArray()) { _, i ->
                val intent = Intent(this, HarvesterService::class.java).apply {
                    action = HarvesterService.ACTION_CONNECT
                    putExtra(HarvesterService.EXTRA_DEVICE, paired[i])
                }
                startService(intent)
            }.show()
    }

    private fun showSettings() {
        val prefs = getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        val editNick = view.findViewById<EditText>(R.id.setNick)
        val editBase = view.findViewById<EditText>(R.id.setBase)
        val editFreq = view.findViewById<EditText>(R.id.setFreq)
        val editTx = view.findViewById<EditText>(R.id.setTx)
        val spinSf = view.findViewById<Spinner>(R.id.setSf)
        val spinBw = view.findViewById<Spinner>(R.id.setBw)
        val spinCr = view.findViewById<Spinner>(R.id.setCr)

        editNick.setText(prefs.getString("nickname", ""))
        editBase.setText(prefs.getString("base_addr", ""))
        editFreq.setText(prefs.getInt("freq", 433000000).toString())
        editTx.setText(prefs.getInt("tx", 17).toString())

        AlertDialog.Builder(this).setTitle("Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().apply {
                    putString("nickname", editNick.text.toString())
                    putString("base_addr", editBase.text.toString())
                    putInt("freq", editFreq.text.toString().toIntOrNull() ?: 433000000)
                    putInt("tx", editTx.text.toString().toIntOrNull() ?: 17)
                    putInt("sf", spinSf.selectedItem.toString().toInt())
                    putInt("bw", spinBw.selectedItem.toString().toInt())
                    putInt("cr", spinCr.selectedItem.toString().toInt())
                    apply()
                }
                Toast.makeText(this, "Saved. Reconnect RNode to apply.", Toast.LENGTH_SHORT).show()
            }.show()
    }
}