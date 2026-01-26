@echo off
setlocal

REM StarAxis Web 开发态一键启动（Windows）
REM - 启动 WebNet（服务器界面模式，17890）
REM - 启动 Web 前端 Vite dev server（5173）
REM - 打开服务器界面（http://127.0.0.1:17890/）

set ROOT=%~dp0

REM 1) 启动后端（新窗口）
start "staraxis-webnet" cmd /k "cd /d "%ROOT%" && call gradlew.bat :webnet:run -PserverUi"

REM 2) 启动前端（新窗口，固定端口 5173）
start "staraxis-web" cmd /k "cd /d "%ROOT%web" && npm run dev -- --port 5173 --strictPort"

REM 3) 打开服务器界面
timeout /t 2 >nul
start "" "http://127.0.0.1:17890/"

endlocal