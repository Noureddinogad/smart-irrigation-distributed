#include <WiFi.h>
#include <HTTPClient.h>
#include <DHT.h>
#include <Ticker.h>

/********************  WIFI  ********************/
const char* ssid = "La_Fibre_dOrange_8BD2";
const char* pass = "20033002";

/********************  JAVA HTTP INGESTION (CHANGE IP)  ********************/
const char* SERVER_URL ="http://192.168.11.186:8080/api/readings";


/********************  DEVICE ID  ********************/
const char* DEVICE_ID = "esp32-01";

/********************  HARDWARE PINS (ESP32 GPIO)  ********************/
#define SOIL_SENSOR_PIN    34
#define WATER_LEVEL_PIN    35
#define RELAY_PIN          25
#define RAIN_PIN           26

#define DHT_PIN            33
#define DHT_TYPE           DHT11

#define LED_PIN            2

DHT dht(DHT_PIN, DHT_TYPE);

/********************  RAIN SENSOR  ********************/
// Most modules: LOW means raining
#define RAIN_ACTIVE_LOW 1
bool isRaining() {
  int v = digitalRead(RAIN_PIN);
  return (RAIN_ACTIVE_LOW ? (v == LOW) : (v == HIGH));
}

/********************  GLOBAL STATE  ********************/
Ticker tBlink, tSensors, tDht, tPost;

volatile bool pumpState = false;   // REAL relay state (applied from server command)

int moisturePct = 0;
int waterPct    = 0;
bool raining    = false;

float dhtHum  = NAN;
float dhtTemp = NAN;

/********************  BLINK  ********************/
void blinkLED() {
  static bool state = false;
  state = !state;
  digitalWrite(LED_PIN, state ? HIGH : LOW);
}

/********************  READ SENSORS ONLY (NO LOGIC)  ********************/
void readSensorsOnly() {
  int moistureRaw = analogRead(SOIL_SENSOR_PIN);
  moisturePct = map(moistureRaw, 4095, 0, 0, 100);
  moisturePct = constrain(moisturePct, 0, 100);

  int waterRaw = analogRead(WATER_LEVEL_PIN);
  waterPct = map(waterRaw, 0, 4095, 0, 100);
  waterPct = constrain(waterPct, 0, 100);

  raining = isRaining();
}

/********************  READ DHT  ********************/
void readDHT() {
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (!isnan(h)) dhtHum = h;
  if (!isnan(t)) dhtTemp = t;
}

/********************  APPLY PUMP COMMAND  ********************/
void applyPumpCmd(bool on) {
  pumpState = on;
  digitalWrite(RELAY_PIN, pumpState ? HIGH : LOW);
}

/********************  SUPER SIMPLE JSON PARSER (pump_cmd only)  ********************/
/*
  Expected server response examples:
    {"status":"ok","pump_cmd":true}
    {"pump_cmd":false}
  If pump_cmd not present, we do nothing (keep current state).
*/
bool tryParsePumpCmd(const String& body, bool &outCmd) {
  int k = body.indexOf("\"pump_cmd\"");
  if (k < 0) return false;

  int colon = body.indexOf(':', k);
  if (colon < 0) return false;

  // Skip spaces
  int i = colon + 1;
  while (i < (int)body.length() && (body[i] == ' ' || body[i] == '\n' || body[i] == '\r' || body[i] == '\t')) i++;

  if (body.startsWith("true", i))  { outCmd = true;  return true; }
  if (body.startsWith("false", i)) { outCmd = false; return true; }

  return false;
}

/********************  POST JSON TO JAVA SERVER  ********************/
void postReadings() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP] WiFi not connected, skip POST");
    return;
  }

  HTTPClient http;
  http.begin(SERVER_URL);
  http.addHeader("Content-Type", "application/json");

  // IMPORTANT: request keys must remain EXACTLY as agreed
  String json = "{";
  json += "\"device\":\"" + String(DEVICE_ID) + "\",";
  json += "\"soil\":" + String(moisturePct) + ",";
  json += "\"water_tank\":" + String(waterPct) + ",";
  json += "\"raining\":" + String(raining ? "true" : "false") + ",";
  json += "\"pump\":" + String(pumpState ? "true" : "false") + ",";
  json += "\"temp_c\":" + (isnan(dhtTemp) ? "null" : String(dhtTemp, 1)) + ",";
  json += "\"humidity\":" + (isnan(dhtHum) ? "null" : String(dhtHum, 1));
  json += "}";

  int code = http.POST(json);

  Serial.print("[HTTP] code="); Serial.println(code);

  if (code > 0) {
    String body = http.getString();
    Serial.print("[HTTP] response: "); Serial.println(body);

    bool cmd;
    if (tryParsePumpCmd(body, cmd)) {
      Serial.print("[CMD] pump_cmd parsed = ");
      Serial.println(cmd ? "true" : "false");
      applyPumpCmd(cmd);
    } else {
      Serial.println("[CMD] no pump_cmd in response (keeping current pump state)");
    }
  } else {
    Serial.println("[HTTP] POST failed");
  }

  http.end();
}

/********************  SETUP + LOOP  ********************/
void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  pinMode(RELAY_PIN, OUTPUT);
  applyPumpCmd(false); // safe default OFF at boot

  pinMode(RAIN_PIN, INPUT_PULLUP);

  dht.begin();

  Serial.println("Connecting WiFi...");
  WiFi.begin(ssid, pass);
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected!");
  Serial.print("ESP IP: ");
  Serial.println(WiFi.localIP());

  // Timers (same rhythm)
  tBlink.attach(0.5, blinkLED);
  tSensors.attach(3.0, readSensorsOnly);
  tDht.attach(5.0, readDHT);
  tPost.attach(5.0, postReadings);
}

void loop() {
  // Ticker does the work
}
