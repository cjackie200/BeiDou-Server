@echo off
chcp 65001 >nul
title BeiDou Server Launcher

set "SCRIPT_DIR=%~dp0"
set "PS1_FILE=%SCRIPT_DIR%start-windows-server.ps1"

if not exist "%PS1_FILE%" (
    echo ERROR: Cannot find %PS1_FILE%
    pause
    exit /b 1
)

echo.
echo ==================================================
echo        BeiDou Server - Windows Launcher
echo ==================================================
echo.
echo    [1] Start server       (start)
echo    [2] Stop server        (stop)
echo    [3] Show status        (status)
echo    [4] Start MySQL only   (mysql-start)
echo    [0] Exit
echo.

set /p CHOICE="Select [1]: "
if "%CHOICE%"=="" set CHOICE=1

set ACTION=start
if "%CHOICE%"=="1" set ACTION=start
if "%CHOICE%"=="2" set ACTION=stop
if "%CHOICE%"=="3" set ACTION=status
if "%CHOICE%"=="4" set ACTION=mysql-start
if "%CHOICE%"=="0" exit /b 0

echo.
echo Running: %ACTION%
echo.

set "PWSH_EXE="
if exist "D:\App\PowerShell\7\pwsh.exe" set "PWSH_EXE=D:\App\PowerShell\7\pwsh.exe"

if not defined PWSH_EXE (
    for /f "delims=" %%P in ('where pwsh.exe 2^>nul') do (
        if not defined PWSH_EXE set "PWSH_EXE=%%P"
    )
)

if not defined PWSH_EXE (
    echo ERROR: Cannot find PowerShell 7 pwsh.exe
    echo Please install PowerShell 7 or add pwsh.exe to PATH.
    pause
    exit /b 1
)

echo PowerShell: %PWSH_EXE%
"%PWSH_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1_FILE%" -Action %ACTION%

echo.
echo --------------------------------------------------
echo Done.
pause
