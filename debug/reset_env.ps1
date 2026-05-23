$ErrorActionPreference = "Stop"

$ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
Set-Location $ProjectRoot

Write-Host "🚀 Iniciando reset do ambiente Keeply (PowerShell)..." -ForegroundColor Cyan

$KeepDataDir = Join-Path $HOME "keeply"

# A. Parar daemon do agente
Write-Host "🛑 Parando daemon do agente..." -ForegroundColor Yellow
$PidFile = Join-Path $KeepDataDir "daemon.pid"
if (Test-Path $PidFile) {
    $DaemonPid = Get-Content $PidFile
    try {
        Stop-Process -Id $DaemonPid -Force -ErrorAction SilentlyContinue
    } catch {}
    Remove-Item $PidFile -Force
}
Get-Process | Where-Object { $_.ProcessName -like "*KeeplyAgentDaemonApp*" } | Stop-Process -Force -ErrorAction SilentlyContinue

# 0. Matar o backend se estiver rodando
Write-Host "🛑 Parando processo do backend..." -ForegroundColor Yellow
Get-Process | Where-Object { $_.CommandLine -like "*:backend:bootRun*" -or $_.ProcessName -like "*BackendApplication*" } | Stop-Process -Force -ErrorAction SilentlyContinue

# 1. Parar infra e remover volumes
Write-Host "📦 Limpando volumes do Docker (Postgres e MinIO)..." -ForegroundColor Yellow
docker compose -f infra/docker-compose.yml down -v

# 2. Subir infra limpa
Write-Host "⚡ Subindo infraestrutura..." -ForegroundColor Yellow
docker compose -f infra/docker-compose.yml up -d

# 3. Remover banco local do agente e arquivos de dados
Write-Host "💾 Removendo banco SQLite local e arquivos de dados..." -ForegroundColor Yellow
$SqliteFiles = @(
    Join-Path $ProjectRoot "keeply_agent.db",
    Join-Path $ProjectRoot "keeply_agent.db-wal",
    Join-Path $ProjectRoot "keeply_agent.db-shm",
    Join-Path $ProjectRoot "agent/keeply_agent.db",
    Join-Path $ProjectRoot "agent/keeply_agent.db-wal",
    Join-Path $ProjectRoot "agent/keeply_agent.db-shm",
    (Join-Path $KeepDataDir "agent.db"),
    (Join-Path $KeepDataDir "agent.db-wal"),
    (Join-Path $KeepDataDir "agent.db-shm"),
    (Join-Path $KeepDataDir "keeply_agent_ui.db"),
    (Join-Path $KeepDataDir "daemon.log"),
    (Join-Path $KeepDataDir "device-auth.json"),
    (Join-Path $KeepDataDir "device-id.txt")
)

foreach ($File in $SqliteFiles) {
    if (Test-Path $File) {
        Remove-Item $File -Force
        Write-Host "   removido: $File"
    }
}

Write-Host "✅ SQLite e logs locais limpos." -ForegroundColor Green

Write-Host "⚠️  AVISO: Por favor, inicie o backend agora (./gradlew :backend:bootRun) em outro terminal." -ForegroundColor Yellow
Write-Host "⏳ Aguardando backend (port 8080) ficar online para registrar o usuário..." -ForegroundColor Yellow

# 4. Aguardar o backend estar pronto
$MaxRetries = 60
$Count = 0
$BackendReady = $false
while (-not $BackendReady -and $Count -lt $MaxRetries) {
    try {
        $Response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method Get -ErrorAction SilentlyContinue
        if ($Response.StatusCode -eq 200) {
            $BackendReady = $true
        }
    } catch {}
    
    if (-not $BackendReady) {
        Start-Sleep -Seconds 2
        $Count++
        Write-Host "   Tentativa $Count/$MaxRetries..."
    }
}

if (-not $BackendReady) {
    Write-Host "❌ Erro: O backend não subiu a tempo ou não foi iniciado." -ForegroundColor Red
    exit 1
}

# 5. Criar usuário de teste
Write-Host "👤 Criando usuário de teste (keeply@keeply.com)..." -ForegroundColor Yellow
$RegisterBody = @{
    name = "Keeply Test"
    email = "keeply@keeply.com"
    password = "keeply123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $RegisterBody

Write-Host "`n✅ Ambiente resetado e usuário criado com sucesso!" -ForegroundColor Green
