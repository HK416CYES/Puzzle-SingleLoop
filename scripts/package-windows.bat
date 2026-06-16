@echo off
setlocal

set APP_NAME=Puzzle
set MAIN_CLASS=puzzle.PuzzleApp
set BUILD_DIR=build
set CLASS_DIR=%BUILD_DIR%\classes
set JAR_DIR=%BUILD_DIR%\jar
set JAR_FILE=%APP_NAME%.jar
set DIST_DIR=dist\windows

where javac >nul 2>nul
if errorlevel 1 (
    echo javac was not found. Install JDK 21 or newer and add it to PATH.
    exit /b 1
)

where jar >nul 2>nul
if errorlevel 1 (
    echo jar was not found. Install JDK 21 or newer and add it to PATH.
    exit /b 1
)

where jpackage >nul 2>nul
if errorlevel 1 (
    echo jpackage was not found. Install JDK 21 or newer and add it to PATH.
    exit /b 1
)

if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%CLASS_DIR%"
mkdir "%JAR_DIR%"
mkdir "%DIST_DIR%"

dir /s /b src\main\java\*.java > "%BUILD_DIR%\sources.txt"
javac -encoding UTF-8 -d "%CLASS_DIR%" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 exit /b 1

jar --create --file "%JAR_DIR%\%JAR_FILE%" --main-class "%MAIN_CLASS%" -C "%CLASS_DIR%" .
if errorlevel 1 exit /b 1

jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --input "%JAR_DIR%" ^
  --main-jar "%JAR_FILE%" ^
  --main-class "%MAIN_CLASS%" ^
  --dest "%DIST_DIR%" ^
  --app-version 1.0 ^
  --vendor Puzzle
if errorlevel 1 exit /b 1

echo Windows executable created at %DIST_DIR%\%APP_NAME%\%APP_NAME%.exe
endlocal
