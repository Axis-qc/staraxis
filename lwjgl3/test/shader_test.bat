@echo off
REM ============================================
REM  Shader Compile Test (GLSL)
REM  Usage: lwjgl3\test\shader_test.bat
REM  Compile-checks shaders in assets/shaders
REM  with multiple material flag combos.
REM
REM  Build output is NOT silenced: errors show directly.
REM ============================================

setlocal
cd /d "%~dp0\..\.."

echo ============================================
echo  Shader Compile Test (GLSL)
echo ============================================
call gradlew.bat :lwjgl3:shaderTest --console=plain -Dorg.gradle.logging.level=lifecycle 2>&1
set EXIT_CODE=%ERRORLEVEL%

echo.
echo ============================================
if "%EXIT_CODE%"=="0" (
    echo  [PASS] All shader combos compiled successfully.
) else (
    echo  [FAIL] Shader compile errors detected! See output above.
)
echo ============================================
pause
exit /b %EXIT_CODE%
