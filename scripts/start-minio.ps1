# start-minio.ps1
# Script to run a standalone MinIO server locally on Windows without Docker.

$BinDir = Join-Path $PSScriptRoot "../minio-bin"
$DataDir = Join-Path $PSScriptRoot "../minio-data"

if (!(Test-Path $BinDir)) {
    New-Item -ItemType Directory -Path $BinDir | Out-Null
}
if (!(Test-Path $DataDir)) {
    New-Item -ItemType Directory -Path $DataDir | Out-Null
}

$MinioExe = Join-Path $BinDir "minio.exe"
$McExe = Join-Path $BinDir "mc.exe"

# 1. Download minio.exe if not exists
if (!(Test-Path $MinioExe)) {
    Write-Host "Downloading minio.exe from official release repository..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri "https://dl.min.io/server/minio/release/windows-amd64/minio.exe" -OutFile $MinioExe
}

# 2. Download mc.exe if not exists
if (!(Test-Path $McExe)) {
    Write-Host "Downloading mc.exe (MinIO Client) from official release repository..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri "https://dl.min.io/client/mc/release/windows-amd64/mc.exe" -OutFile $McExe
}

# 3. Start MinIO Server in background
Write-Host "Starting MinIO Server on port 9000 (Console on 9001)..." -ForegroundColor Green
$env:MINIO_ROOT_USER = "keeply"
$env:MINIO_ROOT_PASSWORD = "keeply123456"

$MinioProcess = Start-Process -FilePath $MinioExe -ArgumentList "server `"$DataDir`" --address :9000 --console-address :9001" -NoNewWindow -PassThru

# Wait for server to start up
Start-Sleep -Seconds 5

# 4. Configure bucket using mc client
Write-Host "Configuring 'keeply' bucket..." -ForegroundColor Cyan
& $McExe alias set local http://localhost:9000 keeply keeply123456
& $McExe mb -p local/keeply
& $McExe anonymous set none local/keeply

Write-Host "------------------------------------------------" -ForegroundColor Green
Write-Host "MinIO Server is running natively on Windows!" -ForegroundColor Green
Write-Host "API Endpoint: http://localhost:9000"
Write-Host "Web Console:  http://localhost:9001"
Write-Host "Root User:    keeply"
Write-Host "Root Pass:    keeply123456"
Write-Host "------------------------------------------------" -ForegroundColor Green
Write-Host "Press Ctrl+C or close this window to stop the MinIO server."

try {
    while (!$MinioProcess.HasExited) {
        Start-Sleep -Seconds 2
    }
} finally {
    if (!$MinioProcess.HasExited) {
        Write-Host "Stopping MinIO process..." -ForegroundColor Yellow
        Stop-Process -Id $MinioProcess.Id -Force
    }
}
