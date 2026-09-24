@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo ============================================================
echo      检查 ADB 环境 - JJ 象棋识别准备
echo ============================================================
echo.

echo [1/4] 检查 ADB 是否安装...
where adb >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ✓ ADB 已安装
    adb version | findstr "version"
) else (
    echo ✗ ADB 未安装
    echo.
    echo 请先安装 ADB 工具：
    echo   方法1: 下载 Android SDK Platform Tools
    echo          https://developer.android.com/studio/releases/platform-tools
    echo   方法2: 安装 Android Studio（包含 ADB）
    echo          https://developer.android.com/studio
    echo.
    echo 安装后将 ADB 目录添加到系统 PATH 环境变量
    echo.
    pause
    exit /b 1
)

echo.
echo [2/4] 检查 ADB 服务状态...
adb start-server >nul 2>&1
timeout /t 2 >nul
echo ✓ ADB 服务已启动

echo.
echo [3/4] 检查连接的设备...
adb devices | findstr /R /C:"device$" >nul
if %ERRORLEVEL% EQU 0 (
    echo ✓ 检测到已连接的设备：
    adb devices
    echo.
    echo [4/4] 测试设备通信...
    adb shell "echo 'ADB 通信正常'"
    if %ERRORLEVEL% EQU 0 (
        echo ✓ 设备通信测试成功
        echo.
        echo ============================================================
        echo      环境检查完成 - 一切就绪！
        echo ============================================================
        echo.
        echo 下一步操作：
        echo   1. 在手机上打开 JJ 象棋 App
        echo   2. 进入对局界面（标准棋盘）
        echo   3. 在 Web 界面切换到 "识别助手" Tab
        echo   4. 点击 "启动 JJ 识别" 按钮
        echo   5. 按提示点击棋盘四角进行校准
        echo.
    ) else (
        echo ✗ 设备通信失败
        echo.
        echo 可能的原因：
        echo   1. 手机未授权 USB 调试
        echo   2. USB 线接触不良
        echo   3. 驱动程序问题
        echo.
        echo 解决方法：
        echo   1. 检查手机是否弹出 "允许 USB 调试" 对话框
        echo   2. 更换 USB 数据线
        echo   3. 重启 ADB: adb kill-server 然后 adb start-server
        echo.
    )
) else (
    echo ✗ 未检测到设备
    echo.
    echo 当前连接状态：
    adb devices
    echo.
    echo 请确保：
    echo   1. 手机已通过 USB 连接到电脑
    echo   2. 手机已开启 USB 调试模式：
    echo      设置 → 关于手机 → 连续点击版本号7次
    echo      设置 → 系统 → 开发者选项 → USB 调试（开启）
    echo   3. 手机弹出 "允许 USB 调试" 时点击"确定"
    echo.
    echo 如果手机显示为 unauthorized：
    echo   - 在手机上重新授权 USB 调试
    echo   - 勾选 "始终允许来自这台计算机"
    echo.
)

echo.
echo 其他有用的 ADB 命令：
echo   adb devices           - 查看连接的设备
echo   adb shell             - 进入设备 Shell
echo   adb logcat            - 查看设备日志
echo   adb reboot            - 重启设备
echo   adb kill-server       - 停止 ADB 服务
echo   adb start-server      - 启动 ADB 服务
echo.

pause
