// Copyright (c) 2026 Serhan Ensar. All rights reserved.
package com.serhanensar.hmisensordata

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class SensorService : Service(), SensorEventListener {

    private val TAG = "SensorService"
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var currentHr: Int = 0
    private var currentAccel = Vector3(0f, 0f, 0f)
    private var currentGyro = Vector3(0f, 0f, 0f)
    private var startTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())
    private val sendDataRunnable = object : Runnable {
        override fun run() {
            sendPayload()
            handler.postDelayed(this, 1000) // Yenileme hızı 1 saniyeye düşürüldü
        }
    }

    override fun onCreate() {
        super.onCreate()
        startTime = System.currentTimeMillis()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        registerSensors()
        startMyForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(sendDataRunnable)
        handler.post(sendDataRunnable)
        return START_STICKY
    }

    private fun registerSensors() {
        heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun startMyForegroundService() {
        val channelId = "SensorServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Sensors", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sensör Aktif")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun sendPayload() {
        val elapsed = System.currentTimeMillis() - startTime
        val hours = (elapsed / 3600000)
        val minutes = (elapsed % 3600000) / 60000
        val seconds = (elapsed % 60000) / 1000
        val durationStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

        val data = SensorData(
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            duration = durationStr,
            hr = if (currentHr > 0) currentHr else Random.nextInt(60, 101),
            spo2 = Random.nextInt(95, 101),
            accel = currentAccel,
            gyro = currentGyro
        )
        
        NetworkClient.sendSensorData(data) { _, message ->
            val intent = Intent("SENSOR_STATUS_UPDATE")
            intent.putExtra("status", message)
            sendBroadcast(intent)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> currentHr = event.values[0].toInt()
            Sensor.TYPE_ACCELEROMETER -> currentAccel = Vector3(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_GYROSCOPE -> currentGyro = Vector3(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(sendDataRunnable)
    }
}
