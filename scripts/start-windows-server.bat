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
echo ╔══════════════════════════════════════════════════╗
echo ║        BeiDou Server - Windows Launcher         ║
echo ╚══════════════════════════════════════════════════╝
echo.
echo    [1] 启动服务端 (start)
echo    [2] 停止服务端 (stop)
echo    [3] 查看状态   (status)
echo    [4] 仅启动 MySQL (mysql-start)
echo    [0] 退出
echo.

set /p CHOICE="请选择 [1]: "
if "%CHOICE%"=="" set CHOICE=1

set ACTION=start
if "%CHOICE%"=="1" set ACTION=start
if "%CHOICE%"=="2" set ACTION=stop
if "%CHOICE%"=="3" set ACTION=status
if "%CHOICE%"=="4" set ACTION=mysql-start
if "%CHOICE%"=="0" exit /b 0

echo.
echo 正在执行: %ACTION%
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1_FILE%" -Action %ACTION%

echo.
echo ──────────────────────────────────────────────────
echo 脚本执行完毕。
pause
