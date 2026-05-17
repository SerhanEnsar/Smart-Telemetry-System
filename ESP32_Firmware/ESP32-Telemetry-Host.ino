// Copyright (c) 2026 Serhan Ensar. All rights reserved.
#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

// --- KULLANICI AYARLARI ---
const char* ssid = "WIFI SSID";
const char* pass = "WIFI Password";

WebServer server(80);
unsigned long lastSeen = 0;

// Veri Yapısı
struct {
  float hr = 0, spo2 = 0;
  float ax = 0, ay = 0, az = 0;
  float gx = 0, gy = 0, gz = 0;
  String timestamp = "---";
} watch;

// ESP32 açıldığından beri geçen süreyi hesaplar (00:00:00)
String getRunningTime() {
  unsigned long s = millis() / 1000;
  int h = s / 3600;
  int m = (s % 3600) / 60;
  int sec = s % 60;
  char buf[10];
  sprintf(buf, "%02d:%02d:%02d", h, m, sec);
  return String(buf);
}

// Nextion'a komut gönderir
void sendNextion(String cmd) {
  Serial2.print(cmd);
  Serial2.write(0xff);
  Serial2.write(0xff);
  Serial2.write(0xff);
}

// --- WEB TASARIMI (ESKİ GÖRÜNÜM KORUNDU) ---
const char index_html[] PROGMEM = R"rawliteral(
<!DOCTYPE html><html><head><meta charset="UTF-8"><title>ESP32 Telemetry</title>
<style>
  body{font-family:Arial;padding:30px;background:#f5f5f5;text-align:center;}
  .card{background:white;padding:20px;border-radius:12px;width:450px;box-shadow:0 2px 10px rgba(0,0,0,0.1);font-size:20px;line-height:1.6;text-align:left;display:inline-block;}
  .status{font-weight:bold;padding:2px 8px;border-radius:5px;float:right;}
  .online{background:#d4edda;color:#155724;}
  .offline{background:#f8d7da;color:#721c24;}
  button{margin-top:20px;padding:10px;font-size:16px;cursor:pointer;}
</style></head>
<body>
  <h2>Live Sensor Dashboard</h2>
  <div class="card">
    Saat Durumu: <span id="st" class="status offline">OFFLINE</span><br><hr>
    <b>HR:</b> <span id="hr">--</span> bpm<br>
    <b>SpO2:</b> <span id="sp">--</span> %<br>
    <b>Accel:</b> <small id="ac">--</small><br>
    <b>Gyro:</b> <small id="gy">--</small><br>
    <b>Süre:</b> <span id="dr">--</span><br>
    <b>Zaman:</b> <span id="tm">--</span>
  </div><br>
  <button onclick="downloadCSV()">Download Log CSV</button>
  <script>
    let logs = [];
    setInterval(() => {
      fetch('/data').then(r=>r.json()).then(d=>{
        document.getElementById('hr').innerText = d.hr;
        document.getElementById('sp').innerText = d.spo2;
        document.getElementById('ac').innerText = d.ax.toFixed(2)+", "+d.ay.toFixed(2)+", "+d.az.toFixed(2);
        document.getElementById('gy').innerText = d.gx.toFixed(2)+", "+d.gy.toFixed(2)+", "+d.gz.toFixed(2);
        document.getElementById('dr').innerText = d.duration;
        document.getElementById('tm').innerText = d.timestamp;
        const s = document.getElementById('st');
        s.innerText = d.isOnline ? "ONLINE" : "OFFLINE";
        s.className = "status " + (d.isOnline ? "online" : "offline");
        if(d.isOnline) logs.push(d);
      });
    }, 1000);
    function downloadCSV(){
      let csv = "time,duration,hr,spo2,ax,ay,az,gx,gy,gz\n";
      logs.forEach(r=>{ csv += `${r.timestamp},${r.duration},${r.hr},${r.spo2},${r.ax},${r.ay},${r.az},${r.gx},${r.gy},${r.gz}\n`; });
      let blob = new Blob([csv], {type:'text/csv'});
      let a = document.createElement('a'); a.href = URL.createObjectURL(blob);
      a.download = "telemetry_log.csv"; a.click();
    }
  </script>
</body></html>
)rawliteral";

void setup() {
  Serial.begin(115200);
  Serial2.begin(9600, SERIAL_8N1, 16, 17); // Nextion RX:16, TX:17
  
  WiFi.begin(ssid, pass);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println("\nBAGLANDI! IP: " + WiFi.localIP().toString());

  server.on("/", [](){ server.send_P(200, "text/html", index_html); });
  
  server.on("/ping", [](){
    lastSeen = millis();
    server.send(200, "text/plain", "PONG");
    sendNextion("t_stat.txt=\"BAGLI\"");
    sendNextion("t_stat.pco=2016");
  });

  server.on("/data", [](){
    StaticJsonDocument<1024> doc;
    doc["hr"] = (int)watch.hr; doc["spo2"] = (int)watch.spo2;
    doc["ax"] = watch.ax; doc["ay"] = watch.ay; doc["az"] = watch.az;
    doc["gx"] = watch.gx; doc["gy"] = watch.gy; doc["gz"] = watch.gz;
    doc["duration"] = getRunningTime();
    doc["timestamp"] = watch.timestamp;
    doc["isOnline"] = (millis() - lastSeen < 10000);
    String o; serializeJson(doc, o);
    server.send(200, "application/json", o);
  });

  server.on("/sensor", HTTP_POST, [](){
    StaticJsonDocument<1024> doc;
    deserializeJson(doc, server.arg("plain"));
    watch.hr = doc["hr"]; watch.spo2 = doc["spo2"];
    watch.ax = doc["accel"]["x"]; watch.ay = doc["accel"]["y"]; watch.az = doc["accel"]["z"];
    watch.gx = doc["gyro"]["x"]; watch.gy = doc["gyro"]["y"]; watch.gz = doc["gyro"]["z"];
    watch.timestamp = doc["timestamp"].as<String>();
    lastSeen = millis();
    server.send(200, "text/plain", "OK");
    
    updateNextionAll();
  });

  server.begin();
}

void updateNextionAll() {
  String dur = getRunningTime();
  
  // 1. Ekran ve Global Değerler
  sendNextion("j_hr.maxval=150"); // Sağdaki barın max değerini 150 yap
  sendNextion("j_hr.val=" + String((int)watch.hr));
  sendNextion("t_hr.txt=\"" + String((int)watch.hr) + "\"");
  sendNextion("j_spo2.val=" + String((int)watch.spo2));
  sendNextion("t_spo2.txt=\"" + String((int)watch.spo2) + "\"");
  sendNextion("t_dur.txt=\"" + dur + "\"");
  
  String accS = "X:" + String(watch.ax,1) + " Y:" + String(watch.ay,1) + " Z:" + String(watch.az,1);
  String gyrS = "X:" + String(watch.gx,1) + " Y:" + String(watch.gy,1) + " Z:" + String(watch.gz,1);
  sendNextion("t_acc.txt=\"" + accS + "\"");
  sendNextion("t_gyro.txt=\"" + gyrS + "\"");
  
  sendNextion("t_stat.txt=\"BAGLI\"");
  sendNextion("t_stat.pco=2016");

  // --- GRAFİKLER (WAVEFORM) ---
  // İvme Grafiği (ID: 8)
  sendNextion("add 8,0," + String(map((int)(watch.ax*10), -150, 150, 0, 255)));
  sendNextion("add 8,1," + String(map((int)(watch.ay*10), -150, 150, 0, 255)));
  sendNextion("add 8,2," + String(map((int)(watch.az*10), -150, 150, 0, 255)));

  // Jiroskop Grafiği (ID: 7)
  sendNextion("add 7,0," + String(map((int)(watch.gx*10), -100, 100, 0, 255)));
  sendNextion("add 7,1," + String(map((int)(watch.gy*10), -100, 100, 0, 255)));
  sendNextion("add 7,2," + String(map((int)(watch.gz*10), -100, 100, 0, 255)));
}

void loop() {
  server.handleClient();
  if (millis() - lastSeen > 10000) {
    sendNextion("t_stat.txt=\"KOPUK\"");
    sendNextion("t_stat.pco=63488");
  }
}
