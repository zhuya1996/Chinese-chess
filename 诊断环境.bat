@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"

cls
echo.
echo ============================================================
echo      环境诊断工具
echo ============================================================
echo.

REM 检查引擎文件
echo [检查 1] 引擎文件
if exist "src\pikafish.exe" (
    echo [OK] src\pikafish.exe 存在
    for %%A in ("src\pikafish.exe") do echo       大小: %%~zA 字节
) else (
    echo [错误] src\pikafish.exe 不存在
)

if exist "src\pikafish.nnue" (
    echo [OK] src\pikafish.nnue 存在
    for %%A in ("src\pikafish.nnue") do echo       大小: %%~zA 字节
) else (
    echo [错误] src\pikafish.nnue 不存在
)

echo.
echo [检查 2] Python 环境
echo.
echo 尝试 Anaconda Python:
if exist "C:\ProgramData\Anaconda3\python.exe" (
    echo [OK] Anaconda Python 存在
    C:\ProgramData\Anaconda3\python.exe --version 2>&1
    echo.
    echo 检查 Flask:
    C:\ProgramData\Anaconda3\python.exe -c "import flask; print('Flask 已安装, 版本:', flask.__version__)" 2>&1
    if !ERRORLEVEL! NEQ 0 (
        echo [错误] Flask 未安装
    )
) else (
    echo [错误] Anaconda Python 不存在
)

echo.
echo 尝试系统 Python:
where python >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    echo [OK] 系统 Python 存在
    python --version 2>&1
    echo.
    echo 检查 Flask:
    python -c "import flask; print('Flask 已安装, 版本:', flask.__version__)" 2>&1
    if !ERRORLEVEL! NEQ 0 (
        echo [错误] Flask 未安装
    )
) else (
    echo [错误] 系统 Python 不在 PATH 中
)

echo.
echo [检查 3] 端口状态
netstat -ano | findstr ":5000.*LISTENING" >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo [OK] 端口 5000 空闲
) else (
    echo [警告] 端口 5000 被占用
)

echo.
echo [检查 4] 网络 IP 地址
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /C:"IPv4"') do (
    for /f "tokens=1" %%b in ("%%a") do (
        echo       手机访问地址: http://%%b:5000/mobile
    )
)

echo.
echo ============================================================
echo      诊断完成
echo ============================================================
echo.

pause
