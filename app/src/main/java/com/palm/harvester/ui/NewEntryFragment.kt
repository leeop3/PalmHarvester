package com.palm.harvester.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
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
    private var editId: Long = -1
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // 1. FIXED FILENAME: This ensures the path is never null after an app restart
    private fun getTempFile(): File {
        val dir = File(requireContext().cacheDir, "camera")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "last_photo.jpg")
    }

    private val takePhotoAction = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            processImageFile(getTempFile())
        } else {
            Toast.makeText(context, "Photo cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) startLocationUpdates()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        
        val tRipeManual = view.findViewById<EditText>(R.id.txtRipeManual)
        val sRipe = view.findViewById<SeekBar>(R.id.seekRipe)
        val tEmpty = view.findViewById<TextView>(R.id.txtEmptyCount)
        val editBlock = view.findViewById<EditText>(R.id.editBlockId)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val lblTitle = view.findViewById<TextView>(R.id.lblFormTitle)

        // Request permissions immediately
        requestPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))

        editId = arguments?.getLong("edit_id", -1) ?: -1
        if (editId != -1L) {
            lblTitle.text = "EDIT HARVEST RECORD"
            btnSave.text = "UPDATE"
            lifecycleScope.launch(Dispatchers.IO) {
                val entry = AppDatabase.getInstance(requireContext()).harvestDao().getEntryById(editId)
                entry?.let { record ->
                    launch(Dispatchers.Main) {
                        editBlock.setText(record.blockId)
                        ripe = record.ripeCount; empty = record.emptyCount
                        sRipe.progress = ripe; tRipeManual.setText(ripe.toString())
                        tEmpty.text = empty.toString()
                        photoB64 = record.photoBase64
                        lat = record.latitude; lon = record.longitude
                    }
                }
            }
        } else {
            val prefs = requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
            editBlock.setText(prefs.getString("last_block", ""))
        }

        sRipe.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) { ripe = p; tRipeManual.setText(p.toString()) } }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        tRipeManual.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val v = s.toString().toIntOrNull() ?: 0
                if (v != sRipe.progress) { ripe = v; sRipe.progress = v }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        view.findViewById<Button>(R.id.btnPlusEmpty).setOnClickListener { empty++; tEmpty.text = empty.toString() }
        view.findViewById<Button>(R.id.btnMinusEmpty).setOnClickListener { if(empty > 0) empty--; tEmpty.text = empty.toString() }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener { launchCamera() }
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        
        btnSave.setOnClickListener {
            val block = editBlock.text.toString().trim()
            if (block.isEmpty()) { Toast.makeText(context, "Enter Block ID", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (lat == 0.0 && editId == -1L) { Toast.makeText(context, "Waiting for GPS Fix...", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            lifecycleScope.launch(Dispatchers.IO) {
                val now = Date()
                val entry = HarvestEntry(
                    id = if(editId != -1L) editId else 0,
                    blockId = block, ripeCount = ripe, emptyCount = empty,
                    latitude = lat, longitude = lon, 
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now),
                    reportDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now), 
                    photoBase64 = photoB64,
                    isSynced = false
                )
                if (editId != -1L) AppDatabase.getInstance(requireContext()).harvestDao().update(entry)
                else AppDatabase.getInstance(requireContext()).harvestDao().insert(entry)
                
                requireContext().getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE).edit().putString("last_block", block).apply()
                launch(Dispatchers.Main) { 
                    try { vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)) } catch(e:Exception){}
                    requireActivity().onBackPressedDispatcher.onBackPressed() 
                }
            }
        }
        startLocationUpdates()
    }

    private fun launchCamera() {
        try {
            val photoFile = getTempFile()
            if (photoFile.exists()) photoFile.delete()
            photoFile.createNewFile()
            val uri = FileProvider.getUriForFile(requireContext(), "com.palm.harvester.fileprovider", photoFile)
            takePhotoAction.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImageFile(file: File) {
        try {
            // Memory safe decoding
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return
            
            val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.WEBP, 50, baos)
            photoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            
            view?.findViewById<ImageView>(R.id.imgPreview)?.setImageBitmap(scaled)
        } catch(e: Exception) {
            Toast.makeText(context, "Image Processing Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) { 
                res.lastLocation?.let { lat = it.latitude; lon = it.longitude } 
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}