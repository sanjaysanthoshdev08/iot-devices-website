#include "TelemetrySender.h"
#include "secrets.h"

#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <esp_system.h>
#include <esp_timer.h>
#include <esp_sntp.h>
#include <ctime>
#include <cstring>
#include <cstdint>

namespace {
constexpr uint64_t SAMPLE_INTERVAL_MS = 5000;
constexpr uint64_t MAX_PENDING_AGE_MS = 23ULL * 60 * 60 * 1000;

char bootId[37]{};
uint64_t sequence = 1;
uint64_t nextAttemptMs = 0;
uint64_t pendingSinceMs = 0;
uint64_t retryDelayMs = SAMPLE_INTERVAL_MS;
uint64_t lastClockMessageMs = 0;

String pendingPayload;
bool clockStarted = false;
bool clockReady = false;
bool stopped = false;

uint64_t uptimeMs() {
    return static_cast<uint64_t>(esp_timer_get_time()) / 1000ULL;
}

void stopSending(const char* reason) {
    stopped = true;
    Serial.println(reason);
    Serial.println("Telemetry paused. Keep this message for troubleshooting.");
}

void generateBootId() {
    uint8_t bytes[16];
    esp_fill_random(bytes, sizeof(bytes));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    snprintf(
        bootId, sizeof(bootId),
        "%02x%02x%02x%02x-%02x%02x-%02x%02x-"
        "%02x%02x-%02x%02x%02x%02x%02x%02x",
        bytes[0], bytes[1], bytes[2], bytes[3],
        bytes[4], bytes[5], bytes[6], bytes[7],
        bytes[8], bytes[9], bytes[10], bytes[11],
        bytes[12], bytes[13], bytes[14], bytes[15]
    );
}

bool prepareSample(uint64_t now) {
    time_t measured = time(nullptr);
    struct tm utc{};

    if (measured < 1704067200 || gmtime_r(&measured, &utc) == nullptr) {
        stopSending("Clock is invalid; no fabricated timestamp was sent.");
        return false;
    }

    char timestamp[25]{};
    if (strftime(
            timestamp, sizeof(timestamp),
            "%Y-%m-%dT%H:%M:%SZ", &utc) == 0) {
        stopSending("Could not format the measurement timestamp.");
        return false;
    }

    // Demonstration values only. No inverter or sensor is being read.
    unsigned phase = static_cast<unsigned>((sequence - 1) % 21);
    float voltage = 51.0f + phase * 0.02f;
    float outputPower = 580.0f + phase * 2.0f;

    char payload[1200];
    int length = snprintf(
        payload, sizeof(payload),
        "{"
        "\"schemaVersion\":1,"
        "\"deviceCode\":\"%s\","
        "\"bootId\":\"%s\","
        "\"sequence\":%llu,"
        "\"measuredAt\":\"%s\","
        "\"uptimeMs\":%llu,"
        "\"simulated\":true,"
        "\"operatingMode\":\"LINE\","
        "\"battery\":{"
            "\"voltageV\":%.2f,"
            "\"currentA\":2.0,"
            "\"socPercent\":80.0"
        "},"
        "\"grid\":{"
            "\"present\":true,"
            "\"voltageV\":230.0,"
            "\"frequencyHz\":50.0,"
            "\"inputPowerW\":750.0"
        "},"
        "\"load\":{"
            "\"voltageV\":230.0,"
            "\"currentA\":null,"
            "\"outputPowerW\":%.2f,"
            "\"loadPercent\":null"
        "}"
        "}",
        TELEMETRY_DEVICE_CODE,
        bootId,
        static_cast<unsigned long long>(sequence),
        timestamp,
        static_cast<unsigned long long>(now),
        voltage,
        outputPower
    );

    if (length <= 0 || static_cast<size_t>(length) >= sizeof(payload)) {
        stopSending("Telemetry payload exceeded its buffer.");
        return false;
    }

    pendingPayload = payload;
    if (pendingPayload.length() != static_cast<size_t>(length)) {
        pendingPayload = "";
        stopSending("Insufficient memory to retain a retryable sample.");
        return false;
    }

    pendingSinceMs = now;
    return true;
}

void scheduleRetry() {
    nextAttemptMs = uptimeMs() + retryDelayMs;
    retryDelayMs = retryDelayMs < 30000 ? retryDelayMs * 2 : 60000;
    Serial.println("Same sample retained; retry scheduled.");
}
}

