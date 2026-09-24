@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      中国象棋 AI 助手 - Web 图形界面（自动检测版）
echo ============================================================
echo.

REM ============================
REM 1. 检查引擎文件
REM ============================
echo [1/4] 检查引擎文件...
if not exist "src\pikafish.exe" (
    echo ✗ 找不到 src\pikafish.exe
    echo.
    echo 请先下载引擎文件！
    echo 下载地址: https://github.com/official-pikafish/Pikafish/releases
    echo.
    pause
    exit /b 1
)
echo ✓ pikafish.exe 存在

if not exist "src\pikafish.nnue" (
    echo ⚠ 找不到 src\pikafish.nnue
    echo 引擎可能无法正常工作！
    echo.
) else (
    echo ✓ pikafish.nnue 存在
)

REM ============================
REM 2. 自动检测 Python
REM ============================
echo.
echo [2/4] 检测 Python 环境...

set PYTHON_CMD=
set PYTHON_NAME=

REM 尝试 Anaconda Python
if exist "C:\ProgramData\Anaconda3\python.exe" (
    C:\ProgramData\Anaconda3\python.exe -c "import flask" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set PYTHON_CMD=C:\ProgramData\Anaconda3\python.exe
        set PYTHON_NAME=Anaconda Python 3.6
        echo ✓ 发现 Anaconda Python（已有 Flask）
        goto python_found
    )
)

REM 尝试系统 Python
where python >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    python -c "import flask" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set PYTHON_CMD=python
        for /f "tokens=*" %%i in ('python --version 2^>^&1') do set PYTHON_NAME=%%i
        echo ✓ 发现系统 Python（已有 Flask）
        goto python_found
    ) else (
        echo ⚠ 系统 Python 缺少 Flask 依赖
        echo.
        echo 正在尝试安装 Flask...
        python -m pip install Flask==2.3.0 Werkzeug==2.3.0
        if %ERRORLEVEL% EQU 0 (
            set PYTHON_CMD=python
            for /f "tokens=*" %%i in ('python --version 2^>^&1') do set PYTHON_NAME=%%i
            echo ✓ Flask 安装成功
            goto python_found
        )
    )
)

REM 未找到可用的 Python
echo ✗ 未找到可用的 Python 环境
echo.
echo 请先安装 Python 或 Anaconda:
echo   - Python: https://www.python.org/downloads/
echo   - Anaconda: https://www.anaconda.com/download
echo.
echo 或手动安装 Flask:
echo   pip install Flask Werkzeug
echo.
pause
exit /b 1

:python_found
echo.
echo [3/4] Python 信息:
echo   路径: %PYTHON_CMD%
echo   版本: %PYTHON_NAME%
echo.

REM ============================
REM 3. 检查端口占用
REM ============================
echo [4/4] 检查端口 5000...
netstat -ano | findstr ":5000.*LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ⚠ 端口 5000 已被占用
    echo.
    echo 正在尝试停止旧服务...
    taskkill /F /IM python.exe >nul 2>&1
    timeout /t 2 >nul
    echo ✓ 已停止旧服务
) else (
    echo ✓ 端口 5000 可用
)

REM ============================
REM 4. 启动服务
REM ============================
echo.
echo ============================================================
echo      正在启动 Web 服务器
echo ============================================================
echo.
echo 浏览器访问: http://localhost:5000
echo.
echo 获取你的 IP 地址:
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /C:"IPv4"') do (
    for /f "tokens=1" %%b in ("%%a") do (
        echo   手机访问: http://%%b:5000/mobile
    )
)
echo.
echo 按 Ctrl+C 可停止服务器
echo ============================================================
echo.

REM 启动服务
%PYTHON_CMD% web_assistant.py

pause
