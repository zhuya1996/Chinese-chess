@echo off
chcp 65001 >nul
echo ====================================
echo    GitHub 自动构建 APK 发布脚本
echo ====================================
echo.

REM 检查是否已经初始化 Git
if not exist .git (
    echo [错误] 当前目录不是 Git 仓库！
    echo 请先运行：git init
    pause
    exit /b 1
)

REM 检查是否配置了远程仓库
git remote -v | findstr "origin" >nul 2>&1
if errorlevel 1 (
    echo [提示] 未检测到远程仓库，请先配置：
    echo.
    echo   git remote add origin https://github.com/你的用户名/Chinese-chess.git
    echo.
    pause
    exit /b 1
)

echo [步骤 1/4] 添加所有更改到暂存区...
git add .
if errorlevel 1 (
    echo [错误] Git add 失败！
    pause
    exit /b 1
)

echo [步骤 2/4] 提交更改...
set /p commit_msg="请输入提交信息（回车使用默认）: "
if "%commit_msg%"=="" (
    set commit_msg=更新：添加自动构建配置
)
git commit -m "%commit_msg%"
if errorlevel 1 (
    echo [警告] 没有需要提交的更改，或提交失败
)

echo [步骤 3/4] 推送到 GitHub master 分支...
git push origin master
if errorlevel 1 (
    echo [错误] 推送失败！请检查：
    echo   1. 是否配置了正确的远程仓库
    echo   2. 是否有推送权限
    echo   3. 网络是否正常
    pause
    exit /b 1
)

echo.
echo [步骤 4/4] 创建版本标签并推送...
set /p version="请输入版本号（如 v1.0.0，回车使用默认）: "
if "%version%"=="" (
    REM 自动生成版本号：v1.0.x （基于当前时间）
    for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
    set version=v1.0.%datetime:~8,2%
    echo 使用自动版本号: %version%
)

REM 检查标签是否已存在
git tag | findstr "^%version%$" >nul 2>&1
if not errorlevel 1 (
    echo [警告] 标签 %version% 已存在！
    set /p overwrite="是否覆盖？(y/N): "
    if /i "%overwrite%"=="y" (
        git tag -d %version%
        git push origin :refs/tags/%version%
    ) else (
        echo 取消发布
        pause
        exit /b 0
    )
)

git tag -a %version% -m "Release %version%"
if errorlevel 1 (
    echo [错误] 创建标签失败！
    pause
    exit /b 1
)

git push origin %version%
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
echo 📦 版本号: %version%
echo ⏳ 构建时间: 约 5-10 分钟
echo.
echo 🔗 查看构建进度：
echo    https://github.com/你的用户名/Chinese-chess/actions
echo.
echo 📥 下载 APK：
echo    https://github.com/你的用户名/Chinese-chess/releases/tag/%version%
echo.
echo 💡 提示：
echo    - 等待 GitHub Actions 构建完成
echo    - 刷新 Releases 页面查看 APK
echo    - 构建失败请查看 Actions 日志
echo.
pause
