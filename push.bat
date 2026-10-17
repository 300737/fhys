@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  One-click push to GitHub
REM  Repo : https://github.com/300737/fhys
REM  Usage: double-click this file, or  push.bat "your commit message"
REM ============================================================

set "REPO=https://github.com/300737/fhys"
set "BRANCH=main"

cd /d "%~dp0"

echo.
echo [1/5] Checking git...
where git >nul 2>&1
if errorlevel 1 (
    echo [ERROR] git not found in PATH. Install Git for Windows first.
    pause
    exit /b 1
)

echo [2/5] Checking repo...
if not exist ".git" (
    echo        No .git found, initializing...
    git init -b %BRANCH%
    if errorlevel 1 ( pause & exit /b 1 )
)

echo [3/5] Checking remote...
git remote get-url origin >nul 2>&1
if errorlevel 1 (
    git remote add origin %REPO%
) else (
    git remote set-url origin %REPO%
)

echo [4/5] Staging files...
git add -A
if errorlevel 1 ( pause & exit /b 1 )

REM --- build commit message ---
set "MSG=%~1"
if "!MSG!"=="" (
    for /f "tokens=1-3 delims=/ " %%a in ("%date%") do set "D=%%a-%%b-%%c"
    for /f "tokens=1-2 delims=:." %%a in ("%time%") do set "T=%%a%%b"
    set "MSG=update !D! !T!"
)
echo        commit message: !MSG!

git commit -m "!MSG!"
if errorlevel 1 (
    echo        [INFO] Nothing to commit, or commit failed.
)

echo [5/5] Pushing to %BRANCH% ...
git branch -M %BRANCH%
git push -u origin %BRANCH%
if errorlevel 1 (
    echo.
    echo [ERROR] Push failed.
    echo   - If asked for credentials, use a GitHub Personal Access Token as the password.
    echo   - If rejected because remote has history you lack, run:
    echo       git pull --rebase origin %BRANCH%
    echo     then run this script again.
    pause
    exit /b 1
)

echo.
echo [OK] Pushed. GitHub Actions will build the APK automatically:
echo      %REPO%/actions
echo.
pause
