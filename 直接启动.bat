@echo off
cd /d "%~dp0"

echo.
echo ====================================
echo   启动象棋 AI 助手
echo ====================================
echo.
echo 请稍候...
echo.

C:\ProgramData\Anaconda3\python.exe web_assistant.py

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Anaconda Python 失败，尝试系统 Python...
    echo.
    python web_assistant.py
)

pause
