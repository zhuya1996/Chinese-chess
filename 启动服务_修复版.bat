@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      中国象棋 AI 助手 - Web 图形界面
echo ============================================================
echo.

REM 检查引擎文件
if not exist "src\pikafish.exe" (
    echo [错误] 找不到 src\pikafish.exe
    echo 请先下载引擎文件！
    echo.
    pause
    exit /b 1
)

if not exist "src\pikafish.nnue" (
    echo [警告] 找不到 src\pikafish.nnue
    echo 引擎可能无法正常工作！
    echo.
)

echo [检查] 引擎文件已就绪
echo.

REM 查找可用的 Python
set PYTHON_CMD=
if exist "C:\ProgramData\Anaconda3\python.exe" (
    set PYTHON_CMD=C:\ProgramData\Anaconda3\python.exe
    echo [发现] Anaconda Python
) else (
    where python >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set PYTHON_CMD=python
        echo [发现] 系统 Python
    ) else (
        echo [错误] 未找到 Python
        echo 请先安装 Python 或 Anaconda
        echo.
        pause
        exit /b 1
    )
)

echo [使用] %PYTHON_CMD%
%PYTHON_CMD% --version
echo.

echo 正在启动 Web 服务器...
echo.
echo 启动后请在浏览器中打开: http://localhost:5000
echo.
echo 按 Ctrl+C 可停止服务器
echo ============================================================
echo.

REM 启动服务
%PYTHON_CMD% web_assistant.py

pause
