package com.palm.harvester

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
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

    // 1. STABLE CAMERA LAUNCHER (Returns thumbnail directly)
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            processImage(bitmap)
        } else {
            Toast.makeText(this, "Photo cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. PERMISSION LAUNCHER
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            takePhoto.launch()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val btPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) showDevicePicker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCounters()
        
        // Check permission before launching camera
        binding.btnPhoto.setOnClickListener { 
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto.launch()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        binding.btnSubmit.setOnClickListener { sendReport() }
        binding.statusText.setOnClickListener { requestBtPerms() }

        HarvesterService.serviceStatus.observe(this) { status ->
            binding.statusText.text = status
            binding.statusText.setTextColor(if (status.contains("Active") || status.contains("Ready")) 0xFF2E7D32.toInt() else 0xFFFF0000.toInt())
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
        // RESIZE: 100x100 is the limit for LoRa stability
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val baos = ByteArrayOutputStream()
        // WEBP: 50% quality gives us ~3KB strings
        scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
        val imageBytes = baos.toByteArray()
        photoBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        binding.imgPreview.setImageBitmap(scaled)
        Toast.makeText(this, "Photo Compressed: ${imageBytes.size} bytes", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun updateGps() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.lastLocation.addOnSuccessListener { lastLocation = it }
        }
    }

    private fun sendReport() {
        val target = binding.editTarget.text.toString()
        val harvester = binding.editHarvesterId.text.toString()
        val block = binding.editBlockId.text.toString()

        if (target.length < 10 || harvester.isEmpty() || block.isEmpty()) {
            Toast.makeText(this, "Fill all fields and scan receiver address", Toast.LENGTH_SHORT).show(); return
        }

        val lat = lastLocation?.latitude ?: 0.0
        val lon = lastLocation?.longitude ?: 0.0

        Thread {
            try {
                val py = Python.getInstance().getModule("rns_engine")
                val result = py.callAttr("send_report", target, harvester, block, ripeCount, emptyCount, lat, lon, photoBase64)
                runOnUiThread { Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
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