void telemetryBegin() {
    generateBootId();

    // Provisioning currently targets this exact registered demonstration device.
    if (std::strcmp(TELEMETRY_DEVICE_CODE, "HALO1200-1") != 0
        || std::strlen(TELEMETRY_DEVICE_KEY_ID) != 36
        || std::strlen(TELEMETRY_DEVICE_SECRET) != 43) {
        stopSending("Local device credential configuration is invalid.");
        return;
    }

    Serial.println("Simulated telemetry enabled; waiting for Wi-Fi and clock sync.");
}

void telemetryTick(const char* backend, bool allowed) {
    if (stopped || !allowed || WiFi.status() != WL_CONNECTED) return;

    uint64_t now = uptimeMs();

    if (!clockStarted) {
        sntp_set_sync_status(SNTP_SYNC_STATUS_RESET);
        configTime(0, 0, "pool.ntp.org", "time.google.com");
        clockStarted = true;
        lastClockMessageMs = now;
        Serial.println("Synchronizing UTC time before the first sample.");
        return;
    }

    if (!clockReady) {
        if (sntp_get_sync_status() == SNTP_SYNC_STATUS_COMPLETED
            && time(nullptr) >= 1704067200) {
            clockReady = true;
            nextAttemptMs = now + SAMPLE_INTERVAL_MS;
            Serial.println("Clock synchronized. First simulated sample in 5 seconds.");
        } else if (now - lastClockMessageMs >= 15000) {
            lastClockMessageMs = now;
            Serial.println("Still waiting for internet time synchronization.");
        }
        return;
    }

    if (now < nextAttemptMs) return;

    if (!pendingPayload.isEmpty()
        && now - pendingSinceMs >= MAX_PENDING_AGE_MS) {
        stopSending("Pending sample is too old. Restore connectivity, then restart.");
        return;
    }

    if (pendingPayload.isEmpty() && !prepareSample(now)) return;

    WiFiClient client;
    HTTPClient http;
    String endpoint = String(backend) + "/api/v1/telemetry/readings";

    if (!http.begin(client, endpoint)) {
        Serial.println("Could not initialize the backend connection.");
        scheduleRetry();
        return;
    }

    http.setConnectTimeout(3000);
    http.setTimeout(5000);
    http.setReuse(false);
    http.setFollowRedirects(HTTPC_DISABLE_FOLLOW_REDIRECTS);

    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Key-Id", TELEMETRY_DEVICE_KEY_ID);
    http.addHeader("X-Device-Secret", TELEMETRY_DEVICE_SECRET);

    int status = http.POST(pendingPayload);
    http.end();

    // The inspected backend returns HTTP 200 only after ingestion succeeds.
    // Do not log request headers, credentials, or response bodies.
    if (status == HTTP_CODE_OK) {
        Serial.printf(
            "Simulated sample %llu acknowledged by backend (HTTP 200).\n",
            static_cast<unsigned long long>(sequence)
        );

        pendingPayload = "";
        retryDelayMs = SAMPLE_INTERVAL_MS;
        nextAttemptMs = uptimeMs() + SAMPLE_INTERVAL_MS;

        if (sequence >= static_cast<uint64_t>(INT64_MAX)) {
            stopSending("Sequence limit reached. Restart the device.");
            return;
        }

        sequence++;
        return;
    }

    Serial.printf("Telemetry request result: %d\n", status);

    if (status < 0 || status == 408 || status == 429 || status >= 500) {
        scheduleRetry();
        return;
    }

    stopSending(
        "Backend rejected the request. Check its HTTP status before restarting."
    );
}