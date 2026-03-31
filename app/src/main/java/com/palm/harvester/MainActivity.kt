package com.palm.harvester

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.google.android.gms.location.LocationServices
import com.palm.harvester.databinding.ActivityMainBinding
import com.palm.harvester.network.HarvesterService
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var ripeCount = 0
    private var emptyCount = 0
    private var photoBase64 = ""
    private var lastLocation: Location? = null

    // 1. Camera Launcher
    private val takePhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as Bitmap
            processImage(imageBitmap)
        }
    }

    // 2. Bluetooth Permission Launcher
    private val btPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) showDevicePicker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCounters()
        
        binding.btnPhoto.setOnClickListener { takePhoto.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
        
        binding.btnSubmit.setOnClickListener { sendReport() }

        binding.statusText.setOnClickListener { requestBtPerms() }

        // Observe Mesh Status
        HarvesterService.serviceStatus.observe(this) { status ->
            binding.statusText.text = status
            binding.statusText.setTextColor(if (status.contains("Ready") || status.contains("Active")) 0xFF2E7D32.toInt() else 0xFFFF0000.toInt())
        }

        startService(Intent(this, HarvesterService::class.java))
        updateGps()
    }

    private fun setupCounters() {
        binding.btnPlusRipe.setOnClickListener { ripeCount++; binding.txtRipeCount.text = ripeCount.toString() }
        binding.btnMinusRipe.setOnClickListener { if(ripeCount > 0) ripeCount--; binding.txtRipeCount.text = ripeCount.toString() }
        binding.btnPlusEmpty.setOnClickListener { emptyCount++; binding.txtEmptyCount.text = emptyCount.toString() }
        binding.btnMinusEmpty.setOnClickListener { if(emptyCount > 0) emptyCount--; binding.txtEmptyCount.text = emptyCount.toString() }
    }

    private fun processImage(bitmap: Bitmap) {
        // CRITICAL FOR LORA: Scale down to 100x100 and compress to 50% WebP
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
        val imageBytes = baos.toByteArray()
        photoBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        binding.imgPreview.setImageBitmap(scaled)
        Toast.makeText(this, "Photo ready (${imageBytes.size} bytes)", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun updateGps() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.lastLocation.addOnSuccessListener { lastLocation = it }
        } else {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) updateGps() }.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun sendReport() {
        val target = binding.editTarget.text.toString()
        val harvester = binding.editHarvesterId.text.toString()
        val block = binding.editBlockId.text.toString()

        if (target.length < 10 || harvester.isEmpty() || block.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show(); return
        }

        val lat = lastLocation?.latitude ?: 0.0
        val lon = lastLocation?.longitude ?: 0.0

        Thread {
            try {
                val py = Python.getInstance().getModule("rns_engine")
                val result = py.callAttr("send_report", target, harvester, block, ripeCount, emptyCount, lat, lon, photoBase64)
                runOnUiThread { Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Python Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun requestBtPerms() {
        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        btPermissionLauncher.launch(perms)
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        val paired = adapter.bondedDevices.toList()
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