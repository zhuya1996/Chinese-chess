@echo off
cd /d "%~dp0"

cls
echo.
echo ============================================================
echo      更新服务（添加中文着法显示）
echo ============================================================
echo.

echo [1/2] 停止旧服务...
taskkill /F /IM python.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       已停止旧服务
    timeout /t 2 >nul
) else (
    echo       没有运行中的服务
)

echo.
echo [2/2] 启动新服务...
echo.
echo 现在支持中文着法显示：
echo   - 炮二平五（代替 h2e2）
echo   - 马八进七（代替 g0f2）
echo   - 更易读易懂！
echo.
echo ============================================================
echo.

C:\ProgramData\Anaconda3\python.exe web_assistant.py

pause
