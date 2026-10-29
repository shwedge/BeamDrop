@echo off
setlocal
cd /d "%~dp0"
set "PROJ_DIR=%CD%"
title Opening BeamDrop in Android Studio...
echo Starting Android Studio for: %PROJ_DIR%
start "" "C:\Program Files\Android\Android Studio\bin\studio64.exe" "%PROJ_DIR%"
