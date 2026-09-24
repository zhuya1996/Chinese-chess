@echo off
chcp 65001 >nul
cd /d "%~dp0"

cls
echo.
echo ============================================================
echo      彻底重启服务
echo ============================================================
echo.

echo [1/4] 停止所有 Python 进程...
taskkill /F /IM python.exe >nul 2>&1
taskkill /F /IM pythonw.exe >nul 2>&1
echo       已停止
timeout /t 3 >nul

echo [2/4] 清理端口...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5000"') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo       已清理
timeout /t 2 >nul

echo [3/4] 验证进程已停止...
tasklist | findstr python.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [警告] 还有 Python 进程在运行
    tasklist | findstr python.exe
    echo.
    echo       请手动关闭这些进程后重试
    pause
    exit /b 1
) else (
    echo       [OK] 所有进程已停止
)

echo.
echo [4/4] 启动新服务...
echo.
echo ============================================================
echo      访问地址：
echo      - 电脑：http://localhost:5000
echo      - 手机悬浮窗：http://192.168.100.102:5000/mobile?v=6
echo      - 手机完整版：http://192.168.100.102:5000/mobile/full
echo ============================================================
echo.

C:\ProgramData\Anaconda3\python.exe web_assistant.py

pause
