# Quickstart: 008 Server/Client Separation & Communication

## 目标

验证 008 的最小闭环：客户端通过 HTTP+JSON 请求服务端生成世界，并基于 WorldSnapshot 进入世界界面渲染；同时验证确定性、错误路径与性能门槛。

## 前置条件

- 已安装 JDK 21（项目使用 Gradle toolchain）。
- 本仓库可以通过 `gradlew.bat` 执行任务。

## Headless 门禁（core）

在仓库根目录执行：

- Windows:

```powershell
.\gradlew.bat :core:check
```

- macOS/Linux:

```bash
./gradlew :core:check
```

预期：任务通过（包含 `checkNoGraphicsDependencies`），确保 core 可在无图形环境下运行。

## 启动服务端（实现完成后）

在仓库根目录执行：

- Windows:

```powershell
.\gradlew.bat :server:run
```

- macOS/Linux:

```bash
./gradlew :server:run
```

预期：服务端启动后监听局域网地址与端口（可配置），并输出启动日志。

说明：

- 同机调试时，客户端默认连接 `http://127.0.0.1:8080`。
- 跨机器/局域网联调时，请将客户端的 serverBaseUrl 配置为 `http://<服务端IP>:8080`。
- MVP 未启用认证，仅用于开发/测试；请勿作为生产服务暴露。

## 使用 curl/postman 调用 API（可选）

### curl（macOS/Linux）

```bash
curl -sS -X POST "http://127.0.0.1:8080/api/worldgen/start-new-game" \
  -H "Content-Type: application/json" \
  -d '{
    "seedText":"demo-seed",
    "mapSizePresetId":"medium",
    "habitableRatio":0.30,
    "starDensity":0.60,
    "planetComplexity":0.50,
    "nebulaRatio":0.20
  }' \
  -o response.json
```

### PowerShell（Windows）

```powershell
$body = @{
  seedText = "demo-seed"
  mapSizePresetId = "medium"
  habitableRatio = 0.30
  starDensity = 0.60
  planetComplexity = 0.50
  nebulaRatio = 0.20
} | ConvertTo-Json

Invoke-WebRequest -Method Post -Uri "http://127.0.0.1:8080/api/worldgen/start-new-game" -ContentType "application/json" -Body $body | Select-Object -ExpandProperty Content
```

## 启动客户端

- Windows:

```powershell
.\gradlew.bat :lwjgl3:run
```

- macOS/Linux:

```bash
./gradlew :lwjgl3:run
```

## 手工验证清单

### 1) WorldGen 通讯闭环

1. 打开客户端“新游戏”界面。
2. 选择地图大小与生成参数，输入 seedText。
3. 点击开始。

预期：

- 客户端通过 HTTP 发送 StartNewGameRequest。
- 服务端返回 StartNewGameResponse（schemaVersion 固定为 `worldgen_v1`，并包含 WorldSnapshot 与 effectiveConfig）。
- 客户端进入世界界面渲染网格与星系标记（无需本地调用生成器）。

### 2) 确定性验证

1. 使用相同 seedText 与相同生成参数，连续创建两次新游戏。

预期：

- 服务端返回的统计摘要一致。
- 响应/快照中回填的 `seedValue` 一致。

### 3) 错误路径验证（本地化）

1. 故意输入非法参数（例如超出范围的比例）。

预期：

- 对于比例类越界参数：服务端进行 clamp 并在成功响应中通过 effectiveConfig 回填最终值（不应返回错误）。
- 对于不可修正的请求（例如 mapSizePresetId 不存在、JSON 结构错误）：服务端返回 ErrorEnvelope（errorCode + messageKey）。
- 客户端基于 messageKey 进行本地化展示，不直接展示 details。

### 4) 性能/规模门槛（默认地图规模）

默认地图规模定义：`mapSizePresetId=medium`。

预期：

- 服务端生成+序列化响应耗时 <= 5s。
- 客户端解析+进入世界耗时 <= 5s。
- WorldSnapshot JSON <= 20MB。

计时口径：

- 服务端：收到请求 -> 序列化完成并写出响应。
- 客户端：收到响应 -> 完成解析并进入世界界面。

测量方法（MVP 建议）：

- 服务端：查看 server 控制台输出 `WorldGen: status=..., durationMs=..., responseBytes=..., seedValue=...`。
- 客户端：查看客户端控制台输出 `WorldGen clientDurationMs=...`（口径：调用 startNewGame -> JSON 解析 -> WorldSnapshot 转 WorldMap）。
- 响应体字节数：
  - macOS/Linux：`curl -w "%{size_download}\n" -o response.json ...`
  - Windows：`(Invoke-WebRequest ...).Content.Length`
