// Copyright (c) 2026 Serhan Ensar. All rights reserved.
package com.serhanensar.hmisensordata.presentation

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.*
import com.serhanensar.hmisensordata.NetworkClient
import com.serhanensar.hmisensordata.SensorService
import com.serhanensar.hmisensordata.presentation.theme.HMISensorDataTheme
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    private var isConnectedToESP by mutableStateOf(false)
    private var watchIp by mutableStateOf("Bilinmiyor")
    private var statusMsg by mutableStateOf("Hazır")
    private var sensorOk by mutableStateOf(false)
    private var activityOk by mutableStateOf(false)

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusMsg = intent?.getStringExtra("status") ?: "Yayınlanıyor..."
        }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateStates() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter("SENSOR_STATUS_UPDATE")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(statusReceiver, filter)

        updateStates()

        setContent {
            HMISensorDataTheme {
                LaunchedEffect(Unit) {
                    while(true) {
                        watchIp = getLocalIpAddress()
                        NetworkClient.checkConnection { connected -> isConnectedToESP = connected }
                        delay(3000)
                    }
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BAĞLANTI KONTROL", style = MaterialTheme.typography.titleSmall)
                        
                        Text("SAAT IP: $watchIp", style = MaterialTheme.typography.labelSmall, color = Color.Cyan)
                        
                        Row {
                            Text("ESP32 (192.168.165.50): ", style = MaterialTheme.typography.labelSmall)
                            Text(if (isConnectedToESP) "BAĞLI" else "KOPUK", color = if (isConnectedToESP) Color.Green else Color.Red)
                        }

                        Spacer(Modifier.height(4.dp))
                        if (!sensorOk) Button(onClick = { permLauncher.launch(Manifest.permission.BODY_SENSORS) }) { Text("Sensör İzni") }
                        else if (!activityOk) Button(onClick = { permLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Aktivite İzni") }
                        else Button(onClick = { startService(Intent(this@MainActivity, SensorService::class.java)) }) { Text("BAŞLAT") }

                        Text(statusMsg, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "---"
                    }
                }
            }
        } catch (e: Exception) {}
        return "IP Yok"
    }

    private fun updateStates() {
        sensorOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        activityOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) {}
    }
}
