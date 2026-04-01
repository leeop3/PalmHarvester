package com.palm.harvester.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.palm.harvester.R
import com.palm.harvester.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NewEntryFragment : Fragment(R.layout.fragment_new_entry) {
    private var ripe = 0; private var empty = 0
    private var photoB64 = ""
    private var lat = 0.0; private var lon = 0.0
    private var currentPhotoPath: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // 1. STABLE CAMERA LAUNCHER
    private val takePhotoAction = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoPath != null) {
            processImageFile(currentPhotoPath!!)
        } else {
            Toast.makeText(context, "Photo failed or cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. UNIFIED PERMISSION LAUNCHER
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) startLocationUpdates()
        if (results[Manifest.permission.CAMERA] == true) { /* Ready */ }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        // Request all hardware rights at once
        requestPermissions.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        val tRipe = view.findViewById<TextView>(R.id.txtRipeCount)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        // Load last block from memory
        val prefs = requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
        editBlock.setText(prefs.getString("last_block", ""))

        view.findViewById<Button>(R.id.btnPlusRipe).setOnClickListener { ripe++; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnMinusRipe).setOnClickListener { if(ripe > 0) ripe--; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { if(empty > 0) empty--; tEmpty.text = empty.toString() }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener { launchCamera() }
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        
        btnSave.setOnClickListener {
            val block = editBlock.text.toString().trim()
            if (block.isEmpty()) { Toast.makeText(context, "Enter Block ID", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (lat == 0.0) { Toast.makeText(context, "Waiting for GPS Fix...", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            lifecycleScope.launch(Dispatchers.IO) {
                val now = Date()
                val entry = HarvestEntry(
                    blockId = block, ripeCount = ripe, emptyCount = empty,
                    latitude = lat, longitude = lon, 
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now),
                    reportDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now), 
                    photoBase64 = photoB64
                )
                AppDatabase.getInstance(requireContext()).harvestDao().insert(entry)
                prefs.edit().putString("last_block", block).apply()
                launch(Dispatchers.Main) { requireActivity().onBackPressedDispatcher.onBackPressed() }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(1f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                lat = loc.latitude
                lon = loc.longitude
                // Optional: show a tiny toast or update UI to show GPS is active
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun launchCamera() {
        try {
            val file = File(requireContext().cacheDir, "temp_harvest.jpg")
            if (file.exists()) file.delete()
            file.createNewFile()
            currentPhotoPath = file.absolutePath
            val uri = FileProvider.getUriForFile(requireContext(), "com.palm.harvester.fileprovider", file)
            takePhotoAction.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImageFile(path: String) {
        // Use sampling to prevent OOM crash
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return
        
        // Scale to LoRa-safe size
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
        photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        
        view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}