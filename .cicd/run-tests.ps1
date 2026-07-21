param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$SeedFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")).Path "seed\mock-data.csv"),
    [string]$WarPath = "target\auth-course-1.0.war",
    [int]$StartupTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

Push-Location $ProjectRoot
try {
    Write-Step "1) Validating build"
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed."
    }

    if (-not (Test-Path $WarPath)) {
        throw "Expected artifact not found: $WarPath"
    }

    Write-Step "2) Starting application in background"
    $logDir = Join-Path $ProjectRoot "cicd\logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $appLog = Join-Path $logDir "app.log"
    $process = Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $WarPath, "*>", $appLog) `
        -WorkingDirectory $ProjectRoot `
        -PassThru
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)

    $healthOk = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $healthOk = $true
                break
            }
        }
        catch {
            Write-Step "Checking application health..."
            Start-Sleep -Seconds 10
        }
    }

    if (-not $healthOk) {
        throw "Application did not respond in time at http://localhost:8081/actuator/health"
    }

    Write-Step "3) Running external tests from $SeedFile"
    if (-not (Test-Path $SeedFile)) {
        throw "Seed file not found: $SeedFile"
    }

    $rows = Import-Csv -Path $SeedFile
    if (-not $rows) {
        throw "No scenarios defined in CSV."
    }

    $token = $null
    foreach ($row in $rows) {
        $scenario = $row.scenario
        $operation = $row.operation.Trim().ToLowerInvariant()
        Write-Host "[RUN] $scenario" -ForegroundColor Yellow

        switch ($operation) {
            "createuser" {
                $body = @{ username = $row.username; password = $row.password; confirmPassword = $row.confirmPassword } | ConvertTo-Json -Depth 3
                Write-Host "[DEBUG] Sending to /api/user/create:" -ForegroundColor DarkGray
                Write-Host $body -ForegroundColor DarkGray

                $result = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/user/create" -ContentType "application/json" -Body $body -ErrorAction Stop

                Write-Host "[DEBUG] Response:" -ForegroundColor DarkGray
                Write-Host ($result | ConvertTo-Json -Depth 3) -ForegroundColor DarkGray

                if ($result.username -ne $row.username) {
                    throw "User creation failed for $($row.username)"
                }
            }
            "login" {
                $body = @{ username = $row.username; password = $row.password } | ConvertTo-Json -Depth 3
                Write-Host "[DEBUG] Sending login request:" -ForegroundColor DarkGray
                Write-Host $body -ForegroundColor DarkGray

                $response = Invoke-WebRequest -Method Post -Uri "http://localhost:8081/login" -ContentType "application/json" -Body $body -UseBasicParsing -TimeoutSec 15

                Write-Host "[DEBUG] Response headers:" -ForegroundColor DarkGray
                $response.Headers.GetEnumerator() | ForEach-Object { Write-Host "$($_.Key): $($_.Value)" -ForegroundColor DarkGray }

                $token = $response.Headers['Authorization']
                if (-not $token) {
                    throw "No JWT token received for $($row.username)"
                }
            }
            "getitems" {
                $headers = @{ Authorization = $token }
                Write-Host "[DEBUG] Requesting items with token..." -ForegroundColor DarkGray

                $response = Invoke-WebRequest -Method Get -Uri "http://localhost:8081/api/item" -Headers $headers -UseBasicParsing -TimeoutSec 15

                Write-Host "[DEBUG] Response status: $($response.StatusCode)" -ForegroundColor DarkGray
                if ($response.StatusCode -ne 200) {
                    throw "Item query failed for $($row.username)"
                }
            }
            "cartadd" {
                $body = @{ username = $row.username; itemId = [int]$row.itemId; quantity = [int]$row.quantity } | ConvertTo-Json -Depth 3
                $headers = @{ Authorization = $token }
                Write-Host "[DEBUG] Adding to cart:" -ForegroundColor DarkGray
                Write-Host $body -ForegroundColor DarkGray

                $response = Invoke-WebRequest -Method Post -Uri "http://localhost:8081/api/cart/addToCart" -ContentType "application/json" -Body $body -Headers $headers -UseBasicParsing -TimeoutSec 15

                Write-Host "[DEBUG] Response status: $($response.StatusCode)" -ForegroundColor DarkGray
                if ($response.StatusCode -ne 200) {
                    throw "Cart operation failed for $($row.username)"
                }
            }
            "ordersubmit" {
                $headers = @{ Authorization = $token }
                Write-Host "[DEBUG] Submitting order for $($row.username)" -ForegroundColor DarkGray

                $response = Invoke-WebRequest -Method Post -Uri "http://localhost:8081/api/order/submit/$($row.username)" -Headers $headers -UseBasicParsing -TimeoutSec 15

                Write-Host "[DEBUG] Response status: $($response.StatusCode)" -ForegroundColor DarkGray
                if ($response.StatusCode -ne 200) {
                    throw "Order submission failed for $($row.username)"
                }
            }
            default {
                throw "Unsupported operation: $($row.operation)"
            }
        }

        Write-Host "[PASS] $scenario" -ForegroundColor Green
    }

    Write-Step "External tests completed successfully"
}
finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}
