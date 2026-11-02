# ESP32 telemetry contract v1

Implementation target for Steps 14-16. No telemetry API exists yet.

## Scope
Matches the device monitoring page: Battery, Grid, Load and inverter/UPS.
No PV. Example values are simulated, not actual inverter readings.
Initial sending interval: one sample every 5 seconds.

## Identity and delivery
- schemaVersion: integer 1.
- deviceCode: exact existing registered device code; does not create devices.
- Authenticate the ESP32 separately from website users.
- Backend must match deviceCode to the authenticated device.
- Keep secrets out of the JSON body, Git and logs.
- bootId: random UUID generated at each ESP32 startup.
- sequence: positive integer starting at 1 per boot; increments per new sample.
- Retry the unchanged sample with the same bootId and sequence.
- Unique sample identity: authenticated device + bootId + sequence.
- Identical retries must not create duplicate readings.
- Same identity with different content must be rejected.

## Time and ordering
- measuredAt: UTC ISO-8601 timestamp ending in Z when synchronized.
- Use null before time synchronization; never invent a calendar timestamp.
- uptimeMs: nonnegative milliseconds since startup, using a 64-bit counter.
- Backend adds its own receivedAt timestamp.
- Stale or out-of-order samples must not replace a newer latest reading.
- Timestamp tolerances and ordering rules are implemented and tested in Step 14.

## Measurements
Values must be finite JSON numbers.
Missing or unsupported measurements use null, never a fabricated zero.

Battery:
- voltageV: nonnegative volts.
- currentA: signed amperes; positive charging, negative discharging.
- socPercent: charge percentage from 0 to 100.

Grid:
- present: true, false, or null when unknown.
- voltageV: nonnegative RMS volts.
- frequencyHz: nonnegative hertz.
- inputPowerW: real watts; positive import, negative export.
- Export is supported only where the actual model supports it.

Load:
- voltageV: nonnegative RMS output volts.
- currentA: nonnegative RMS output amperes.
- outputPowerW: nonnegative real output watts.
- loadPercent: inverter-reported or derived using an explicitly configured,
  compatible rated capacity; never assume a capacity.
- Actual overload may exceed 100%; do not silently clamp.
- Do not assume real power equals voltage multiplied by current.

## Operating state and display
- simulated: required boolean; true for dummy-data firmware.
- operatingMode: LINE, BATTERY, BYPASS, STANDBY, FAULT, or UNKNOWN.
- Unmapped inverter modes use UNKNOWN.
- Angular labels demonstration readings Simulated data.
- Registration status ACTIVE does not mean telemetry is online.
- Connection status and last update derive from backend telemetry reception.
- No readings means unavailable, not healthy, online or zero.
- Energy-flow animation requires supporting received measurements and state.

## Model compatibility
Keep field names and units consistent across supported models and capacities.
Model-specific firmware converts register values into these units.
Unsupported fields remain null.
Inspect existing registration metadata before adding capacity or capability fields.

## Security and implementation
Protect device credentials in transit.
Plain HTTP demonstrations are limited to an approved trusted local network.
Production requires HTTPS with server certificate validation.

Step 14: authenticated ingestion, validation, storage and read APIs.
Step 15: ESP32 simulated samples through Wi-Fi and HTTP POST.
Step 16: Angular monitoring integration.
Step 17: complete wireless demonstration acceptance.
Step 18: replace simulated readings with actual inverter measurements.