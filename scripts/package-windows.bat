@echo off
setlocal

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..
pushd "%PROJECT_DIR%" >nul 2>nul
if errorlevel 1 (
    echo Failed to enter project directory: %PROJECT_DIR%
    pause
    exit /b 1
)

set APP_NAME=Puzzle
set MAIN_CLASS=puzzle.PuzzleApp
set BUILD_DIR=build
set CLASS_DIR=%BUILD_DIR%\classes
set JAR_DIR=%BUILD_DIR%\jar
set JAR_FILE=%APP_NAME%.jar
set DIST_DIR=dist\windows

call :require_tool javac "Install JDK 21 or newer and add it to PATH."
if errorlevel 1 goto fail
call :require_tool jar "Install JDK 21 or newer and add it to PATH."
if errorlevel 1 goto fail
call :require_tool jpackage "Install JDK 21 or newer and add it to PATH."
if errorlevel 1 goto fail

echo Cleaning previous build...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%CLASS_DIR%"
mkdir "%JAR_DIR%"
mkdir "%DIST_DIR%"

echo Compiling Java sources...
dir /s /b src\puzzle\*.java > "%BUILD_DIR%\sources.txt"
javac -encoding UTF-8 -d "%CLASS_DIR%" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 goto fail

echo Creating runnable jar...
jar --create --file "%JAR_DIR%\%JAR_FILE%" --main-class "%MAIN_CLASS%" -C "%CLASS_DIR%" .
if errorlevel 1 goto fail

echo Creating Windows app image...
jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --input "%JAR_DIR%" ^
  --main-jar "%JAR_FILE%" ^
  --main-class "%MAIN_CLASS%" ^
  --dest "%DIST_DIR%" ^
  --app-version 1.0 ^
  --vendor Puzzle
if errorlevel 1 goto fail

echo.
echo Windows executable created at:
echo %CD%\%DIST_DIR%\%APP_NAME%\%APP_NAME%.exe
echo.
popd >nul
pause
exit /b 0

:require_tool
where %~1 >nul 2>nul
if errorlevel 1 (
    echo %~1 was not found. %~2
    exit /b 1
)
exit /b 0

:fail
echo.
echo Packaging failed. See the message above for details.
echo.
popd >nul
pause
exit /b 1
