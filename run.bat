@echo off
setlocal enabledelayedexpansion

echo ==========================================
echo Order Entry Application - Automated Setup
echo ==========================================
echo.

cd /d "%~dp0"

REM Check for reset flag
set RESET_DB=0
if /i "%~1"=="--reset" set RESET_DB=1
if /i "%~1"=="/reset" set RESET_DB=1
if /i "%~1"=="-r" set RESET_DB=1

java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo Java found:
    java -version
) else (
    echo Java not found. Please install OpenJDK 11+ from:
    echo https://adoptium.net/ or download directly JDK 11 from github: https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.28%%2B7/OpenJDK11U-jdk_x64_windows_hotspot_11.0.28_7.msi
    echo.
    echo Or install using winget:
    echo   winget install EclipseAdoptium.Temurin.11.JDK
    echo.
    echo After installing Java, run this script again.
    pause
    exit /b 1
)

echo.
echo Downloading dependencies and building application...
echo.

call mvnw.cmd clean compile -q

if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

REM Delete database if reset flag was provided
if %RESET_DB%==1 (
    echo.
    echo Resetting database...
    if exist orderentry.db del /q orderentry.db
    echo Database reset complete.
)

echo.
echo ==========================================
echo Setup Complete!
echo ==========================================
echo.
echo Starting application...
echo (Use "run.bat --reset" to reset the database)
echo.

call mvnw.cmd -q exec:java
