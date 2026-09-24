@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      测试移动端 API
echo ============================================================
echo.

echo [1/3] 检查服务是否运行...
curl -s http://localhost:5000/api/status >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 服务未运行
    echo.
    echo 请先启动服务:
    echo   启动服务_修复版.bat
    echo.
    pause
    exit /b 1
)
echo ✓ 服务运行中

echo.
echo [2/3] 测试移动端状态 API...
curl -s http://localhost:5000/api/mobile/status
echo.

echo.
echo [3/3] 测试移动端建议 API...
curl -X POST http://localhost:5000/api/mobile/quick_suggest ^
  -H "Content-Type: application/json" ^
  -d "{\"depth\": 12}"
echo.
echo.

echo ============================================================
echo      测试完成
echo ============================================================
echo.
echo 下一步:
echo   1. 在浏览器测试移动端页面:
echo      http://localhost:5000/mobile
echo.
echo   2. 在手机浏览器测试（替换为你的IP）:
echo      http://192.168.100.101:5000/mobile
echo.
echo   3. 安装 Android App 使用悬浮窗
echo.

pause
