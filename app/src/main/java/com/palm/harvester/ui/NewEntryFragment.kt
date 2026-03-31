package com.palm.harvester.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
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

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
            photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tRipe = view.findViewById<TextView>(R.id.txtRipeCount)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)

        view.findViewById<Button>(R.id.btnPlusRipe).setOnClickListener { ripe++; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnMinusRipe).setOnClickListener { if(ripe>0) ripe--; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { if(empty>0) empty--; tEmpty.text = empty.toString() }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener { takePhoto.launch(null) }
        
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { parentFragmentManager.popBackStack() }
        
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val block = editBlock.text.toString()
            if (block.isEmpty()) { Toast.makeText(context, "Enter Block ID", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

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
                launch(Dispatchers.Main) { parentFragmentManager.popBackStack() }
            }
        }
        updateGps()
    }

    private fun updateGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation.addOnSuccessListener { 
                lat = it?.latitude ?: 0.0; lon = it?.longitude ?: 0.0 
            }
        }
    }
}