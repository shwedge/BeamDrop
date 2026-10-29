@echo off
setlocal EnableDelayedExpansion
title BeamDrop - Push to Private GitHub Repository
color 0B

echo ============================================================================
echo   BeamDrop Continuity - Push to Private GitHub Repository
echo   Publisher: Sajjad Hossain [sajjadhossain1508@gmail.com]
echo ============================================================================
echo.

set "REPO_URL=%~1"

if "%REPO_URL%"=="" (
    echo Enter your private GitHub repository URL:
    echo (Example: https://github.com/your-username/BeamDrop.git)
    set /p "REPO_URL=Repository URL: "
)

if "%REPO_URL%"=="" (
    echo [ERROR] No repository URL provided.
    pause
    exit /b 1
)

echo.
echo [1/3] Setting remote origin to %REPO_URL%...
git remote remove origin >nul 2>&1
git remote add origin "%REPO_URL%"
if errorlevel 1 (
    echo [ERROR] Failed to set git remote origin.
    pause
    exit /b 1
)

echo [2/3] Ensuring branch is set to main...
git branch -M main

echo [3/3] Pushing all files to your private GitHub repository...
git push -u origin main

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
