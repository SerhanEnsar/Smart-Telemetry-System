# WearOS to ESP32 Sensor Data & Nextion Dashboard

This project streams real-time sensor data from a WearOS smartwatch to an ESP32 server over WiFi, which then displays the data on both a web dashboard and a Nextion HMI display.

## 🚀 Features
- **Real-time Streaming:** Heart Rate, SpO2, Accelerometer, and Gyroscope data sent every 1 second.
- **ESP32 Web Dashboard:** View live data and download logs as CSV via a browser.
- **Nextion HMI Support:** Visual gauges for vitals and real-time waveforms for IMU data.
- **Background Service:** Uses a Foreground Service on WearOS to ensure continuous data transmission.
- **Connection Monitoring:** Live "ONLINE/OFFLINE" status tracking on all interfaces.

## 📁 Project Structure
- `wearos-app/`: Android Studio project for the WearOS application.
- `esp32-firmware/`: Arduino sketch for the ESP32 server and Nextion driver.
- `nextion-display/`: Nextion Editor project file (`.hmi`) for the display UI.

## 🛠️ Setup Instructions
1. **ESP32:** Upload the code in `esp32-firmware/` and note the IP address from the Serial Monitor.
2. **WearOS:** Update `NetworkClient.kt` with the ESP32's IP address and install the app on your watch.
3. **Hardware:** Connect your Nextion display to ESP32 (TX->16, RX->17).

## 📡 JSON Data Format
Data is sent via HTTP POST in the following format:
```json
{
  "timestamp": "yyyy-MM-dd HH:mm:ss",
  "duration": "HH:mm:ss",
  "hr": 75,
  "spo2": 98,
  "accel": { "x": 0.1, "y": 0.2, "z": 9.8 },
  "gyro": { "x": 0.01, "y": 0.02, "z": 0.03 }
}
```

## 📜 License
MIT License
