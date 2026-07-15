# Copia il modulo hardware nel gestionale React + Vite (aurasyncro.com)
# Uso: .\scripts\copia-modulo-sito.ps1 -Destinazione "C:\Users\Elena\Documents\progetto per App Ristorante\frontend"

param(
    [Parameter(Mandatory = $true)]
    [string]$Destinazione
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Modulo = Join-Path $Root "web-integration\vite"

if (-not (Test-Path $Modulo)) {
    Write-Error "Modulo non trovato: $Modulo"
}

if (-not (Test-Path $Destinazione)) {
    Write-Error "Cartella destinazione non trovata: $Destinazione"
}

$hasVite = (Test-Path (Join-Path $Destinazione "vite.config.ts")) -or
           (Test-Path (Join-Path $Destinazione "vite.config.js")) -or
           (Test-Path (Join-Path $Destinazione "index.html"))
if (-not $hasVite) {
    Write-Warning "Attenzione: $Destinazione non sembra un progetto Vite"
    $r = Read-Host "Continuare comunque? (s/n)"
    if ($r -ne "s") { exit 1 }
}

$SrcDir = Join-Path $Destinazione "src"
if (-not (Test-Path $SrcDir)) {
    New-Item -ItemType Directory -Force -Path $SrcDir | Out-Null
}

function Copy-Tree {
    param([string]$Source, [string]$Target)
    if (-not (Test-Path $Source)) { return }
    New-Item -ItemType Directory -Force -Path $Target | Out-Null
    Copy-Item -Path (Join-Path $Source "*") -Destination $Target -Recurse -Force
}

Write-Host ""
Write-Host "Aura Syncro — copia modulo hardware (React + Vite)" -ForegroundColor Cyan
Write-Host "Da:  $Modulo"
Write-Host "A:   $Destinazione"
Write-Host ""

Copy-Tree (Join-Path $Modulo "public") (Join-Path $Destinazione "public")
Copy-Tree (Join-Path $Modulo "src\lib\hardware") (Join-Path $SrcDir "lib\hardware")
Copy-Tree (Join-Path $Modulo "src\hooks") (Join-Path $SrcDir "hooks")
Copy-Tree (Join-Path $Modulo "src\components\hardware") (Join-Path $SrcDir "components\hardware")

$pageDir = Join-Path $SrcDir "pages\settings"
New-Item -ItemType Directory -Force -Path $pageDir | Out-Null
Copy-Item (Join-Path $Modulo "src\pages\settings\HardwareSettingsPage.tsx") $pageDir -Force

Write-Host "File copiati:" -ForegroundColor Green
Write-Host "  public/aura-android-bridge.js"
Write-Host "  src/lib/hardware/"
Write-Host "  src/hooks/useAuraHardware.ts"
Write-Host "  src/components/hardware/"
Write-Host "  src/pages/settings/HardwareSettingsPage.tsx"
Write-Host ""
Write-Host "PROSSIMI PASSI:" -ForegroundColor Yellow
Write-Host "  1. index.html -> <script src=\"/aura-android-bridge.js\"></script>"
Write-Host "  2. src/App.tsx -> <AuraHardwareRoot>"
Write-Host "  3. Router -> /dashboard/settings/hardware"
Write-Host "  4. npm run build && deploy"
Write-Host ""
Write-Host "Guida: web-integration\COSA_FARE_SUL_SITO.txt"
Write-Host "Dettagli: web-integration\MODIFICHE_VITE.txt"
Write-Host ""
