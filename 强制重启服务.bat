@echo off
cd /d "%~dp0"

cls
echo.
echo ============================================================
echo      强制重启服务
echo ============================================================
echo.

echo [1/3] 强制停止所有 Python 进程...
taskkill /F /IM python.exe >nul 2>&1
taskkill /F /IM pythonw.exe >nul 2>&1
echo       [OK] 已停止
timeout /t 3 >nul

echo.
echo [2/3] 清理端口占用...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5000.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo       [OK] 端口已清理
timeout /t 2 >nul

echo.
echo [3/3] 启动新服务...
echo.
echo ============================================================
echo      新功能已加载：
echo      1. 移动端完整版（带棋盘）
echo      2. 中文着法显示
echo      3. 禁用浏览器缓存
echo ============================================================
echo.
echo 访问地址：
echo   电脑：http://localhost:5000
echo   手机完整版：http://192.168.100.102:5000/mobile/full
echo   手机悬浮窗：http://192.168.100.102:5000/mobile
echo.
echo 正在启动...
echo.

C:\ProgramData\Anaconda3\python.exe web_assistant.py

pause
