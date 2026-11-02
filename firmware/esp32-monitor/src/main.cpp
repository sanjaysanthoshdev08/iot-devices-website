#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <esp_system.h>
#include <cstring>
#include "secrets.h"

struct NetworkSettings {
    uint32_t version;
    char ssid[33];
    char password[64];
    char backend[96];
};

Preferences preferences;
WebServer portal(80);
NetworkSettings savedSettings{};
NetworkSettings candidate{};

bool storageReady = false;
bool hasSettings = false;
bool setupActive = false;
bool candidateQueued = false;
bool candidateConnecting = false;
bool recoveryOffered = false;
bool wasConnected = false;
bool buttonHeld = false;

uint32_t setupStarted = 0;
uint32_t candidateStarted = 0;
uint32_t disconnectedAt = 0;
uint32_t buttonStarted = 0;

String setupToken;

const char PAGE[] PROGMEM = R"PAGE(
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>HYKON Device Setup</title>
<style>
body{font:16px system-ui;background:#10151c;color:#edf2f7;
max-width:480px;margin:40px auto;padding:20px}
label{display:block;margin-top:18px}
input,button{box-sizing:border-box;width:100%;padding:12px;
margin-top:6px;border-radius:8px;border:1px solid #536172}
button{background:#2084ed;color:white;margin-top:24px;cursor:pointer}
small{color:#b6c2cf}h1{font-size:25px}
</style>
</head>
<body>
<h1>HYKON Device Setup</h1>
<p>Connect this device to your demonstration network.</p>
<form method="post" action="/save" autocomplete="off">
<input type="hidden" name="token" value="{{TOKEN}}">
<label>2.4 GHz Wi-Fi name
<input name="ssid" maxlength="32" required></label>
<label>Wi-Fi password
<input name="password" type="password" minlength="8" maxlength="63"
autocomplete="new-password" required></label>
<label>Laptop backend address
<input name="backend" maxlength="95"
placeholder="http://10.199.40.107:8080" required></label>
<small>Use the laptop's current local address, including http:// and port.
This setup supports trusted local demonstrations.</small>
<button type="submit">Connect and save</button>
</form>
<p>Settings are saved only after Wi-Fi connects successfully.</p>
</body>
</html>
)PAGE";

bool validBackend(const String& value) {
    if (!value.startsWith("http://") || value.length() >= 96) return false;

    String address = value.substring(7);
    int separator = address.indexOf(':');
    if (separator <= 0) return false;

    String host = address.substring(0, separator);
    String portText = address.substring(separator + 1);
    if (portText.isEmpty() || portText.length() > 5) return false;

    for (size_t index = 0; index < portText.length(); index++) {
        if (portText[index] < '0' || portText[index] > '9') return false;
    }

    long port = portText.toInt();
    if (port < 1 || port > 65535) return false;

    IPAddress ip;
    if (!ip.fromString(host)) return false;

    return ip[0] == 10
        || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31)
        || (ip[0] == 192 && ip[1] == 168);
}

bool validSettings(const NetworkSettings& value) {
    if (value.version != 1
        || !std::memchr(value.ssid, '\0', sizeof(value.ssid))
        || !std::memchr(value.password, '\0', sizeof(value.password))
        || !std::memchr(value.backend, '\0', sizeof(value.backend))) {
        return false;
    }

    size_t passwordLength = std::strlen(value.password);
    return value.ssid[0] != '\0'
        && passwordLength >= 8 && passwordLength <= 63
        && validBackend(String(value.backend));
}

bool setupRequestAllowed() {
    if (!setupActive || portal.client().localIP() != WiFi.softAPIP()) {
        portal.send(403, "text/plain", "Connect to the device setup Wi-Fi.");
        return false;
    }
    portal.sendHeader("Cache-Control", "no-store");
    return true;
}

void closeSetup() {
    portal.stop();
    WiFi.softAPdisconnect(true);
    setupActive = false;
    Serial.println("Setup network closed.");
}

void openSetup() {
    if (setupActive) return;

    WiFi.mode(WIFI_AP_STA);
    String name = "HYKON-Setup-" + String(
        static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
    name.toUpperCase();

    if (!WiFi.softAP(name.c_str(), SETUP_AP_PASSWORD)) {
        Serial.println("Could not start setup Wi-Fi.");
        return;
    }

    char token[33];
    snprintf(token, sizeof(token), "%08lx%08lx%08lx%08lx",
        static_cast<unsigned long>(esp_random()),
        static_cast<unsigned long>(esp_random()),
        static_cast<unsigned long>(esp_random()),
        static_cast<unsigned long>(esp_random()));
    setupToken = token;

    setupActive = true;
    recoveryOffered = true;
    setupStarted = millis();
    portal.begin();

    Serial.print("Setup Wi-Fi: ");
    Serial.println(name);
    Serial.print("Setup page: http://");
    Serial.println(WiFi.softAPIP());
    Serial.println("Use your locally chosen setup password.");
}

void showSetup() {
    if (!setupRequestAllowed()) return;

    String html = FPSTR(PAGE);
    html.replace("{{TOKEN}}", setupToken);
    portal.sendHeader(
        "Content-Security-Policy",
        "default-src 'none'; style-src 'unsafe-inline'; "
        "form-action 'self'; frame-ancestors 'none'");
    portal.send(200, "text/html; charset=utf-8", html);
}

void receiveSettings() {
    if (!setupRequestAllowed()) return;

    if (portal.arg("token") != setupToken) {
        portal.send(403, "text/plain", "Reload the setup page and try again.");
        return;
    }

    if (candidateQueued || candidateConnecting) {
        portal.send(409, "text/plain", "A connection attempt is already running.");
        return;
    }

    String ssid = portal.arg("ssid");
    String password = portal.arg("password");
    String backend = portal.arg("backend");
    backend.trim();

    if (ssid.isEmpty() || ssid.length() > 32
        || password.length() < 8 || password.length() > 63
        || !validBackend(backend)) {
        portal.send(400, "text/plain",
            "Check the Wi-Fi name, password length, and local backend address. "
            "Example address: http://10.199.40.107:8080");
        return;
    }

    candidate = {};
    candidate.version = 1;
    ssid.toCharArray(candidate.ssid, sizeof(candidate.ssid));
    password.toCharArray(candidate.password, sizeof(candidate.password));
    backend.toCharArray(candidate.backend, sizeof(candidate.backend));

    portal.send(202, "text/plain",
        "Connecting. Check Serial Monitor for the result. "
        "On success, reconnect your laptop to your normal Wi-Fi. "
        "On failure, reopen the setup page and try again.");
    candidateQueued = true;
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    pinMode(0, INPUT_PULLUP);

    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);

    storageReady = preferences.begin("hykon-net", false);
    if (storageReady
        && preferences.getBytesLength("settings") == sizeof(savedSettings)) {
        size_t loaded = preferences.getBytes(
            "settings", &savedSettings, sizeof(savedSettings));
        hasSettings = loaded == sizeof(savedSettings)
            && validSettings(savedSettings);
    }

    portal.on("/", HTTP_GET, showSetup);
    portal.on("/save", HTTP_POST, receiveSettings);
    portal.onNotFound([]() {
        if (setupRequestAllowed()) {
            portal.send(404, "text/plain", "Page not found.");
        }
    });

    disconnectedAt = millis();
    if (hasSettings) {
        Serial.println("Connecting using saved Wi-Fi settings.");
        WiFi.begin(savedSettings.ssid, savedSettings.password);
    } else {
        Serial.println("No saved Wi-Fi settings.");
        openSetup();
    }

    Serial.println(
        "After startup, hold BOOT for 3 seconds to reopen setup. "
        "Do not hold BOOT while powering on.");
    Serial.println("Telemetry sending is not configured yet.");
}

void loop() {
    uint32_t now = millis();

    if (setupActive) portal.handleClient();

    if (digitalRead(0) == LOW && !setupActive) {
        if (!buttonHeld) {
            buttonHeld = true;
            buttonStarted = now;
        } else if (now - buttonStarted >= 3000) {
            buttonHeld = false;
            openSetup();
        }
    } else {
        buttonHeld = false;
    }

    if (candidateQueued) {
        candidateQueued = false;
        candidateConnecting = true;
        candidateStarted = now;
        WiFi.disconnect();
        WiFi.begin(candidate.ssid, candidate.password);
        Serial.println("Testing the entered Wi-Fi settings.");
    }

    if (candidateConnecting && now - candidateStarted >= 500) {
        if (WiFi.status() == WL_CONNECTED) {
            candidateConnecting = false;

            if (storageReady
                && preferences.putBytes(
                    "settings", &candidate, sizeof(candidate))
                    == sizeof(candidate)) {
                savedSettings = candidate;
                hasSettings = true;
                candidate = {};
                Serial.println("Wi-Fi connected; settings saved.");
                Serial.print("ESP32 address: ");
                Serial.println(WiFi.localIP());
                closeSetup();
            } else {
                candidate = {};
                Serial.println(
                    "Wi-Fi connected, but saving failed. Setup remains open.");
            }
        } else if (now - candidateStarted >= 20000) {
            candidateConnecting = false;
            candidate = {};
            WiFi.disconnect();
            if (hasSettings) {
                WiFi.begin(savedSettings.ssid, savedSettings.password);
            }
            Serial.println(
                "Connection failed. Saved settings unchanged. Try setup again.");
        }
    }

    bool connected = WiFi.status() == WL_CONNECTED;
    if (connected && !wasConnected) {
        Serial.print("Wi-Fi connected. ESP32 address: ");
        Serial.println(WiFi.localIP());
        recoveryOffered = false;
    } else if (!connected && wasConnected) {
        disconnectedAt = now;
        Serial.println("Wi-Fi disconnected; reconnecting.");
    }
    wasConnected = connected;

    if (!connected && !setupActive && !candidateConnecting
        && !recoveryOffered && now - disconnectedAt >= 20000) {
        openSetup();
    }

    if (setupActive && !candidateConnecting
        && (uint32_t)(millis() - setupStarted) >= 600000UL) {
        closeSetup();
        Serial.println("Hold BOOT for 3 seconds if setup is still needed.");
    }

    delay(5);
}