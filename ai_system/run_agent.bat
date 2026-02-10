@echo off
:: run_agent.bat
::
:: 作用（description）：
:: - 使用内置的 Python 3.12 嵌入版环境启动 AI 助手主程序喵。
:: - 自动设置 PYTHONPATH 指向 vendor 和 src 目录，确保解压即用喵。
::
:: 使用方式（usage）：
:: - 由 WebNetServer 在检测到玩家连接时自动调用喵。
:: - 也可以手动运行进行调试喵。
::
:: 注意事项（important_notes）：
:: - 运行前需确保执行过 scripts/setup_runtime.ps1 和 build_vendor.ps1 喵。
:: - 本脚本假设运行在 ai_system 目录下或通过项目根目录相对路径调用喵。

setlocal
set "AI_ROOT=%~dp0"
cd /d "%AI_ROOT%"

:: 设置 Python 嵌入版路径喵
set "PYTHONHOME=%AI_ROOT%runtime\python312"
:: 设置模块搜索路径，包含 vendor 依赖和 src 源码喵
set "PYTHONPATH=%AI_ROOT%vendor;%AI_ROOT%src"

echo [run_agent] Starting StarAxis AI Assistant...
echo [run_agent] AI_ROOT: %AI_ROOT%
echo [run_agent] PYTHONPATH: %PYTHONPATH%

if not exist "%PYTHONHOME%\python.exe" (
    echo [ERROR] Python runtime not found at %PYTHONHOME%\python.exe 喵！
    echo Please run scripts\setup_runtime.ps1 first 喵.
    exit /b 1
)

:: 启动主程序喵
"%PYTHONHOME%\python.exe" -m ai_system.main

if %ERRORLEVEL% neq 0 (
    echo [ERROR] AI Assistant exited with code %ERRORLEVEL% 喵.
)

pause
