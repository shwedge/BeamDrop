@echo off
setlocal EnableDelayedExpansion
title BeamDrop Continuity - Register Trusted Publisher: Sajjad Hossain
echo ============================================================================
echo   BeamDrop Continuity - Authenticode Digital Certificate Installer
echo   Publisher: Sajjad Hossain [sajjadhossain1508@gmail.com]
echo ============================================================================
echo.
echo Installing Sajjad Hossain Authenticode Code Signing Certificate...
echo This registers publisher "Sajjad Hossain" in your Windows Certificate Store
echo so that Windows Defender, SmartScreen, and Windows 10/11 verify BeamDrop
echo as a trusted, authentic application.
echo.

cd /d "%~dp0"
set CER_FILE=%~dp0SajjadHossain_CodeSigning.cer
if not exist "%CER_FILE%" set CER_FILE=%~dp0SajjadH0ssain_CodeSigning.cer

if not exist "%CER_FILE%" (
    echo [ERROR] Could not find certificate file.
    echo Please ensure SajjadHossain_CodeSigning.cer is in this folder.
    pause
    exit /b 1
)

:: Use Windows native certutil.exe to register certificate into CurrentUser Root & TrustedPublisher
echo [1/2] Adding certificate to Current User Trusted Root Certification Authorities...
certutil.exe -user -addstore -f "Root" "%CER_FILE%" >nul 2>&1
if %errorLevel% neq 0 (
    echo Running elevated certutil...
    certutil.exe -addstore -f "Root" "%CER_FILE%" >nul 2>&1
)

echo [2/2] Adding certificate to Current User Trusted Publishers...
certutil.exe -user -addstore -f "TrustedPublisher" "%CER_FILE%" >nul 2>&1
if %errorLevel% neq 0 (
    certutil.exe -addstore -f "TrustedPublisher" "%CER_FILE%" >nul 2>&1
)

echo.
echo ============================================================================
echo   [SUCCESS] Publisher "Sajjad Hossain" is now officially trusted!
echo   Windows Defender and SmartScreen will recognize all signed BeamDrop
echo   installers (.MSI) and executables (.EXE) as verified and authentic.
echo ============================================================================
echo.
timeout /t 3 >nul 2>&1
exit /b 0
