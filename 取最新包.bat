@echo off
chcp 65001 >nul
cd /d %~dp0
echo 正在拉取最新构建的 APK（走 cf.612526.xyz 反代加速）...
echo.
"C:\Program Files\Git\bin\bash.exe" tools/fetch-apk.sh %1 %2
echo.
pause
