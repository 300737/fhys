@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  Push the vendored media3 fork to its own repo.
REM  The main repo (fhys) gitignores media/, so GitHub Actions
REM  clones this repo into media/ before building.
REM
REM  BEFORE RUNNING: create an empty repo on GitHub named
REM      https://github.com/300737/fhys-media3
REM ============================================================

set "REPO=https://github.com/300737/fhys-media3"
set "BRANCH=main"

cd /d "%~dp0"

if not exist "media" (
    echo [ERROR] media directory not found.
    pause
    exit /b 1
)

echo.
echo [1/4] Preparing media3 fork repo...
pushd media
if not exist ".git" (
    git init -b %BRANCH%
    git config user.name "300737"
    git config user.email "300737@users.noreply.github.com"
) else (
    git config user.name "300737"
    git config user.email "300737@users.noreply.github.com"
)

git remote get-url origin >nul 2>&1
if errorlevel 1 ( git remote add origin %REPO% ) else ( git remote set-url origin %REPO% )

echo [2/4] Staging (build outputs excluded)...
if not exist ".gitignore" (
    > .gitignore echo *build
    >> .gitignore echo buildout/
    >> .gitignore echo .gradle/
    >> .gitignore echo .idea/
    >> .gitignore echo local.properties
    >> .gitignore echo *.iml
)
git add -A
if errorlevel 1 ( popd & pause & exit /b 1 )

echo [3/4] Committing...
set "MSG=%~1"
if "!MSG!"=="" set "MSG=media3 fork (fongmi) 1.11.1"
git commit -m "!MSG!"
if errorlevel 1 echo        [INFO] Nothing to commit.

echo [4/4] Pushing...
git branch -M %BRANCH%
git push -u origin %BRANCH%
if errorlevel 1 (
    echo.
    echo [ERROR] Push failed. Make sure the repo exists:
    echo      %REPO%
    echo   and that you authenticate with a Personal Access Token.
    popd
    pause
    exit /b 1
)
popd

echo.
echo [OK] media3 fork pushed.
echo      Now push main code with push.bat, and GitHub Actions will build the APK.
echo.
pause
