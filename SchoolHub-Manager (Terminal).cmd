@echo off
REM SchoolHub Manager — launches in Windows Terminal with tab support
setlocal
set "PSCRIPT=%~dp0setup\SchoolHub-Manager.ps1"
if not exist "%PSCRIPT%" (
    echo Error: setup\SchoolHub-Manager.ps1 is missing.
    pause
    exit /b 1
)
where wt >nul 2>nul
if %ERRORLEVEL%==0 (
    wt -w 0 nt --title "SchoolHub Manager" cmd /c "C:\Users\Deus\Desktop\Springworld\SchoolHub refix\SchoolHub-Manager.cmd"
) else (
    where pwsh >nul 2>nul
    if %ERRORLEVEL%==0 (
        pwsh -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
    ) else (
        powershell -NoProfile -ExecutionPolicy Bypass -File "%PSCRIPT%"
    )
)
exit /b %ERRORLEVEL%
