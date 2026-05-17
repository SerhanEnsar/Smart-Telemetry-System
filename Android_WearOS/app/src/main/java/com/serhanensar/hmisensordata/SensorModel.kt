// Copyright (c) 2026 Serhan Ensar. All rights reserved.
package com.serhanensar.hmisensordata

data class SensorData(
    val timestamp: String,
    val duration: String,
    val hr: Int,
    val spo2: Int,
    val accel: Vector3,
    val gyro: Vector3
)

data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
)
