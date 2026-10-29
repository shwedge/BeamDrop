@echo off
setlocal EnableDelayedExpansion
title BeamDrop - Pull Latest Android Updates from GitHub
color 0A

echo ============================================================================
echo   BeamDrop Continuity - Pull Latest Updates from GitHub
echo   Repository: https://github.com/shwedge/BeamDrop.git
echo   Publisher: Sajjad Hossain [sajjadtheacid@gmail.com]
echo ============================================================================
echo.

cd /d "%~dp0"

echo [1/2] Fetching and pulling latest changes from GitHub...
git pull origin main

if errorlevel 1 (
    echo.
    echo [ERROR] Could not pull from GitHub.
    echo Please verify your internet connection or GitHub credentials.
    pause
    exit /b 1
)

echo.
echo [2/2] Synchronizing updated assets...
if exist "BeamDrop-Latest-Complete-Update.zip" del /f /q "BeamDrop-Latest-Complete-Update.zip" >nul 2>&1

echo.
echo ============================================================================
echo   Successfully pulled latest Android updates from AI Studio / GitHub!
echo ============================================================================
pause
