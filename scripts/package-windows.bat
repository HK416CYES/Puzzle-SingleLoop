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

call :resolve_jdk_tool javac JAVAC_CMD
if errorlevel 1 goto fail
call :resolve_jdk_tool jar JAR_CMD
if errorlevel 1 goto fail
call :resolve_jdk_tool jpackage JPACKAGE_CMD
if errorlevel 1 goto fail

echo Cleaning previous build...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%CLASS_DIR%"
mkdir "%JAR_DIR%"
mkdir "%DIST_DIR%"

echo Compiling Java sources...
dir /s /b src\puzzle\*.java > "%BUILD_DIR%\sources.txt"
"%JAVAC_CMD%" -encoding UTF-8 -d "%CLASS_DIR%" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 goto fail

echo Creating runnable jar...
"%JAR_CMD%" --create --file "%JAR_DIR%\%JAR_FILE%" --main-class "%MAIN_CLASS%" -C "%CLASS_DIR%" .
if errorlevel 1 goto fail

echo Creating Windows app image...
"%JPACKAGE_CMD%" ^
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

:resolve_jdk_tool
set "%~2="
call :try_tool_from_path %~1 %~2
if defined %~2 exit /b 0

call :try_tool_from_java_home %~1 %~2
if defined %~2 exit /b 0

call :try_tool_from_installed_jdks %~1 %~2
if defined %~2 exit /b 0

echo %~1 was not found in a JDK 21+ installation. Install JDK 21 or newer, or update PATH/JAVA_HOME.
exit /b 1

:try_tool_from_path
for /f "delims=" %%I in ('where %~1 2^>nul') do (
    call :is_java21_or_newer "%%~fI"
    if not errorlevel 1 (
        set "%~2=%%~fI"
        exit /b 0
    )
)
exit /b 0

:try_tool_from_java_home
if not defined JAVA_HOME exit /b 0
call :try_explicit_tool "%JAVA_HOME%\bin\%~1.exe" %~2
exit /b 0

:try_tool_from_installed_jdks
for /d %%D in ("C:\Program Files\Java\jdk-*") do (
    call :try_explicit_tool "%%~fD\bin\%~1.exe" %~2
    if defined %~2 exit /b 0
)
exit /b 0

:try_explicit_tool
if not exist "%~1" exit /b 0
call :is_java21_or_newer "%~1"
if errorlevel 1 exit /b 0
set "%~2=%~1"
exit /b 0

:is_java21_or_newer
set "CHECK_DIR=%~dp1..\"

if exist "%CHECK_DIR%release" (
    for /f "tokens=2 delims==" %%V in ('findstr /b "JAVA_VERSION=" "%CHECK_DIR%release" 2^>nul') do (
        for /f "tokens=1 delims=." %%M in ("%%~V") do (
            if %%M GEQ 21 exit /b 0
        )
    )
)
exit /b 1

:fail
echo.
echo Packaging failed. See the message above for details.
echo.
popd >nul
pause
exit /b 1
