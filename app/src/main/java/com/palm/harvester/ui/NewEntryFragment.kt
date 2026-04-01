package com.palm.harvester.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.palm.harvester.R
import com.palm.harvester.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class NewEntryFragment : Fragment(R.layout.fragment_new_entry) {
    private var ripe = 0; private var empty = 0
    private var photoB64 = ""
    private var lat = 0.0; private var lon = 0.0

    // 1. THE STABLE CAMERA CONTRACT (Optimized for thumbnails)
    private val takePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            processImage(bitmap)
        } else {
            Toast.makeText(context, "Photo cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. PERMISSION HANDLER
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try { takePreview.launch(null) } catch(e: Exception) { 
                Toast.makeText(context, "Camera Error: ${e.message}", Toast.LENGTH_SHORT).show() 
            }
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val tRipe = view.findViewById<TextView>(R.id.txtRipeCount)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)
        val imgPreview = view.findViewById<ImageView>(R.id.imgPreview)

        val prefs = requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
        editBlock.setText(prefs.getString("last_block", ""))

        fun vibrate(duration: Long = 50) {
            try {
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else { vibrator.vibrate(duration) }
                }
            } catch (e: Exception) {}
        }

        view.findViewById<Button>(R.id.btnPlusRipe).setOnClickListener { vibrate(); ripe++; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnMinusRipe).setOnClickListener { vibrate(); if(ripe > 0) ripe--; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { vibrate(); empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { vibrate(); if(empty > 0) empty--; tEmpty.text = empty.toString() }

        // BUTTON CLICK LOGIC
        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try { takePreview.launch(null) } catch(e: Exception) { 
                    Toast.makeText(context, "Could not open camera", Toast.LENGTH_SHORT).show() 
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { 
            requireActivity().onBackPressedDispatcher.onBackPressed() 
        }
        
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val block = editBlock.text.toString().trim()
            if (block.isEmpty()) {
                Toast.makeText(context, "Enter Block ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener 
            }

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

                launch(Dispatchers.Main) { 
                    vibrate(150)
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        updateGps()
    }

    private fun processImage(bitmap: Bitmap) {
        try {
            // Scale and compress
            val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
            photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            
            // Show preview
            view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
            Toast.makeText(context, "Photo Captured", Toast.LENGTH_SHORT).show()
        } catch(e: Exception) {
            Toast.makeText(context, "Processing Error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGps() {
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation.addOnSuccessListener { 
                    lat = it?.latitude ?: 0.0; lon = it?.longitude ?: 0.0 
                }
            }
        } catch(e: Exception) {}
    }
}