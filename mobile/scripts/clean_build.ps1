# ============================================================
# Keeply Mobile — Limpeza semanal da pasta build
# Executado automaticamente via Agendador de Tarefas do Windows
# ============================================================

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# Fallback caso seja chamado diretamente
if (-not (Test-Path "$projectRoot\pubspec.yaml")) {
    $projectRoot = "c:\code\Keeply-Mobile"
}

$buildPath = Join-Path $projectRoot "build"
$logFile = Join-Path $projectRoot "scripts\clean_build.log"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

if (Test-Path $buildPath) {
    $sizeMB = [math]::Round((Get-ChildItem $buildPath -Recurse -ErrorAction SilentlyContinue | 
               Measure-Object -Property Length -Sum).Sum / 1MB, 2)
    
    Remove-Item -Recurse -Force $buildPath -ErrorAction SilentlyContinue
    
    $msg = "[$timestamp] Build limpo com sucesso ($sizeMB MB liberados)"
} else {
    $msg = "[$timestamp] Pasta build nao encontrada, nada a limpar"
}

# Grava log
Add-Content -Path $logFile -Value $msg
Write-Host $msg
