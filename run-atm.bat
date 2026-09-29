@echo off
REM ---------------------------------------------------------------------------
REM ATM System - double click this file to run the whole app
REM Database settings are read from ATM_DB_* environment variables, or from
REM atm-db.properties next to this script, or -Datm.db.* JVM options.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

if not exist "target\atm-system.jar" (
  echo Building atm-system.jar ...
  call mvn -B -q package
  if errorlevel 1 (
    echo.
    echo BUILD FAILED. Install Maven and Java 17+ and run this file again.
    pause
    exit /b 1
  )
)

if exist "atm-db.properties" echo Using atm-db.properties for database settings.

java -jar target\atm-system.jar %*
if errorlevel 1 pause
endlocal
