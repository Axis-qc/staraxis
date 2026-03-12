@echo off
setlocal enabledelayedexpansion

REM StarAxis Game Debug Launcher

set "GAME_DIR=%~dp0"
set "JDK_DIR=%GAME_DIR%openjdk-21.0.2"
set "JAVA_EXE=%JDK_DIR%\bin\java.exe"

REM Check bundled JDK exists
if not exist "%JAVA_EXE%" (
    echo [ERROR] Bundled JDK not found: %JAVA_EXE%
    echo Please ensure openjdk-21.0.2 folder is in the same directory.
    pause
    exit /b 1
)

echo ==========================================
echo  StarAxis Debug Mode
echo ==========================================
echo Java version:
"%JAVA_EXE%" -version
echo.
echo Game directory: %GAME_DIR%
echo ==========================================
echo.

REM Start with verbose logging
"%JAVA_EXE%" -Xmx2G -Dfile.encoding=UTF-8 -verbose:class -jar "%GAME_DIR%StarAxis.jar"

pause
