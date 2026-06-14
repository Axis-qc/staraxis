@echo off
setlocal enabledelayedexpansion

REM StarAxis Native Client Launcher - Using bundled JDK

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

REM Check game jar exists
if not exist "%GAME_DIR%StarAxis.jar" (
    echo [ERROR] Game file not found: %GAME_DIR%StarAxis.jar
    pause
    exit /b 1
)

echo ==========================================
echo  StarAxis Space Strategy Game
echo ==========================================
echo Using bundled JDK: %JDK_DIR%
echo Game directory: %GAME_DIR%
echo ==========================================
echo.

REM Start native LWJGL3/OpenGL game client
REM -Xmx2G: Allocate 2GB memory (adjustable)
REM -Dfile.encoding=UTF-8: Force UTF-8 encoding
"%JAVA_EXE%" -Xmx2G -Dfile.encoding=UTF-8 -jar "%GAME_DIR%StarAxis.jar"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Game exited with error code: %ERRORLEVEL%
    pause
)

endlocal
