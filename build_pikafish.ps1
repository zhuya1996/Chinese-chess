# Pikafish build script
$BASH = "C:\msys64\usr\bin\bash.exe"

if (-not (Test-Path $BASH)) {
    Write-Host "ERROR: MSYS2 not found. Please run setup_msys2.ps1 first" -ForegroundColor Red
    exit 1
}

$WIN_SRC_DIR = Join-Path $PSScriptRoot "src"
Write-Host "Building Pikafish..." -ForegroundColor Cyan

# Change to directory in PowerShell, then run make via MSYS2
Push-Location $WIN_SRC_DIR

Write-Host "Attempting AVX2 optimized build..." -ForegroundColor Cyan
$result = & $BASH -lc "cd `$(pwd -W | sed 's|\\\\|/|g') && make -j 4 profile-build ARCH=x86-64-avx2 COMP=mingw" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "AVX2 build failed, falling back to SSE4.1-POPCNT..." -ForegroundColor Yellow
    $result = & $BASH -lc "cd `$(pwd -W | sed 's|\\\\|/|g') && make clean && make -j 4 profile-build ARCH=x86-64-sse41-popcnt COMP=mingw" 2>&1
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Profile build failed, trying simple build..." -ForegroundColor Yellow
        $result = & $BASH -lc "cd `$(pwd -W | sed 's|\\\\|/|g') && make clean && make -j 4 build ARCH=x86-64-sse41-popcnt COMP=mingw" 2>&1
    }
}

Pop-Location

Write-Host $result

if (Test-Path (Join-Path $PSScriptRoot "src\pikafish.exe")) {
    Write-Host ""
    Write-Host "Build successful! Executable: $(Join-Path $PSScriptRoot 'src\pikafish.exe')" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Build failed" -ForegroundColor Red
    exit 1
}
