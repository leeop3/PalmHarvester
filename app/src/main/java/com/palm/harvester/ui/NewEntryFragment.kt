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
    private var photoUri: Uri? = null
    private var tempPhotoFile: File? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 1. THE STABLE FILE CONTRACT
    private val takePhotoAction = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempPhotoFile?.let { file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    processImage(bitmap)
                    requestFreshLocation()
                }
            }
        } else {
            Toast.makeText(context, "Photo cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) launchCamera()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        val tRipe = view.findViewById<TextView>(R.id.txtRipeCount)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)
        val prefs = requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
        editBlock.setText(prefs.getString("last_block", ""))

        view.findViewById<Button>(R.id.btnPlusRipe).setOnClickListener { ripe++; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnMinusRipe).setOnClickListener { if(ripe > 0) ripe--; tRipe.text = ripe.toString() }
        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { if(empty > 0) empty--; tEmpty.text = empty.toString() }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val block = editBlock.text.toString().trim()
            if (block.isEmpty()) { Toast.makeText(context, "Enter Block ID", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

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
        requestFreshLocation()
    }

    private fun launchCamera() {
        try {
            val photoFile = File.createTempFile("IMG_", ".jpg", requireContext().cacheDir)
            tempPhotoFile = photoFile
            photoUri = FileProvider.getUriForFile(requireContext(), "com.palm.harvester.fileprovider", photoFile)
            takePhotoAction.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera Error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
        photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
    }

    private fun requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).setMaxUpdates(1).build()
            fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation
                    lat = location?.latitude ?: 0.0
                    lon = location?.longitude ?: 0.0
                }
            }, Looper.getMainLooper())
        }
    }
}