# HYKON IoT Monitoring Platform — Updated Project Plan

Goal: ship this as a **product on the public internet**. Any authorised customer, from anywhere
(Thrissur, Ernakulam, Bangalore…), logs in, enters their Device ID + serial number, and monitors
their inverter/UPS in real time. A device installed in a Thrissur home keeps reporting to the cloud,
so it can be watched from any other place.

Stack in use (kept): Angular (TypeScript, HTML, SCSS) · Spring Boot 4 / Java · JWT security ·
PostgreSQL · ESP32 firmware (PlatformIO).

---------------------------------------------------------------------------------------------------
## CURRENT STATUS (updated 2026-11-18)

| # | Item | Status |
|---|---|---|
| 13–17 | ESP32 setup, telemetry API, monitoring screens, wireless demo | Done (your own steps; closeout + other-hotspot pending) |
| 17B | Roles, distributor/branch hierarchy, provisioning, device registration, customer claim (3 per device) | **Done** |
| D1 | Docker: Postgres + Spring Boot + Nginx/Angular with Docker Compose, first-start seeder, env template | **Done** (tested locally, pushed) |
| U1 | Universal telemetry v2 endpoint (`POST /api/v2/telemetry/readings`) + migration 014 + unit tests | **Done** (code pushed; live test below) |
| U2 | Show extra (non-standard) measurements and firmware/model on the website | Pending |
| U3 | Device model/profile registry (labels, units, ranges per inverter model) | Pending |
| U4 | Device health: online/offline, last seen, firmware version, stale rules | Pending |
| U5 | API guide (OpenAPI/Swagger) + simulator that pretends to be many different inverters | Pending |
| U6 | ESP32 firmware template: home Wi-Fi setup, HTTPS, NTP, offline buffer, OTA | Pending |
| W1 | Enquiry form saved in DB + emailed; edit/delete for distributors, branches, devices; password reset email | Pending |
| P1 | Production readiness: remove localhost URLs from services, Flyway, strong secrets, login rate limit, remove test data, retention, backups | Pending |
| P2 | Charts, warnings/events, summaries, export | Pending |
| X1 | Deployment (VPS + domain + HTTPS + CI/CD) | Pending (after the above) |
| X2 | Remote acceptance with a real ESP32 | Pending |
| X3 | Real inverter communication and verification | Pending |

Next action: U2 → U5 (finish the universal device path), then W1, P1, U6, then deploy.

---------------------------------------------------------------------------------------------------
## PART A — Done before the Claude Code work (your own steps 13–17)

| Step | Status |
|---|---|
| 13 — ESP32 setup and telemetry contract | Completed |
| 14 — Backend telemetry API and database | Completed (demonstration scope) |
| 15 — ESP32 simulated telemetry over Wi-Fi | Working; other-hotspot / stability follow-up pending |
| 16 — Angular monitoring, history, analysis | Completed (implemented scope) |
| 17 — Wireless demonstration acceptance | Core checks passed; closeout pending |

(Details of 13.A–17.L are exactly as in your own record; nothing there was changed.)

---------------------------------------------------------------------------------------------------
## PART B — Done in the Claude Code chats (new Step 17B: multi-role platform)

Everything below is implemented, committed and pushed to GitHub. Frontend unit tests: 188 passing.

### 17B.1 Role model (4 fixed roles)
- **HYKON_ADMIN** — one account; sees and manages everything.
- **DISTRIBUTOR_ADMIN** — (a) the one overseer account that sees all distributors and branches
  (`all_distributors` flag), and (b) scoped distributor logins created from the website, limited to
  their own distributor.
- **BRANCH_ADMIN** — one login per branch; sees and manages only that branch.
- **USER** — customers; see only their own / authorised devices.
- Role rules enforced in Spring Security (`SecurityConfig`), `BranchAccess` and
  `SessionAccessPolicyService`; `AccessDeniedException` now returns HTTP 403 (was 500).

### 17B.2 Hierarchy and database
- Hierarchy: **HYKON → Distributor (district) → Branch → Device → up to 3 customers**.
- Additive PostgreSQL migrations `backend/sql/008–013` (branch address, shared/scoped distributor
  admin, overseer flag, relaxed unique constraints), all applied to the real database.
- Unique **distributor code**, unique **branch code** (globally), unique login email.

### 17B.3 Provisioning (create accounts from the website)
- **Create Distributor**: organisation + its own login, unique distributor code, state, district.
- **Create Branch**: branch + its Branch Admin login, unique branch code, location, official address;
  distributor identified by code; scoped distributors may only add to their own distributor.
