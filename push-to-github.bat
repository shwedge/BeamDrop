@echo off
setlocal EnableDelayedExpansion
title BeamDrop - Push to GitHub Repository
color 0B

echo ============================================================================
echo   BeamDrop Continuity - Push to GitHub Repository
echo   Publisher: Sajjad Hossain [sajjadtheacid@gmail.com]
echo ============================================================================
echo.

set "REPO_URL=%~1"

if "%REPO_URL%"=="" (
    echo Enter your GitHub repository URL [Press ENTER for default: https://github.com/shwedge/BeamDrop.git]:
    set /p "REPO_URL=Repository URL: "
)

if "%REPO_URL%"=="" set "REPO_URL=https://github.com/shwedge/BeamDrop.git"

echo.
echo [1/4] Staging and committing any updated Windows or Android files...
git add -A
git diff --cached --quiet
if errorlevel 1 (
    git commit -m "Update BeamDrop: Liquid Glass animation and cross-platform continuity sync"
)

echo.
echo [2/4] Setting remote origin to %REPO_URL%...
git remote remove origin >nul 2>&1
git remote add origin "%REPO_URL%"
if errorlevel 1 (
    echo [ERROR] Failed to set git remote origin.
    pause
    exit /b 1
)

echo.
echo [3/4] Ensuring branch is set to main...
git branch -M main

echo.
echo [4/4] Pushing all files to your GitHub repository...
git push -u origin main --force

if errorlevel 1 (
    echo.
    echo [NOTE] If GitHub asks for authentication, use:
    echo   - Username: your GitHub username
    echo   - Password: a GitHub Personal Access Token (PAT) with 'repo' scope
    echo     (Create one at https://github.com/settings/tokens)
    pause
    exit /b 1
)

echo.
echo ============================================================================
echo [SUCCESS] BeamDrop has been successfully pushed to your GitHub repository!
echo ============================================================================
pause
exit /b 0
