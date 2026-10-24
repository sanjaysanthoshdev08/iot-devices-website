# Step 34.C - PostgreSQL user database evidence
#
# Proves, against the running backend and the real hykon_iot database:
#   1. Registration creates a PostgreSQL user record (USER role, ACTIVE status).
#   2. The stored password is a BCrypt hash, never the plaintext password.
#   3. Duplicate emails are rejected (HTTP 409), including a different-case duplicate,
#      and the database enforces a unique constraint on users.email.
#
# Usage (backend must already be running on port 8080):
#   $env:DB_PASSWORD = "<your-db-password>"
#   .\scripts\verify-34c-database.ps1
#
# The DB password is read from the environment and is never printed.
# Only the first 7 characters of the stored hash are shown.

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$Database = "hykon_iot"
)

$ErrorActionPreference = "Stop"

if (-not $env:DB_PASSWORD) {
    throw "DB_PASSWORD is not set in this terminal. Set it first: `$env:DB_PASSWORD = '<your-db-password>'"
}
if (-not (Test-Path $PsqlPath)) {
    throw "psql not found at $PsqlPath. Pass -PsqlPath with the correct location."
}

$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" }
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$email = "evidence.$stamp@example.com"
# Test-only password generated per run; letters and digits only.
$plainPassword = "Evid" + [guid]::NewGuid().ToString("N").Substring(0, 12)

$evidenceDir = Join-Path $PSScriptRoot "..\docs\evidence"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$reportPath = Join-Path $evidenceDir "34C-database-evidence-$stamp.txt"

function Write-Report([string]$text) {
    Write-Host $text
    Add-Content -Path $reportPath -Value $text -Encoding UTF8
}

function Invoke-Register([string]$registerEmail) {
    $body = @{
        name            = "Evidence User"
        email           = $registerEmail
        password        = $plainPassword
        confirmPassword = $plainPassword
    } | ConvertTo-Json

    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/register" -Method Post `
            -ContentType "application/json" -Body $body -UseBasicParsing
        return @{ Status = [int]$response.StatusCode; Body = $response.Content }
    }
    catch [System.Net.WebException] {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) {
            throw "Backend not reachable at $BaseUrl. Start it first."
        }
        $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
        return @{ Status = [int]$errorResponse.StatusCode; Body = $reader.ReadToEnd() }
    }
}

function Invoke-Sql([string]$sql, [switch]$Scalar) {
    $previous = $env:PGPASSWORD
    $env:PGPASSWORD = $env:DB_PASSWORD
    try {
        $flags = @("-h", "localhost", "-p", "5432", "-U", $dbUser, "-d", $Database, "-X", "-q")
        if ($Scalar) { $flags += @("-t", "-A") }
        $output = $sql | & $PsqlPath @flags `
            -v ON_ERROR_STOP=1 -v "email=$email" -v "plain=$plainPassword"
        if ($LASTEXITCODE -ne 0) { throw "psql failed with exit code $LASTEXITCODE (see message above)." }
        return ($output | Out-String).TrimEnd()
    }
    finally {
        $env:PGPASSWORD = $previous
    }
}

$results = [ordered]@{}

Write-Report "HYKON IoT - Step 34.C database evidence"
Write-Report "Run at:     $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Report "Backend:    $BaseUrl"
Write-Report "Database:   $Database"
Write-Report "Test email: $email"
Write-Report ""

# 1. Registration creates a record
Write-Report "== 1. Register a new user =="
$first = Invoke-Register $email
Write-Report "HTTP $($first.Status)"
Write-Report $first.Body
$results["Registration returns 201"] = ($first.Status -eq 201)
Write-Report ""

Write-Report "== 2. User record in PostgreSQL (hash masked) =="
Write-Report (Invoke-Sql @'
SELECT u.id, u.name, u.email, r.name AS role, u.status,
       left(u.password, 7) AS hash_prefix,
       length(u.password) AS hash_length,
       u.password LIKE '$2_$%' AS is_bcrypt,
       u.created_at
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE u.email = :'email';
'@)
$recordCheck = Invoke-Sql -Scalar @'
SELECT count(*) FROM users u JOIN roles r ON r.id = u.role_id
WHERE u.email = :'email' AND r.name = 'USER' AND u.status = 'ACTIVE';
'@
$results["Record saved with USER role and ACTIVE status"] = ($recordCheck.Trim() -eq "1")
Write-Report ""

# 2. BCrypt hash, no plaintext
Write-Report "== 3. Password storage =="
$hashCheck = Invoke-Sql -Scalar @'
SELECT (password LIKE '$2_$%' AND length(password) = 60)::text || ',' ||
       (password = :'plain')::text
FROM users WHERE email = :'email';
'@
$parts = $hashCheck.Trim().Split(",")
Write-Report "Stored value is a 60-character BCrypt hash: $($parts[0])"
Write-Report "Stored value equals the plaintext password:  $($parts[1])"
$results["Password stored as BCrypt hash"] = ($parts[0] -eq "true")
$results["Plaintext password not stored"] = ($parts[1] -eq "false")
Write-Report ""

# 3. Duplicate rejection
Write-Report "== 4. Duplicate registration (same email) =="
$duplicate = Invoke-Register $email
Write-Report "HTTP $($duplicate.Status)"
Write-Report $duplicate.Body
$results["Duplicate email rejected with 409"] = ($duplicate.Status -eq 409)
Write-Report ""

Write-Report "== 5. Duplicate registration (same email, upper case) =="
$caseDuplicate = Invoke-Register $email.ToUpper()
Write-Report "HTTP $($caseDuplicate.Status)"
Write-Report $caseDuplicate.Body
$results["Different-case duplicate rejected with 409"] = ($caseDuplicate.Status -eq 409)
Write-Report ""

Write-Report "== 6. Row count for the email after duplicate attempts =="
$count = Invoke-Sql -Scalar @'
SELECT count(*) FROM users WHERE lower(email) = lower(:'email');
'@
Write-Report "Rows: $($count.Trim())"
$results["Still exactly one row after duplicates"] = ($count.Trim() -eq "1")
Write-Report ""

Write-Report "== 7. Database unique constraint on users.email =="
Write-Report (Invoke-Sql @'
SELECT conname AS constraint_name, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'users'::regclass AND contype = 'u';
'@)
$constraint = Invoke-Sql -Scalar @'
SELECT count(*) FROM pg_constraint
WHERE conrelid = 'users'::regclass AND contype = 'u'
  AND pg_get_constraintdef(oid) = 'UNIQUE (email)';
'@
$results["Unique constraint on users.email exists"] = ([int]$constraint.Trim() -ge 1)
Write-Report ""

# Summary
Write-Report "== Summary =="
$allPassed = $true
foreach ($entry in $results.GetEnumerator()) {
    $mark = if ($entry.Value) { "PASS" } else { "FAIL"; $allPassed = $false }
    Write-Report ("[{0}] {1}" -f $mark, $entry.Key)
}
Write-Report ""
Write-Report $(if ($allPassed) { "RESULT: Step 34.C verified." } else { "RESULT: Step 34.C has failures - see above." })
Write-Host ""
Write-Host "Evidence saved to $reportPath"

if (-not $allPassed) { exit 1 }
