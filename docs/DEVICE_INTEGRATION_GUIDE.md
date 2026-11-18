# HYKON Platform — Device Integration Guide (for ESP32 / inverter firmware developers)

Give this document to whoever writes the ESP32 / inverter firmware. Follow it and the device's
readings appear on the HYKON website automatically. You do not need to know anything about the
website, Spring Boot or the database.

## 1. What the device must do

1. Connect to Wi-Fi and get the correct time from the internet (NTP).
2. Every **5 seconds** (never slower than 10 seconds), send one JSON reading over HTTPS.
3. Send the device's own **Key ID** and **Secret** in two headers.
4. If sending fails, keep the reading and **retry the same reading** (same sequence number).

The website shows a device as ONLINE only if its last reading is less than **30 seconds** old.

## 2. What you receive from the website operator (per device)

| Item | Example | Notes |
|---|---|---|
| Server address | `https://api.example.com` | Local testing: `http://<laptop-ip>:8080` |
| Device Code | `halo1200-100` | Must be sent in every reading, exactly as given |
| Key ID | `3f2a…` (UUID) | Sent as a header |
| Secret | long random text | Shown **once**; store it in the device's protected storage; never in Git |

Each device has its own Key ID + Secret. Never share one between devices.

## 3. The request

```
POST {server}/api/v2/telemetry/readings
Content-Type: application/json
X-Device-Key-Id: <Key ID>
X-Device-Secret: <Secret>
```

### Body (schema version 2 — works for any inverter model)

```json
{
  "schemaVersion": 2,
  "deviceCode": "halo1200-100",
  "bootId": "6f1c2a3e-8b1d-4c9a-9e0f-1a2b3c4d5e6f",
  "sequence": 1,
  "measuredAt": "2026-11-18T10:15:30Z",
  "uptimeMs": 5000,
  "simulated": false,
  "operatingMode": "LINE",
  "firmwareVersion": "2.0.1",
  "model": "Halo 1200",
  "measurements": [
    { "key": "battery_voltage_v",   "value": 52.4,  "unit": "V" },
    { "key": "battery_current_a",   "value": -3.1,  "unit": "A" },
    { "key": "battery_soc_percent", "value": 87,    "unit": "%" },
    { "key": "grid_present",        "value": 1 },
    { "key": "grid_voltage_v",      "value": 230.2, "unit": "V" },
    { "key": "grid_frequency_hz",   "value": 50.0,  "unit": "Hz" },
    { "key": "grid_input_power_w",  "value": 420,   "unit": "W" },
    { "key": "load_voltage_v",      "value": 229.8, "unit": "V" },
    { "key": "load_current_a",      "value": 2.8,   "unit": "A" },
    { "key": "load_output_power_w", "value": 640,   "unit": "W" },
    { "key": "load_percent",        "value": 53,    "unit": "%" },
    { "key": "heatsink_temp_c",     "value": 41.5,  "unit": "C" }
  ]
}
```

### Field rules

| Field | Rule |
|---|---|
| `schemaVersion` | Always `2`. |
| `deviceCode` | Exactly the Device Code you were given (max 100 chars). |
| `bootId` | A random UUID created **once each time the device starts**. Keep it until the next restart. |
| `sequence` | Whole number. **1 for the first reading after each start**, then 2, 3, 4… (+1 each new reading). |
| `measuredAt` | UTC time of the reading, ISO-8601 (`...Z`). Optional but strongly recommended. Must not be in the future by more than 5 minutes or older than 24 hours. Must not go backwards. |
| `uptimeMs` | Milliseconds since the device started (0 or more). Must not go backwards within a boot. |
| `simulated` | `true` for test/fake data, `false` for real measurements. |
| `operatingMode` | One of `LINE`, `BATTERY`, `BYPASS`, `STANDBY`, `FAULT`, `UNKNOWN` (optional, default `UNKNOWN`). |
| `firmwareVersion` | Optional, up to 40 characters. |
| `model` | Optional, up to 60 characters (inverter model). |
| `measurements` | 1 to 100 items. Each name (`key`) may appear **once** per reading. |

### Measurement items

- `key`: lowercase letters, digits and `_`; starts with a letter; 2 to 48 characters.
- `value`: a number. Use `null` when the value is **not available** (never send 0 for unknown).
  True/false values are sent as `1` / `0`.
- `unit`: optional, up to 16 characters from letters, digits and `% / . _ -`.

### Standard names (shown on the existing dashboard)