- Distributor directory API: HYKON `GET /api/v1/admin/distributors`,
  overseer/scoped `GET /api/v1/distributor/directory`; Accounts table with distributor filter.

### 17B.4 Device registration and ownership
- Admin registers a device against a **verified distributor + branch** (checked in PostgreSQL by ID or
  code); `GET /api/v1/management/devices/options` returns only what the signed-in role may choose.
- **Customer claim**: a customer enters **Device ID + serial number**; the first becomes the owner,
  up to **3 customers per device** (`device_authorized_customers`); repeating the entry is harmless;
  a 4th customer is refused. The customer's device list includes devices shared with them.
- Admins (HYKON / Distributor / Branch) skip the customer verification gate; device lists are scoped
  by role (`DeviceQueryService`).

### 17B.5 Public website backend-related pieces
- Enquiry form validation (name, email, 10-digit phone, message) — **front end only**; no backend
  endpoint yet (see Step 19).
- Device page search by system name, serial number, user, distributor, branch.

### 17B.6 Quality
- Backend tests rewritten/added for the new rules (`BranchAccessTest`, access-policy, registration,
  claim, query tests). Integration tests that need `HYKON_GATE_TEST_DB_PASSWORD` were not run.

---------------------------------------------------------------------------------------------------
## PART C — Remaining plan, in recommended order

> Recommendation: **deploy first with the simulated ESP32 data (Steps 19–21), then connect the real
> inverter (Step 18).** Remote access is your main goal, and it does not depend on the inverter
> protocol. Step 18 keeps its number but is done after deployment.

### Step 17C — Close out the local demonstration (small)
- 17.J record screenshots, test results, demo instructions; 17.K investigate earlier intermittent
  errors; 17.L verify a different hotspot (replaced in production by the Wi-Fi setup flow in 19.F).

### Step 19 — Production readiness (before any server)  ← do this next
**19.A Configuration**
- Replace every hard-coded `http://localhost:8080` in the Angular services with an
  `environment.ts` / `environment.prod.ts` API URL (this is the single most important code change).
- Spring profiles: `dev`, `test`, `prod`; all secrets from environment variables, none in Git
  (DB password, JWT secret, ESP32 credentials).
- Set CORS to the real website domain only.

**19.B Security hardening**
- Change the default HYKON admin password; remove demo/test accounts and ~24 sample customers.
- Strong random JWT secret; short-lived access tokens + refresh; login rate limiting / lockout.
- Per-device credentials for ESP32 (already separate from website login); ability to revoke a device.
- Input validation on every endpoint; security headers; no stack traces in responses.

**19.C Backend features still missing**
- Contact/enquiry endpoint: store enquiries in PostgreSQL and email the team (SMTP).
- Edit / deactivate / delete for distributors, branches and devices (today these need DB access).
- Password reset by email working in production (SMTP provider such as SES, SendGrid or Brevo).
- Audit log: who created/changed which distributor, branch, device.

**19.D Data & operations**
- Flyway (or Liquibase) for migrations instead of manual SQL + `ddl-auto=update`
  (set `ddl-auto=validate` in production).
- Telemetry retention policy (e.g. raw readings 90 days, then daily summaries) and indexes on
  `(device_id, recorded_at)`.
- Backups: nightly `pg_dump` to cloud storage, plus a **tested restore**.
- Logging (structured), Spring Boot Actuator health endpoint.

**19.E Remaining product features (agreed earlier)**
- Trend charts, "view around this reading", warning/event history, daily/monthly summaries, export.

**19.F ESP32 firmware for real customers**
- Wi-Fi provisioning for any home network (setup page / captive portal) — customers must be able to
  change Wi-Fi without re-flashing.
- Talk to the **public HTTPS API** (TLS with a root CA certificate), not a laptop address.
- Over-the-air (OTA) firmware updates; unique device ID + secret provisioned per unit.

**Checkpoint 19:** code builds for production, no secrets in Git, tests green, backup/restore proven.

### Step 20 — Deployment (put it on the internet)
**Recommended architecture (simple, professional, low cost)**

```
Customer browser / phone ──HTTPS──▶  Nginx or Caddy (reverse proxy, TLS)
ESP32 in Thrissur home ───HTTPS──▶     ├─ /            → Angular build (static files)
                                       └─ /api/...     → Spring Boot (Docker)
                                                          └─▶ PostgreSQL (Docker volume or managed)
```

