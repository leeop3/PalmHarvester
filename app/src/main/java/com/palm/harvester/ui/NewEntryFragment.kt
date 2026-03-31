package com.palm.harvester.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
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
    private var ripe = 0
    private var empty = 0
    private var photoB64 = ""
    private var lat = 0.0
    private var lon = 0.0

    // 1. ROBUST CAMERA LAUNCHER (Standard Intent)
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                if (imageBitmap != null) {
                    processImage(imageBitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. CAMERA PERMISSION LAUNCHER
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tRipe = view.findViewById<TextView>(R.id.txtRipeCount)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)

        view.findViewById<Button>(R.id.btnPlusRipe).setOnClickListener { ripe++; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnMinusRipe).setOnClickListener { if(ripe > 0) ripe--; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { if(empty > 0) empty--; tEmpty.text = empty.toString() }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            checkPermissionAndLaunchCamera()
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
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                
                val entry = HarvestEntry(
                    blockId = block, ripeCount = ripe, emptyCount = empty,
                    latitude = lat, longitude = lon, timestamp = ts,
                    reportDate = day, photoBase64 = photoB64
                )
                AppDatabase.getInstance(requireContext()).harvestDao().insert(entry)
                launch(Dispatchers.Main) { 
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        updateGps()
    }

    private fun checkPermissionAndLaunchCamera() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            cameraLauncher.launch(cameraIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(bitmap: Bitmap) {
        // COMPRESS FOR LORA: 100x100 is mandatory for bandwidth safety
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
        val imageBytes = baos.toByteArray()
        photoB64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        
        view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
        Toast.makeText(context, "Photo ready", Toast.LENGTH_SHORT).show()
    }

    private fun updateGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation.addOnSuccessListener { 
                lat = it?.latitude ?: 0.0; lon = it?.longitude ?: 0.0 
            }
        }
    }
}