@echo off
REM ============================================
REM  Model Test Scene (glTF model debug tool)
REM  Usage: lwjgl3\test\model_test.bat
REM
REM  Controls (in the model window):
REM    LMB drag   orbit camera
REM    Wheel      zoom
REM    RMB drag   pan
REM    F          toggle normal-map visualization
REM    Space      auto-rotate
REM    Q/E        light elevation
REM    A/D        light azimuth
REM    R          reset camera & light
REM
REM  Build output is NOT silenced: errors show directly.
REM ============================================

setlocal
cd /d "%~dp0\..\.."

echo ============================================
echo  Model Test Scene
echo ============================================
call gradlew.bat :lwjgl3:modelTest --console=plain -Dorg.gradle.logging.level=lifecycle 2>&1
set EXIT_CODE=%ERRORLEVEL%

echo.
echo ============================================
if "%EXIT_CODE%"=="0" (
    echo  Model test scene exited normally.
) else (
    echo  [FAIL] Model test scene exited with error code %EXIT_CODE%
)
echo ============================================
pause
exit /b %EXIT_CODE%
