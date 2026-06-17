@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
pushd "%PROJECT_DIR%" >nul 2>nul
if errorlevel 1 (
    echo Failed to enter project directory: %PROJECT_DIR%
    pause
    exit /b 1
)

set "APP_NAME=Puzzle"
set "MAIN_CLASS=puzzle.PuzzleApp"
set "BUILD_DIR=build"
set "CLASS_DIR=%BUILD_DIR%\classes"
set "JAR_DIR=%BUILD_DIR%\jar"
set "JAR_FILE=%APP_NAME%.jar"
set "DIST_DIR=dist\windows"
set "APP_VERSION=0.2"

call :resolve_latest_jdk
if errorlevel 1 goto fail

set "JAVAC_CMD=%JDK_HOME%\bin\javac.exe"
set "JAR_CMD=%JDK_HOME%\bin\jar.exe"
set "JPACKAGE_CMD=%JDK_HOME%\bin\jpackage.exe"

echo Using JDK: %JDK_HOME%
"%JAVAC_CMD%" -version
if errorlevel 1 goto fail

echo Cleaning previous build...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%CLASS_DIR%"
mkdir "%JAR_DIR%"
mkdir "%DIST_DIR%"

echo Compiling Java sources...
dir /s /b src\puzzle\*.java > "%BUILD_DIR%\sources.txt"
"%JAVAC_CMD%" --release 17 -encoding UTF-8 -d "%CLASS_DIR%" @"%BUILD_DIR%\sources.txt"
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
  --app-version "%APP_VERSION%" ^
  --vendor Puzzle
if errorlevel 1 goto fail

echo.
echo Windows executable created at:
echo %CD%\%DIST_DIR%\%APP_NAME%\%APP_NAME%.exe
echo.
popd >nul
pause
exit /b 0

:resolve_latest_jdk
set "JDK_HOME="
set "JDK_FIND_SCRIPT=%TEMP%\puzzle-find-latest-jdk-%RANDOM%.ps1"
> "%JDK_FIND_SCRIPT%" echo $ErrorActionPreference = 'SilentlyContinue'
>> "%JDK_FIND_SCRIPT%" echo $candidates = New-Object System.Collections.Generic.List[string]
>> "%JDK_FIND_SCRIPT%" echo if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
>> "%JDK_FIND_SCRIPT%" echo foreach ($tool in @('javac.exe','jpackage.exe','java.exe')) {
>> "%JDK_FIND_SCRIPT%" echo   foreach ($cmd in (Get-Command $tool -All ^| Where-Object { $_.Source })) {
>> "%JDK_FIND_SCRIPT%" echo     $bin = Split-Path -Parent $cmd.Source
>> "%JDK_FIND_SCRIPT%" echo     $home = Split-Path -Parent $bin
>> "%JDK_FIND_SCRIPT%" echo     $candidates.Add($home)
>> "%JDK_FIND_SCRIPT%" echo   }
>> "%JDK_FIND_SCRIPT%" echo }
>> "%JDK_FIND_SCRIPT%" echo foreach ($pattern in @(
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\Java\jdk-*',
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\Eclipse Adoptium\jdk-*',
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\Microsoft\jdk-*',
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\Amazon Corretto\jdk*',
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\BellSoft\LibericaJDK-*',
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\Zulu\zulu-*',
>> "%JDK_FIND_SCRIPT%" echo   'C:\Program Files\Semeru\jdk-*'
>> "%JDK_FIND_SCRIPT%" echo )) {
>> "%JDK_FIND_SCRIPT%" echo   Get-ChildItem -Directory $pattern ^| ForEach-Object { $candidates.Add($_.FullName) }
>> "%JDK_FIND_SCRIPT%" echo }
>> "%JDK_FIND_SCRIPT%" echo $valid = foreach ($home in ($candidates ^| Where-Object { $_ } ^| Select-Object -Unique)) {
>> "%JDK_FIND_SCRIPT%" echo   $release = Join-Path $home 'release'
>> "%JDK_FIND_SCRIPT%" echo   $javac = Join-Path $home 'bin\javac.exe'
>> "%JDK_FIND_SCRIPT%" echo   $jar = Join-Path $home 'bin\jar.exe'
>> "%JDK_FIND_SCRIPT%" echo   $jpackage = Join-Path $home 'bin\jpackage.exe'
>> "%JDK_FIND_SCRIPT%" echo   if (!(Test-Path $release) -or !(Test-Path $javac) -or !(Test-Path $jar) -or !(Test-Path $jpackage)) { continue }
>> "%JDK_FIND_SCRIPT%" echo   $line = Select-String -Path $release -Pattern '^JAVA_VERSION=' ^| Select-Object -First 1
>> "%JDK_FIND_SCRIPT%" echo   if (!$line) { continue }
>> "%JDK_FIND_SCRIPT%" echo   $versionText = ($line.Line -replace '^JAVA_VERSION=','').Trim('"')
>> "%JDK_FIND_SCRIPT%" echo   $majorText = ($versionText -split '[._+-]')[0]
>> "%JDK_FIND_SCRIPT%" echo   $major = 0
>> "%JDK_FIND_SCRIPT%" echo   if (![int]::TryParse($majorText, [ref]$major)) { continue }
>> "%JDK_FIND_SCRIPT%" echo   if ($major -lt 17) { continue }
>> "%JDK_FIND_SCRIPT%" echo   [pscustomobject]@{ Home = $home; Version = [version](($versionText -replace '[^0-9.]','.') -replace '\.+','.' -replace '^\.|\.$','') }
>> "%JDK_FIND_SCRIPT%" echo }
>> "%JDK_FIND_SCRIPT%" echo $best = $valid ^| Sort-Object Version, Home -Descending ^| Select-Object -First 1
>> "%JDK_FIND_SCRIPT%" echo if ($best) { Write-Output $best.Home; exit 0 }
>> "%JDK_FIND_SCRIPT%" echo exit 1

for /f "usebackq delims=" %%J in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%JDK_FIND_SCRIPT%"`) do (
    set "JDK_HOME=%%J"
)
set "JDK_FIND_ERROR=%ERRORLEVEL%"
if exist "%JDK_FIND_SCRIPT%" del "%JDK_FIND_SCRIPT%" >nul 2>nul

if not "%JDK_FIND_ERROR%"=="0" (
    echo No JDK 17+ with javac, jar, and jpackage was found.
    echo Install the latest JDK 17 or newer, or update JAVA_HOME/PATH.
    exit /b 1
)
if not defined JDK_HOME (
    echo No JDK 17+ with javac, jar, and jpackage was found.
    echo Install the latest JDK 17 or newer, or update JAVA_HOME/PATH.
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
