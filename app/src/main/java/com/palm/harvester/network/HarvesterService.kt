package com.palm.harvester.network

import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.ServerSocket
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
            val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
            device?.let { startBridge(it) }
        }
        return START_STICKY
    }

    private fun startBridge(device: BluetoothDevice) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                onStatusUpdate("Connecting BT...")
                val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                btSocket = m.invoke(device, 1) as BluetoothSocket
                btSocket?.connect()
                
                tcpServer = ServerSocket()
                tcpServer?.reuseAddress = true
                tcpServer?.bind(InetSocketAddress("127.0.0.1", 7633))
                
                delay(500)
                injectPython()

                val client = tcpServer?.accept() ?: return@launch
                val btIn = btSocket!!.inputStream; val btOut = btSocket!!.outputStream
                val tcpIn = client.inputStream; val tcpOut = client.outputStream

                onStatusUpdate("Mesh Ready")

                launch { val b = ByteArray(1024); var r: Int; while(isActive && btIn.read(b).also{r=it}!=-1) { tcpOut.write(b,0,r); tcpOut.flush() } }
                launch { val b = ByteArray(1024); var r: Int; while(isActive && tcpIn.read(b).also{r=it}!=-1) { btOut.write(b,0,r); btOut.flush() } }
            } catch (e: Exception) { onStatusUpdate("Bridge Fail") }
        }
    }

    private fun injectPython() {
        serviceScope.launch {
            val prefs = getSharedPreferences("radio", Context.MODE_PRIVATE)
            val json = JSONObject().apply { 
                put("freq", prefs.getInt("freq", 433000000))
                put("sf", prefs.getInt("sf", 8))
            }
            Python.getInstance().getModule("rns_engine").callAttr("inject_rnode", json.toString())
        }
    }

    override fun onCreate() {
        super.onCreate()
        val chan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel("h", "Harvester", NotificationManager.IMPORTANCE_LOW).also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        } else ""
        startForeground(1, NotificationCompat.Builder(this, "h").setContentTitle("Palm Harvester").setSmallIcon(android.R.drawable.stat_notify_sync).build())
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        serviceScope.launch { Python.getInstance().getModule("rns_engine").callAttr("start_engine", this@HarvesterService, filesDir.absolutePath) }
    }

    fun onStatusUpdate(msg: String) { serviceStatus.postValue(msg) }
}