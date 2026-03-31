package com.palm.harvester.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
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
    private var ripe = 0; private var empty = 0
    private var photoB64 = ""
    private var lat = 0.0; private var lon = 0.0

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { processImage(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // VIBRATION HAPTICS
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val tRipe = view.findViewById<TextView>(R.id.txtRipeCount)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)

        // SMART MEMORY: Load the last used Block ID
        val prefs = requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
        editBlock.setText(prefs.getString("last_block", ""))

        fun vibrate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else { vibrator.vibrate(50) }
        }

        view.findViewById<Button>(R.id.btnPlusRipe).setOnClickListener { vibrate(); ripe++; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnMinusRipe).setOnClickListener { vibrate(); if(ripe > 0) ripe--; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { vibrate(); empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { vibrate(); if(empty > 0) empty--; tEmpty.text = empty.toString() }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(cameraIntent)
        }
        
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val block = editBlock.text.toString().trim()
            if (block.isEmpty()) return@setOnClickListener 

            lifecycleScope.launch(Dispatchers.IO) {
                val now = Date()
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                val entry = HarvestEntry(
                    blockId = block, ripeCount = ripe, emptyCount = empty,
                    latitude = lat, longitude = lon, timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now),
                    reportDate = day, photoBase64 = photoB64
                )
                AppDatabase.getInstance(requireContext()).harvestDao().insert(entry)
                
                // SAVE TO SMART MEMORY
                prefs.edit().putString("last_block", block).apply()

                launch(Dispatchers.Main) { 
                    // Long vibrate for success
                    vibrator.vibrate(200)
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        updateGps()
    }

    private fun processImage(bitmap: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
        photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
    }

    private fun updateGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation.addOnSuccessListener { 
                lat = it?.latitude ?: 0.0; lon = it?.longitude ?: 0.0 
            }
        }
    }
}