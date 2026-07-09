$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "=== Aura Syncro Mobile - Build Release ===" -ForegroundColor Cyan

if (-not (Test-Path "keystore.properties")) {
    Write-Host "ERRORE: manca keystore.properties" -ForegroundColor Red
    Write-Host "Copia keystore.properties.example e compila le password."
    exit 1
}

if (-not (Test-Path "aurasyncro-release-key.keystore")) {
    Write-Host "ERRORE: manca aurasyncro-release-key.keystore" -ForegroundColor Red
    exit 1
}

Write-Host "Compilazione AAB release in corso..." -ForegroundColor Yellow
.\gradlew.bat bundleRelease --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build fallita." -ForegroundColor Red
    exit $LASTEXITCODE
}

$aabPath = "app\build\outputs\bundle\release\app-release.aab"
if (Test-Path $aabPath) {
    Write-Host ""
    Write-Host "BUILD COMPLETATA!" -ForegroundColor Green
    Write-Host "File da caricare su Google Play:" -ForegroundColor Green
    Write-Host (Resolve-Path $aabPath)
    Write-Host ""
    Write-Host "Prossimo passo: carica questo file su https://play.google.com/console"
} else {
    Write-Host "Build terminata ma AAB non trovato." -ForegroundColor Red
    exit 1
}