| Key | Meaning | Allowed values |
|---|---|---|
| `battery_voltage_v` | Battery voltage | 0 or more |
| `battery_current_a` | Battery current (+ charging, − discharging) | any |
| `battery_soc_percent` | State of charge | 0 – 100 |
| `grid_present` | Mains present | 1 or 0 |
| `grid_voltage_v` | Grid voltage | 0 or more |
| `grid_frequency_hz` | Grid frequency | 0 or more |
| `grid_input_power_w` | Grid input power | any |
| `load_voltage_v` | Output voltage | 0 or more |
| `load_current_a` | Output current | 0 or more |
| `load_output_power_w` | Output power | 0 or more |
| `load_percent` | Load percentage | 0 or more |

**Any other key** (for example `pv_power_w`, `heatsink_temp_c`, `fan_rpm`) is accepted and stored
with the reading. Use clear lowercase names with the unit at the end.
Omit a standard measurement that the inverter does not provide, or send it as `null`.

## 4. The reply

| Code | Meaning | What the device should do |
|---|---|---|
| `200` "Telemetry accepted" | Stored | Send the next reading (sequence + 1). |
| `200` "Telemetry retry acknowledged" | That sequence was already stored | Treat as success; move on. |
| `400` | Invalid data (bad name, missing field, out-of-range value, bad time) | Fix the firmware; do not retry the same bytes forever. |
| `401` | Wrong or revoked Key ID / Secret | Stop and report; ask the operator for a new key. |
| `403` | Device is inactive, or Device Code does not match the key | Stop and report. |
| `409` | Sequence/time moved backwards, or the same sequence was sent with different content | See rule 5.1 below. |
| `5xx` / no network | Server or network problem | Keep the reading and retry (wait 5 s, then 10 s, 20 s… up to 60 s). |

## 5. Rules that avoid problems

1. **Retries must be identical.** If a send fails, resend the same JSON with the same `sequence`.
   Never change the content of a sequence number that may have been received. A `409` for
   "different content" means this rule was broken.
2. **After every restart** create a new `bootId` and start at `sequence = 1`. The first reading of
   a replacement boot must include a correct `measuredAt`.
3. Do not send faster than every 1 second. Recommended: every 5 seconds.
4. Check the server's HTTPS certificate on the device (load the root CA). Do not turn
   certificate checking off in production.
5. Keep the Secret in non-volatile protected storage; allow the operator to replace it.
6. Send `simulated: true` for anything that is not a real measurement.

## 6. Quick test with curl (before writing firmware)

```bash
curl -X POST http://<server>:8080/api/v2/telemetry/readings \
  -H "Content-Type: application/json" \
  -H "X-Device-Key-Id: <KEY_ID>" -H "X-Device-Secret: <SECRET>" \
  -d '{"schemaVersion":2,"deviceCode":"<DEVICE_CODE>","bootId":"11111111-2222-4333-8444-555555555555","sequence":1,"uptimeMs":5000,"simulated":true,"operatingMode":"LINE","measurements":[{"key":"battery_voltage_v","value":52.4,"unit":"V"}]}'
```

Expected: `{"success":true,"message":"Telemetry accepted",...}`

## 7. Reference code (Arduino / PlatformIO, ESP32)

```cpp
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>

// Set from protected storage / secrets.h (never commit real values)
const char* SERVER_URL  = "https://api.example.com/api/v2/telemetry/readings";
const char* DEVICE_CODE = "halo1200-100";
const char* KEY_ID      = "<key id>";
const char* SECRET      = "<secret>";
extern const char* ROOT_CA;               // server's root certificate (PEM)

// bootId: generate a random UUID at startup; sequence starts at 1 and +1 per new reading.
bool sendReading(const String& json) {
  WiFiClientSecure client;
  client.setCACert(ROOT_CA);               // certificate checking ON
  HTTPClient http;
  http.begin(client, SERVER_URL);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key-Id", KEY_ID);
  http.addHeader("X-Device-Secret", SECRET);
  int code = http.POST(json);
  http.end();
  return code == 200;                      // on false: keep the SAME json and retry later
}
```

Build the JSON with the ArduinoJson library, exactly as in section 3.

## 8. Operator checklist (website side)

1. Register the device on the website (Device ID, serial number, distributor, branch).
2. Issue the device key: `POST /api/v1/management/devices/{deviceId}/telemetry-credential`
   with an admin login. The Secret is shown once. A one-click button on the website is on the plan.
3. Give the firmware developer: server address, Device Code, Key ID, Secret.
4. The customer claims the device on the website with Device ID + serial number.
5. Check the Device page: status ONLINE, readings updating.

## 9. Compatibility

The original format (`POST /api/v1/telemetry/readings`, schema version 1, fixed battery/grid/load
objects) still works unchanged. New firmware should use version 2.
