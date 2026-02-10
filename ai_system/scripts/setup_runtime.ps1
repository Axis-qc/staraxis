# setup_runtime.ps1
#
# 作用（description）：
# - 下载并解压 Windows Python 3.12 embeddable 包到 ai_system/runtime/python312 喵。
# - 该脚本用于“构建期”，最终发行包应包含 runtime/python312 的产物喵。
#
# 提供的接口/API：
# - 直接运行本脚本：powershell -ExecutionPolicy Bypass -File .\ai_system\scripts\setup_runtime.ps1 喵。
#
# 使用方式（usage）：
# - 在项目根目录运行喵。
# - 可通过环境变量 PY_EMBED_VERSION 覆盖默认版本（例如 3.12.7）喵。
#
# 注意事项（important_notes）：
# - 该脚本会发起网络下载请求（访问 python.org）喵。
# - 若你使用离线/内网镜像，请将 $BaseUrl 替换为镜像地址喵。
# - Windows embeddable 包默认启用 ._pth 隔离模式，会导致 pip 安装成功但无法 import 的问题喵。
# - 本脚本会在解压后自动修改 python312._pth：启用 import site，并追加 vendor/src 相对路径，确保后续 build_vendor 与运行期可用喵。

$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$AiRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$RuntimeDir = Join-Path $AiRoot 'runtime\python312'

$Version = $env:PY_EMBED_VERSION
if ([string]::IsNullOrWhiteSpace($Version)) {
  $Version = '3.12.7'
}

$ZipName = "python-$Version-embed-amd64.zip"
$BaseUrl = "https://www.python.org/ftp/python/$Version"
$Url = "$BaseUrl/$ZipName"
$TmpDir = Join-Path $AiRoot 'runtime\_tmp'
$ZipPath = Join-Path $TmpDir $ZipName

Write-Host "[setup_runtime] projectRoot=$ProjectRoot" 
Write-Host "[setup_runtime] aiRoot=$AiRoot" 
Write-Host "[setup_runtime] runtimeDir=$RuntimeDir" 
Write-Host "[setup_runtime] version=$Version" 
Write-Host "[setup_runtime] url=$Url" 

New-Item -ItemType Directory -Force -Path $TmpDir | Out-Null
New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null

# 清理旧 runtime（保留目录）喵。
Get-ChildItem -Path $RuntimeDir -Force | Remove-Item -Force -Recurse

Write-Host "[setup_runtime] downloading..." 
Invoke-WebRequest -Uri $Url -OutFile $ZipPath

Write-Host "[setup_runtime] extracting..." 
Expand-Archive -Path $ZipPath -DestinationPath $RuntimeDir -Force

# 修改 ._pth 文件以解除隔离并启用 site 喵
$PthFile = Join-Path $RuntimeDir 'python312._pth'
if (Test-Path $PthFile) {
    Write-Host "[setup_runtime] patching $PthFile ..."
    $Content = Get-Content $PthFile
    $NewContent = @()
    foreach ($Line in $Content) {
        if ($Line.Trim() -eq "#import site") {
            $NewContent += "import site"
        } else {
            $NewContent += $Line
        }
    }
    # 追加依赖和源码路径喵
    $NewContent += "../../vendor"
    $NewContent += "../../src"
    # 使用 UTF8 无 BOM 编码保存，避免 Python 启动时 init_fs_encoding 失败喵
    $Utf8NoBom = New-Object System.Text.UTF8Encoding $False
    [System.IO.File]::WriteAllLines($PthFile, $NewContent, $Utf8NoBom)
}

# 基础校验喵。
$PyExe = Join-Path $RuntimeDir 'python.exe'
if (!(Test-Path $PyExe)) {
  throw "python.exe not found after extract: $PyExe"
}

Write-Host "[setup_runtime] done: $PyExe" 
