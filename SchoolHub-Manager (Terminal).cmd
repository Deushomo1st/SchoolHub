@echo off
REM SchoolHub Manager — launches in Windows Terminal with tab support
setlocal
cd /d "%~dp0"
where wt >nul 2>nul
if %ERRORLEVEL%==0 (
    REM Try current window; fall back to new Terminal window
    wt -w 0 nt --title "SchoolHub Manager" cmd /c SchoolHub-Manager.cmd 2>nul
    if %ERRORLEVEL% NEQ 0 (
        start "" wt nt --title "SchoolHub Manager" cmd /c SchoolHub-Manager.cmd
    )
) else (
    where pwsh >nul 2>nul
    if %ERRORLEVEL%==0 (
        pwsh -NoProfile -ExecutionPolicy Bypass -File "setup\SchoolHub-Manager.ps1"
    ) else (
        powershell -NoProfile -ExecutionPolicy Bypass -File "setup\SchoolHub-Manager.ps1"
    )
)
exit /b %ERRORLEVEL%
