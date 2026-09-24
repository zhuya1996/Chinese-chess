@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      中国象棋 AI 助手 - 状态检查
echo ============================================================
echo.

echo [1/4] 检查引擎文件...
if exist "src\pikafish.exe" (
    echo ✓ 引擎文件存在
    dir src\pikafish.exe | find "pikafish.exe"
) else (
    echo ✗ 引擎文件缺失
    goto :error
)

echo.
echo [2/4] 检查神经网络模型...
if exist "src\pikafish.nnue" (
    echo ✓ 神经网络模型存在
    dir src\pikafish.nnue | find "pikafish.nnue"
) else (
    echo ✗ 神经网络模型缺失
    goto :error
)

echo.
echo [3/4] 检查 Web 服务...
curl -s http://localhost:5000/api/status >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ✓ Web 服务运行中 (http://localhost:5000)
) else (
    echo ⚠ Web 服务未启动
    echo   请运行: 启动服务_修复版.bat
)

echo.
echo [4/4] 系统信息...
C:\ProgramData\Anaconda3\python.exe --version 2>&1 | find "Python"
echo.

echo ============================================================
echo      状态检查完成
echo ============================================================
echo.
echo 快速启动:
echo   1. 双击: 启动服务_修复版.bat
echo   2. 访问: http://localhost:5000
echo   3. 点击: "启动引擎" 按钮
echo.
pause
exit /b 0

:error
echo.
echo ============================================================
echo      发现问题
echo ============================================================
echo.
echo 请先完成以下步骤:
echo   1. 下载 Pikafish 引擎
echo   2. 解压到 src 目录
echo   3. 运行本脚本验证
echo.
pause
exit /b 1
