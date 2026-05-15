package com.serhanensar.hmisensordata

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val TAG = "NetworkClient"
    
    // ESP32'nin seri monitöründe yazan IP adresini buraya yazın:
    var espIp: String = "10.55.210.89"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    fun sendSensorData(data: SensorData, callback: (Boolean, String) -> Unit) {
        val json = gson.toJson(data)
        val request = Request.Builder()
            .url("http://$espIp/sensor")
            .post(json.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, "Hata") }
            override fun onResponse(call: Call, response: Response) { 
                callback(response.isSuccessful, if(response.isSuccessful) "OK" else "Hata") 
            }
        })
    }

    fun checkConnection(onResult: (Boolean) -> Unit) {
        val request = Request.Builder().url("http://$espIp/ping").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
            override fun onResponse(call: Call, response: Response) { onResult(response.isSuccessful) }
        })
    }
}