**Tech stack to add**
| Need | Recommended | Notes |
|---|---|---|
| Containers | **Docker + Docker Compose** | One compose file: `frontend(nginx)`, `backend(spring)`, `postgres`. |
| Server | One cloud VM in **India** (AWS Mumbai `ap-south-1`, Azure Central India, DigitalOcean Bangalore, or Hetzner) — start 2 vCPU / 4 GB | Azure is fine if you prefer it; a plain VM is cheaper than Azure App Service + managed DB. |
| Database | PostgreSQL in Docker with a volume **or** managed Postgres (Azure Database / AWS RDS) | Managed = automatic backups, costs more. |
| Domain + DNS | e.g. `app.hykonindia.com`, `api.hykonindia.com` | Needed for HTTPS and for ESP32 to find the server. |
| HTTPS | **Let's Encrypt** certificates via Caddy or Nginx + Certbot | Free, auto-renewing. |
| CI/CD | **GitHub Actions** (build, test, push Docker images, deploy) | You already use GitHub. |
| Backups | Nightly `pg_dump` → S3 / Azure Blob / Backblaze | Keep 14–30 days. |
| Monitoring | Uptime Kuma or UptimeRobot + Actuator health; logs via `docker logs` / Grafana Loki later | Alert when backend or DB is down. |
| Email | SendGrid / SES / Brevo | Password reset, enquiries. |
| Device messaging (later, optional) | **MQTT broker** (EMQX/Mosquitto) | Only if you need thousands of devices or push commands; HTTPS posting is enough to start. |

**Steps**
- 20.A Create the VM, firewall (only 80/443/SSH), non-root user, SSH keys, automatic security updates.
- 20.B Write `Dockerfile`s (backend, frontend) and `docker-compose.yml`; production env file on the server only.
- 20.C Buy/point the domain; enable HTTPS; force HTTP→HTTPS.
- 20.D Restore the current database into production PostgreSQL (after cleaning test data).
- 20.E Set up GitHub Actions: build → test → image → deploy on push to `main`.
- 20.F Turn on backups and monitoring/alerts; run one restore drill.
- 20.G Flash an ESP32 with the production API URL and its device credentials.

**Checkpoint 20:** `https://app.<your-domain>` opens from any network; login works; the ESP32 posts
to the public API.

### Step 21 — Real-world acceptance (remote monitoring)
- 21.A Register a device (e.g. a Thrissur home) under a distributor/branch; customer claims it with
  Device ID + serial from their own phone.
- 21.B Watch it from Ernakulam / Bangalore on mobile data; confirm live values, history, analysis.
- 21.C Verify each role journey (HYKON, Distributor, Branch, up to 3 customers) and that devices of
  other branches/customers are not visible.
- 21.D Failure tests: power cut, home Wi-Fi down, server restart, database restart — devices retry and
  recover without data loss or duplicates.
- 21.E Load test (e.g. 100–500 simulated devices), review slow queries.
- 21.F Write final acceptance, runbook (deploy, rollback, restore, rotate secrets).

**Final checkpoint:** authorised users can monitor deployed devices remotely over the internet.

### Step 18 — Actual inverter measurements (after deployment)
- 18.A confirm inverter model, interface (RS485 / Modbus / CAN / UART) and protocol documentation.
- 18.B interface hardware (e.g. RS485 transceiver, isolation), wiring, power.
- 18.C settings and pin assignments; 18.D read/decode real measurements.
- 18.E replace the simulated generator, keep the telemetry contract; 18.F send with the correct
  simulation flag, leave unsupported values unavailable.
- 18.G compare with verified inverter readings; 18.H test comms loss/recovery; 18.I add other models
  only after verifying their protocols.

### Step 22 — Growth after launch (optional)
- Warnings/alerts by email/SMS/WhatsApp, mobile app or PWA, firmware fleet management (OTA rollout),
  billing/subscription if sold as a service, multi-region/high availability, MQTT for large fleets.

---------------------------------------------------------------------------------------------------
## Suggested order from today
1. Step 19.A → 19.B (environment config, secrets, security) — a few days
2. Step 19.C → 19.D (enquiry endpoint, edit/delete, Flyway, backups)
3. Step 19.F (ESP32 Wi-Fi setup + HTTPS)
4. Step 20 (Docker, VM, domain, HTTPS, CI/CD)
5. Step 21 (remote acceptance with a real device)
6. Step 18 (real inverter), then Step 19.E features and Step 22
