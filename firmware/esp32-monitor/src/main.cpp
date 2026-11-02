#include <Arduino.h>

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("ESP32 monitoring project: serial test started.");
}

void loop() {
    Serial.printf(
        "ESP32 is running. Uptime: %lu seconds. Wi-Fi and HTTP not configured yet.\n",
        millis() / 1000UL
    );
    delay(2000);
}