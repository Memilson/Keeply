$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..")
Set-Location $ProjectRoot

Write-Host "🚀 Iniciando reset do ambiente Keeply..." -ForegroundColor Cyan

# Resolver caminhos no Windows (AppData)
$KeeplyConfig = Join-Path $env:APPDATA "keeply"
$KeeplyData = Join-Path $env:LOCALAPPDATA "keeply"
$KeeplyRuntime = Join-Path $env:TEMP "keeply"

# A. Parar todos os processos do agente (Daemon e UI)
Write-Host "🛑 Parando processos do agente..." -ForegroundColor Yellow
$PidFile = Join-Path $KeeplyRuntime "daemon.pid"
if (Test-Path $PidFile) {
    $DaemonPid = Get-Content $PidFile
    try {
        Stop-Process -Id $DaemonPid -Force -ErrorAction SilentlyContinue
    } catch {}
}

# Mata processos por linha de comando ou nome
Get-Process | Where-Object { $_.CommandLine -like "*com.keeply.agent*" -or $_.CommandLine -like "*:agent:run*" } | Stop-Process -Force -ErrorAction SilentlyContinue

# Dá um tempo para os processos liberarem os arquivos
Start-Sleep -Seconds 2

# 0. Matar o backend se estiver rodando
Write-Host "🛑 Parando processo do backend (Java/Gradle)..." -ForegroundColor Yellow
Get-Process | Where-Object { $_.CommandLine -match "gradle-wrapper.jar.*:backend:bootRun|BackendApplication|com.keeply.backend" } | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# 1. Parar infra e remover volumes
Write-Host "📦 Limpando volumes do Docker (Postgres e MinIO)..." -ForegroundColor Yellow
docker compose -f infra/docker-compose.yml down -v

# 2. Subir infra limpa
Write-Host "⚡ Subindo infraestrutura..." -ForegroundColor Yellow
docker compose -f infra/docker-compose.yml up -d

# 3. Remover arquivos locais do agente
Write-Host "💾 Removendo arquivos de configuração, estado e bancos do agente..." -ForegroundColor Yellow

if (Test-Path $KeeplyConfig) { Remove-Item -Recurse -Force $KeeplyConfig }
if (Test-Path $KeeplyData) { Remove-Item -Recurse -Force $KeeplyData }
if (Test-Path $KeeplyRuntime) { Remove-Item -Recurse -Force $KeeplyRuntime }

# Limpar eventuais arquivos de banco na raiz (durante testes)
Get-ChildItem -Path $ProjectRoot -Filter "*keeply_agent*.db*" -Recurse -Depth 2 -File | Remove-Item -Force
Get-ChildItem -Path $ProjectRoot -Filter "agent.db*" -Recurse -Depth 2 -File | Remove-Item -Force

Write-Host "✅ Arquivos e diretórios locais limpos." -ForegroundColor Green

Write-Host "⚠️  AVISO: Por favor, inicie o backend agora (./gradlew :backend:bootRun) em outro terminal." -ForegroundColor Magenta
Write-Host "⏳ Aguardando backend (port 8080) ficar online para registrar o usuário..." -ForegroundColor Cyan

# 4. Aguardar o backend estar pronto
$MaxRetries = 60
$Count = 0
$BackendUp = $false

while ($Count -lt $MaxRetries -and -not $BackendUp) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -ErrorAction Stop
        $BackendUp = $true
    } catch {
        Start-Sleep -Seconds 2
        $Count++
        Write-Host "   Tentativa $Count/$MaxRetries..."
    }
}

if (-not $BackendUp) {
    Write-Host "❌ Erro: O backend não subiu a tempo ou não foi iniciado." -ForegroundColor Red
    exit 1
}

# 5. Criar usuário de teste
Write-Host "👤 Criando usuário de teste (keeply@keeply.com)..." -ForegroundColor Yellow
$body = @{
    name = "Keeply Test"
    email = "keeply@keeply.com"
    password = "keeply123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -Body $body -ContentType "application/json" | Out-Null

Write-Host "`n✅ Ambiente resetado e usuário criado com sucesso!" -ForegroundColor Green
