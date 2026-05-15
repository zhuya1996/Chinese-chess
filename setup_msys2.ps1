# MSYS2 auto install script
Write-Host "Downloading MSYS2 installer..." -ForegroundColor Cyan

$msysInstaller = "$env:TEMP\msys2-installer.exe"
$msysUrl = "https://mirror.msys2.org/distrib/x86_64/msys2-x86_64-20231026.exe"

try {
    Invoke-WebRequest -UseBasicParsing -Uri $msysUrl -OutFile $msysInstaller -TimeoutSec 300
    Write-Host "Download completed" -ForegroundColor Green
} catch {
    Write-Host "Primary mirror failed, trying GitHub..." -ForegroundColor Yellow
    $msysUrl = "https://github.com/msys2/msys2-installer/releases/download/2023-10-26/msys2-x86_64-20231026.exe"
    Invoke-WebRequest -UseBasicParsing -Uri $msysUrl -OutFile $msysInstaller -TimeoutSec 300
}

if (-not (Test-Path $msysInstaller)) {
    Write-Host "ERROR: Download failed" -ForegroundColor Red
    exit 1
}

Write-Host "Installing MSYS2 to C:\msys64..." -ForegroundColor Cyan
Start-Process -Wait -FilePath $msysInstaller -ArgumentList "install","--root","C:\msys64","--confirm-command"

$BASH = "C:\msys64\usr\bin\bash.exe"
if (-not (Test-Path $BASH)) {
    Write-Host "ERROR: MSYS2 installation failed" -ForegroundColor Red
    exit 1
}

Write-Host "MSYS2 installed successfully!" -ForegroundColor Green
Write-Host "Updating package database..." -ForegroundColor Cyan

& $BASH -lc "pacman -Syu --noconfirm"
Start-Sleep -Seconds 2
& $BASH -lc "pacman -Syu --noconfirm"

Write-Host "Installing MinGW64 toolchain..." -ForegroundColor Cyan
& $BASH -lc "pacman -S --noconfirm mingw-w64-x86_64-toolchain make git wget curl"

Write-Host ""
Write-Host "All dependencies installed successfully!" -ForegroundColor Green
