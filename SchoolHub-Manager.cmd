@echo off
REM SchoolHub Manager - unified launcher. Double-click this file.
REM Auto-relaunches inside Windows Terminal so services open as TABS, not new windows.
setlocal
set "PSCRIPT=%~dp0setup\SchoolHub-Manager.ps1"
if not exist "%PSCRIPT%" (
    echo Error: setup\SchoolHub-Manager.ps1 is missing.
    pause
    exit /b 1
)

REM Already inside Windows Terminal? Skip straight to the manager.
if defined WT_SESSION goto run

REM Not in Windows Terminal - relaunch this script as a tab in the current WT window.
where wt >nul 2>nul
if errorlevel 1 goto run
start "" wt -w 0 nt --title "SchoolHub Manager" -d "%~dp0" cmd /c "%~f0"
exit /b 0

:run
where pwsh >nul 2>nul
if %ERRORLEVEL%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
)
exit /b %ERRORLEVEL%
