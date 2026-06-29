@echo off
:: build_exe.bat
::
:: 作用（description）：
:: - 用 PyInstaller 把启动器打包成单 exe 喵。
:: - 打包后玩家无需安装 Python 即可运行喵。
::
:: 使用方式（usage）：
:: - 需先安装 PyInstaller：pip install pyinstaller 喵。
:: - 运行本脚本，产物在 dist\StarAxisLauncher.exe 喵。
::
:: 注意事项（important_notes）：
:: - 打包需系统 Python 带 tkinter 喵。
:: - 打包后 exe 约 10-15MB 喵。

setlocal
set "LAUNCHER_ROOT=%~dp0"
cd /d "%LAUNCHER_ROOT%"

echo [build_exe] Installing PyInstaller...
python -m pip install --upgrade pyinstaller >nul 2>&1

echo [build_exe] Building StarAxisLauncher.exe...

:: 用 PyInstaller 打包喵
:: --onefile: 单文件喵
:: --windowed: 无控制台窗口（GUI 程序）喵
:: --name: 输出文件名喵
:: --add-data: 打包资源喵
python -m PyInstaller ^
    --onefile ^
    --windowed ^
    --name StarAxisLauncher ^
    --paths "%LAUNCHER_ROOT%src" ^
    "%LAUNCHER_ROOT%src\staraxis_launcher\main.py"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] 打包失败喵
    pause
    exit /b 1
)

echo.
echo [build_exe] 打包成功喵
echo [build_exe] 产物: %LAUNCHER_ROOT%dist\StarAxisLauncher.exe
echo.
pause

endlocal