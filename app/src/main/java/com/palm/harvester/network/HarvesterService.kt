package com.palm.harvester.network

import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import org.json.JSONObject

class HarvesterService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var btSocket: BluetoothSocket? = null
    private var tcpServer: ServerSocket? = null

    companion object {
        val serviceStatus = MutableLiveData("Disconnected")
        const val ACTION_CONNECT = "connect"
        const val EXTRA_DEVICE = "device"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)
            } else { @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DEVICE) }
            device?.let { startBridge(it) }
        }
        return START_STICKY
    }

    private fun startBridge(device: BluetoothDevice) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                onStatusUpdate("Connecting BT...")
                btSocket?.close(); tcpServer?.close()
                
                val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                btSocket = m.invoke(device, 1) as BluetoothSocket
                btSocket?.connect()
                
                tcpServer = ServerSocket()
                tcpServer?.reuseAddress = true
                tcpServer?.bind(InetSocketAddress("127.0.0.1", 7633))
                
                onStatusUpdate("Bridge 7633 Listening")
                
                // Start the background accept loop
                launch { handleTcpClients() }

                // Wait 500ms then tell Python to inject
                delay(500)
                injectPython()

            } catch (e: Exception) {
                onStatusUpdate("Bridge Error")
                Log.e("PalmHarvester", "Bridge Failed", e)
            }
        }
    }

    private suspend fun handleTcpClients() {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    val client = tcpServer?.accept() ?: break
                    client.tcpNoDelay = true
                    onStatusUpdate("Mesh Active")

                    val btIn = btSocket!!.inputStream
                    val btOut = btSocket!!.outputStream
                    val tcpIn = client.inputStream
                    val tcpOut = client.outputStream

                    launch {
                        val buf = ByteArray(2048)
                        var r = 0
                        while (isActive && btIn.read(buf).also { r = it } != -1) {
                            if (r > 0) { tcpOut.write(buf, 0, r); tcpOut.flush() }
                        }
                    }

                    launch {
                        val buf = ByteArray(2048)
                        var r = 0
                        while (isActive && tcpIn.read(buf).also { r = it } != -1) {
                            if (r > 0) { btOut.write(buf, 0, r); btOut.flush() }
                        }
                    }
                } catch (e: Exception) { break }
            }
        }
    }

    private fun injectPython() {
        serviceScope.launch {
            try {
                val py = Python.getInstance()
                val prefs = getSharedPreferences("harvester_prefs", Context.MODE_PRIVATE)
                val json = JSONObject()
                json.put("freq", prefs.getInt("freq", 433000000))
                json.put("sf", prefs.getInt("sf", 8))
                json.put("cr", prefs.getInt("cr", 6))
                json.put("tx", prefs.getInt("tx", 17))
                json.put("bw", prefs.getInt("bw", 125000))
                py.getModule("rns_engine").callAttr("inject_rnode", json.toString())
            } catch (e: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        val chan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel("h", "Harvester", NotificationManager.IMPORTANCE_LOW).also { 
                getSystemService(NotificationManager::class.java).createNotificationChannel(it) 
            }
        } else ""
        startForeground(1, NotificationCompat.Builder(this, "h").setContentTitle("Palm Harvester").setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true).build())
        
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        serviceScope.launch { 
            try {
                Python.getInstance().getModule("rns_engine").callAttr("start_engine", this@HarvesterService, filesDir.absolutePath)
            } catch (e: Exception) { }
        }
    }

    fun onStatusUpdate(msg: String) { serviceStatus.postValue(msg) }
}