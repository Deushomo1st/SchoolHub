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

REM Strip the trailing backslash from %~dp0 - "...refix\" makes wt read \" as an
REM escaped quote and mangles the path (error 0x80070002). "...refix" is safe.
set "HERE=%~dp0"
set "HERE=%HERE:~0,-1%"

REM -d sets the working dir, so we can relaunch by bare filename (no spaces in it).
start "" wt -w 0 nt --title "SchoolHub Manager" -d "%HERE%" cmd /k SchoolHub-Manager.cmd
exit /b 0

:run
where pwsh >nul 2>nul
if %ERRORLEVEL%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
)
exit /b %ERRORLEVEL%
