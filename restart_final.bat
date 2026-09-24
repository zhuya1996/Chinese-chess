@echo off
chcp 65001 >nul
cd /d "%~dp0"

cls
echo.
echo ============================================================
echo      Restart Service - Final Version
echo ============================================================
echo.

echo [1/4] Stop all Python processes...
taskkill /F /IM python.exe >nul 2>&1
taskkill /F /IM pythonw.exe >nul 2>&1
echo       Done
timeout /t 3 >nul

echo [2/4] Clear port...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5000"') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo       Done
timeout /t 2 >nul

echo [3/4] Verify processes stopped...
tasklist | findstr python.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [Warning] Python still running
    tasklist | findstr python.exe
    echo.
    pause
    exit /b 1
) else (
    echo       [OK] All stopped
)

echo.
echo [4/4] Start new service...
echo.
echo ============================================================
echo      Access URLs:
echo      - PC: http://localhost:5000
echo      - Mobile Simple: http://192.168.100.102:5000/mobile?v=7
echo      - Mobile Full: http://192.168.100.102:5000/mobile/full
echo ============================================================
echo.

C:\ProgramData\Anaconda3\python.exe web_assistant.py

pause
