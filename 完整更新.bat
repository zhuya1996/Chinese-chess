@echo off
cd /d "%~dp0"

cls
echo.
echo ============================================================
echo      完整更新 - 移动端完整版界面
echo ============================================================
echo.

echo [1/2] 停止旧服务并清理...
taskkill /F /IM python.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] 已停止旧服务
) else (
    echo       [--] 没有运行中的服务
)
timeout /t 2 >nul

echo.
echo [2/2] 启动新服务（禁用浏览器缓存）...
echo.
echo ============================================================
echo      新功能：
echo      - 大号中文着法显示
echo      - 完整信息（评分、深度、变化）
echo      - 多个候选按钮
echo      - 卡片式布局
echo ============================================================
echo.
echo 重要提示：
echo   如果手机看到的还是旧界面，请访问：
echo   http://你的IP:5000/mobile?v=3
echo                            ^^^^
echo                            加这个参数清除缓存
echo.
echo 正在启动服务...
echo.

C:\ProgramData\Anaconda3\python.exe web_assistant.py

pause
