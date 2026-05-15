@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      中国象棋 AI 助手 - Web 图形界面
echo ============================================================
echo.
echo 正在启动 Web 服务器...
echo.
echo 启动后请在浏览器中打开: http://localhost:5000
echo.
echo 按 Ctrl+C 可停止服务器
echo ============================================================
echo.

set HOT_MODE=0
if /I "%~1"=="--hot" set HOT_MODE=1
if /I "%~1"=="--reload" set HOT_MODE=1
if /I "%~1"=="--hot-reload" set HOT_MODE=1

if "%HOT_MODE%"=="1" (
    echo 已启用热部署（代码/模板改动后自动重启）...
    set CHESS_HOT_RELOAD=1
    python web_assistant.py --hot-reload
) else (
    python web_assistant.py
)

pause
