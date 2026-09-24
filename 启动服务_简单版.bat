@echo off
cd /d "%~dp0"

echo.
echo ============================================================
echo      启动象棋 AI 助手
echo ============================================================
echo.

REM 使用 Anaconda Python 启动
C:\ProgramData\Anaconda3\python.exe web_assistant.py

REM 如果上面失败，尝试系统 Python
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo 尝试系统 Python...
    python web_assistant.py
)

pause
