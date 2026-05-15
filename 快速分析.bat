@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo ============================================================
echo      快速分析工具
echo ============================================================
echo.
python quick_analyze.py
pause
