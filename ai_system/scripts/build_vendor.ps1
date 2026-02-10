# build_vendor.ps1
#
# 作用（description）：
# - 使用内置的 Python 3.12 解释器，根据 requirements.txt 将依赖安装到 ai_system/vendor 目录喵。
# - 实现“离线化”依赖分发，确保玩家环境一致性喵。
#
# 注意事项（important_notes）：
# - 必须在运行 setup_runtime.ps1 之后执行喵。
# - 使用 --target 参数实现定向安装，不会污染任何外部环境喵。

$ErrorActionPreference = 'Stop'

$AiRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$RuntimeDir = Join-Path $AiRoot 'runtime\python312'
$PyExe = Join-Path $RuntimeDir 'python.exe'
$VendorDir = Join-Path $AiRoot 'vendor'
$ReqFile = Join-Path $AiRoot 'requirements.txt'

if (!(Test-Path $PyExe)) {
  throw "Runtime not found. Please run setup_runtime.ps1 first喵."
}

Write-Host "[build_vendor] Installing dependencies to $VendorDir ..."

# 确保 vendor 目录存在并清理喵。
if (Test-Path $VendorDir) { Remove-Item -Force -Recurse $VendorDir }
New-Item -ItemType Directory -Force -Path $VendorDir | Out-Null

# 使用内置 Python 运行 pip 安装到指定目录喵。
# 注意：embeddable 包默认没带 pip，我们需要通过 get-pip.py 引导喵。
$GetPipScript = Join-Path $AiRoot 'runtime\_tmp\get-pip.py'
if (!(Test-Path $GetPipScript)) {
    Invoke-WebRequest -Uri "https://bootstrap.pypa.io/get-pip.py" -OutFile $GetPipScript
}

& $PyExe $GetPipScript --no-warn-script-location
if ($LASTEXITCODE -ne 0) {
  throw "get-pip failed with exitCode=$LASTEXITCODE"
}

# embeddable 默认隔离模式：需要允许 site 才能 import pip / site-packages 喵。
# 我们不在此写入系统环境变量，仅在当前进程内设置 PYTHONPATH 指向 vendor 目标目录喵。
$env:PYTHONPATH = $VendorDir

& $PyExe -m pip --version
if ($LASTEXITCODE -ne 0) {
  throw "pip not available after get-pip, exitCode=$LASTEXITCODE"
}

& $PyExe -m pip install --target $VendorDir -r $ReqFile --no-cache-dir
if ($LASTEXITCODE -ne 0) {
  throw "pip install failed with exitCode=$LASTEXITCODE"
}

Write-Host "[build_vendor] Success喵!"
