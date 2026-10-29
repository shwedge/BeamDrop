@echo off
setlocal EnableDelayedExpansion
title BeamDrop Continuity - 1-Click MSI Installer and Launcher - Publisher Sajjad Hossain
color 0B

echo ============================================================================
echo   BeamDrop Continuity Engine v2.4.0.0 - Native Windows MSI and App Setup
echo   Publisher: Sajjad Hossain [sajjadhossain1508@gmail.com]
echo ============================================================================
echo.

cd /d "%~dp0"

set "INSTALL_DIR=%LOCALAPPDATA%\Programs\Sajjad Hossain\BeamDrop"
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%" >nul 2>&1

echo [1/4] Removing Windows Mark-of-the-Web Zone.Identifier blocks...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -Path '%~dp0' -Recurse -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue" >nul 2>&1

echo [2/4] Running PowerShell Authenticode Certificate, MSI, and Setup Builder...
if exist "%~dp0Build-BeamDropMsi.ps1" goto :RUN_PS_BUILDER
goto :DIRECT_COMPILE

:RUN_PS_BUILDER
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-BeamDropMsi.ps1" -OutputDir "%~dp0."
if exist "%INSTALL_DIR%\BeamDrop.exe" goto :CHECK_LAUNCH
if exist "%~dp0BeamDrop.exe" goto :COPY_TO_INSTALL

:DIRECT_COMPILE
echo.
echo [3/4] Running direct .NET Framework 4.x C# Compiler fallback...
set "CSC_PATH=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if exist "%CSC_PATH%" goto :FOUND_CSC
set "CSC_PATH=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if exist "%CSC_PATH%" goto :FOUND_CSC
echo [ERROR] Could not locate .NET Framework v4.0.30319 csc.exe on this PC.
pause
exit /b 1

:FOUND_CSC
set "WPF_DIR=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\WPF"
if exist "%WPF_DIR%\PresentationFramework.dll" goto :DO_CSC_BUILD
set "WPF_DIR=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\WPF"

:DO_CSC_BUILD
set "MANIFEST_ARG="
if exist "%~dp0BeamDrop.exe.manifest" set "MANIFEST_ARG=/win32manifest:"%~dp0BeamDrop.exe.manifest""
if exist "%~dp0BeamDrop.manifest" set "MANIFEST_ARG=/win32manifest:"%~dp0BeamDrop.manifest""

set "ICON_ARG="
if exist "%~dp0BeamDrop.ico" set "ICON_ARG=/win32icon:"%~dp0BeamDrop.ico""

"%CSC_PATH%" /nologo /target:winexe /out:"%~dp0BeamDrop.exe" /Platform:anycpu /optimize+ %MANIFEST_ARG% %ICON_ARG% /r:"%WPF_DIR%\PresentationCore.dll" /r:"%WPF_DIR%\PresentationFramework.dll" /r:"%WPF_DIR%\WindowsBase.dll" /r:System.Xaml.dll /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Net.Http.dll "%~dp0BeamDropWindows.cs"
if errorlevel 1 goto :COMPILE_ERROR

copy /Y "%~dp0BeamDrop.exe" "%~dp0BeamDropApp.exe" >nul 2>&1

:COPY_TO_INSTALL
if exist "%~dp0BeamDrop.exe" copy /Y "%~dp0BeamDrop.exe" "%INSTALL_DIR%\BeamDrop.exe" >nul 2>&1
if exist "%~dp0BeamDrop.ico" copy /Y "%~dp0BeamDrop.ico" "%INSTALL_DIR%\BeamDrop.ico" >nul 2>&1

:CHECK_LAUNCH
echo.
echo [4/4] Verifying and launching BeamDrop Continuity Desktop...
if exist "%INSTALL_DIR%\BeamDrop.exe" goto :LAUNCH_INSTALLED
if exist "%~dp0BeamDrop.exe" goto :LAUNCH_LOCAL
if exist "%~dp0BeamDropApp.exe" goto :LAUNCH_LOCAL_APP
goto :COMPILE_ERROR

:LAUNCH_INSTALLED
echo   [OK] Installed at: %INSTALL_DIR%\BeamDrop.exe
echo   [OK] Publisher: Sajjad Hossain [sajjadhossain1508@gmail.com]
start "" "%INSTALL_DIR%\BeamDrop.exe"
echo.
echo BeamDrop Continuity is now running!
timeout /t 4 >nul
exit /b 0

:LAUNCH_LOCAL
echo   [OK] Launching "%~dp0BeamDrop.exe"...
start "" "%~dp0BeamDrop.exe"
timeout /t 4 >nul
exit /b 0

:LAUNCH_LOCAL_APP
echo   [OK] Launching "%~dp0BeamDropApp.exe"...
start "" "%~dp0BeamDropApp.exe"
timeout /t 4 >nul
exit /b 0

:COMPILE_ERROR
echo.
echo [ERROR] BeamDrop compilation or installation encountered an error.
echo Please inspect the messages above.
pause
exit /b 1
