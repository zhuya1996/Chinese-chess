@echo off
chcp 65001 >nul
echo ====================================
echo    首次发布到 GitHub
echo ====================================
echo.

echo [步骤 1/4] 添加所有文件...
git add .
if errorlevel 1 (
    echo [错误] Git add 失败！
    pause
    exit /b 1
)

echo [步骤 2/4] 提交更改...
git commit -m "添加云端构建配置：用户无需开发环境直接下载APK"
if errorlevel 1 (
    echo [提示] 可能没有新的更改，继续推送...
)

echo [步骤 3/4] 推送到 GitHub...
git push origin master
if errorlevel 1 (
    echo [错误] 推送失败！请检查网络和权限
    pause
    exit /b 1
)

echo [步骤 4/4] 创建首个版本标签 v1.0.0...
git tag -a v1.0.0 -m "首次发布：中国象棋AI助手 Android版"
git push origin v1.0.0
if errorlevel 1 (
    echo [错误] 推送标签失败！
    pause
    exit /b 1
)

echo.
echo ====================================
echo          ✅ 发布成功！
echo ====================================
echo.
echo 📦 版本号: v1.0.0
echo ⏳ 预计构建时间: 5-10 分钟
echo.
echo 🔗 查看构建进度：
echo    https://github.com/li-xiao-kun/Chinese-chess/actions
echo.
echo 📥 构建完成后下载 APK：
echo    https://github.com/li-xiao-kun/Chinese-chess/releases/tag/v1.0.0
echo.
echo 💡 提示：
echo    1. 等待 GitHub Actions 自动构建（约5-10分钟）
echo    2. 刷新 Releases 页面查看 app-debug.apk
echo    3. 分享下载链接给用户即可！
echo.
echo 📱 用户下载链接（最新版本）：
echo    https://github.com/li-xiao-kun/Chinese-chess/releases/latest
echo.
pause
