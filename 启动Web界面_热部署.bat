@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      中国象棋 AI 助手 - Web 图形界面（热部署）
echo ============================================================
echo.
echo 正在启动 Web 服务器（热部署模式）...
echo.
echo 启动后请在浏览器中打开: http://localhost:5000
echo.
echo 代码或模板改动后会自动重启
echo 按 Ctrl+C 可停止服务器
echo ============================================================
echo.

set CHESS_HOT_RELOAD=1
python web_assistant.py --hot-reload

pause
