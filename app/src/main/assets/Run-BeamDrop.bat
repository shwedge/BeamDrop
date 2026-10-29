@echo off
setlocal EnableDelayedExpansion
title BeamDrop Continuity Desktop - Publisher Sajjad H0ssain
color 0B

echo ============================================================================
echo   BeamDrop Continuity Engine v2.4.0.0 for Windows 10 and 11
echo   Publisher: Sajjad H0ssain [sajjadtheacid@gmail.com]
echo ============================================================================
echo.

cd /d "%~dp0"

set "INSTALL_DIR=%LOCALAPPDATA%\Programs\Sajjad H0ssain\BeamDrop"

echo [1/3] Removing Mark-of-the-Web Zone.Identifier blocks...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -Path '%~dp0' -Recurse -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue" >nul 2>&1

if exist "%INSTALL_DIR%\BeamDrop.exe" goto :LAUNCH_INSTALLED
if exist "%~dp0BeamDropApp.exe" goto :LAUNCH_APP
if exist "%~dp0BeamDrop.exe" goto :LAUNCH_EXE

echo [2/3] Compiling native 64-bit WPF BeamDrop.exe - Publisher Sajjad H0ssain...
set "CSC_PATH=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if exist "%CSC_PATH%" goto :FOUND_CSC
set "CSC_PATH=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if exist "%CSC_PATH%" goto :FOUND_CSC
echo [ERROR] .NET Framework v4.0.30319 csc.exe was not found on this machine.
pause
exit /b 1

:FOUND_CSC
set "WPF_DIR=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\WPF"
if exist "%WPF_DIR%\PresentationFramework.dll" goto :COMPILE_NOW
set "WPF_DIR=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\WPF"

:COMPILE_NOW
set "MANIFEST_ARG="
if exist "%~dp0BeamDrop.exe.manifest" set "MANIFEST_ARG=/win32manifest:"%~dp0BeamDrop.exe.manifest""

"%CSC_PATH%" /nologo /target:winexe /out:"%~dp0BeamDrop.exe" /Platform:anycpu /optimize+ %MANIFEST_ARG% /r:"%WPF_DIR%\PresentationCore.dll" /r:"%WPF_DIR%\PresentationFramework.dll" /r:"%WPF_DIR%\WindowsBase.dll" /r:System.Xaml.dll /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Net.Http.dll "%~dp0BeamDropWindows.cs"
if errorlevel 1 goto :BUILD_FAILED

copy /Y "%~dp0BeamDrop.exe" "%~dp0BeamDropApp.exe" >nul 2>&1
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%" >nul 2>&1
copy /Y "%~dp0BeamDrop.exe" "%INSTALL_DIR%\BeamDrop.exe" >nul 2>&1
goto :LAUNCH_EXE

:LAUNCH_INSTALLED
echo [3/3] Launching installed BeamDrop Continuity Desktop...
start "" "%INSTALL_DIR%\BeamDrop.exe"
exit /b 0

:LAUNCH_APP
echo [3/3] Launching BeamDropApp.exe...
start "" "%~dp0BeamDropApp.exe"
exit /b 0

:LAUNCH_EXE
echo [3/3] Launching BeamDrop.exe...
start "" "%~dp0BeamDrop.exe"
exit /b 0

:BUILD_FAILED
echo.
echo [ERROR] Failed to compile BeamDropWindows.cs.
pause
exit /b 1
