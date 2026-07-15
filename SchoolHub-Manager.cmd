@echo off
REM SchoolHub Manager — unified launcher. Double-click this file.
setlocal
set "PSCRIPT=%~dp0setup\SchoolHub-Manager.ps1"
if not exist "%PSCRIPT%" (
    echo Error: setup\SchoolHub-Manager.ps1 is missing.
    pause
    exit /b 1
)
where pwsh >nul 2>nul
if %ERRORLEVEL%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
)
exit /b %ERRORLEVEL%
