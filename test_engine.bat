@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      测试 Pikafish 引擎
echo ============================================================
echo.

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

echo [1/2] 检查引擎文件...
dir src\pikafish.exe | find "pikafish.exe"
echo.

echo [2/2] 测试引擎启动...
echo quit | src\pikafish.exe
echo.

if %ERRORLEVEL% EQU 0 (
    echo ============================================================
    echo      引擎测试成功！
    echo ============================================================
    echo.
    echo 现在可以运行: 启动Web界面.bat
    echo.
) else (
    echo ============================================================
    echo      引擎测试失败
    echo ============================================================
    echo.
    echo 可能的原因:
    echo 1. 引擎文件损坏
    echo 2. CPU 不支持此版本（尝试下载 popcnt 或 sse41 版本）
    echo 3. 缺少必要的运行库
    echo.
)

pause
