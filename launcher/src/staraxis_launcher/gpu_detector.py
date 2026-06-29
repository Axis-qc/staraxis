"""GPU 检测模块喵。

作用（description）：
- 检测系统所有显卡（集显/独显），返回结构化列表喵。
- 识别显卡类型（集成/独立），便于启动器推荐高性能 GPU 喵。

实现方式（implementation）：
- Windows：调用 PowerShell 的 Get-CimInstance Win32_VideoController 查询喵。
- 返回 JSON 由 Python 解析，避免依赖第三方 wmi 库喵。

数据结构（data_structure）：
- GpuInfo：dataclass，包含 name（名称）、driver（驱动版本）、vendor（厂商）、is_igpu（是否集显）喵。
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from dataclasses import dataclass
from typing import List


@dataclass
class GpuInfo:
    """GPU 信息喵。

    字段（fields）：
- name：显卡名称喵。
- driver：驱动版本喵。
- vendor：厂商（Intel/NVIDIA/AMD 等）喵。
- is_igpu：是否为集成显卡喵。
    """

    name: str
    driver: str = ""
    vendor: str = ""
    is_igpu: bool = False

    def __str__(self) -> str:
        tag = "[集显]" if self.is_igpu else "[独显]"
        return f"{tag} {self.name}"


# 集成显卡关键字（用于识别集显）喵
_IGPU_KEYWORDS = (
    "intel(r) iris",
    "intel(r) uhd",
    "intel(r) hd",
    "intel(r) graphics",
    "amd radeon(tm) graphics",  # AMD APU 集显
    "amd radeon graphics",
    "mali",
    "adreno",
)

# 虚拟显示器/虚拟显卡关键字（应排除）喵
_VIRTUAL_KEYWORDS = (
    "idd device",  # Indirect Display Device，虚拟显示器
    "virtual display",
    "gameviewer",  # GameViewer 虚拟显示适配器
    "mirror",  # 投屏虚拟显卡
    "remote",  # 远程显示
    "basic render",  # 微软基本渲染驱动（软件渲染）
    "microsoft basic",
)


def _is_virtual(name: str) -> bool:
    """判断是否为虚拟显卡/虚拟显示器（应排除）喵。"""
    lower = name.lower()
    return any(kw in lower for kw in _VIRTUAL_KEYWORDS)


def _is_igpu(name: str, vendor: str) -> bool:
    """根据名称和厂商判断是否为集成显卡喵。"""
    lower = name.lower()
    if any(kw in lower for kw in _IGPU_KEYWORDS):
        return True
    # Intel 厂商且非独立显卡型号（如 Arc 独显）喵
    if "intel" in vendor.lower() and "arc" not in lower:
        return True
    return False


def _parse_vendor(name: str) -> str:
    """从显卡名称推断厂商喵。"""
    lower = name.lower()
    if "nvidia" in lower or "geforce" in lower or "rtx" in lower or "quadro" in lower:
        return "NVIDIA"
    if "amd" in lower or "radeon" in lower:
        return "AMD"
    if "intel" in lower:
        return "Intel"
    return "Unknown"


def detect_gpus() -> List[GpuInfo]:
    """检测系统所有 GPU 喵。

    返回（returns）：
- List[GpuInfo]：真实显卡列表（已排除虚拟显示器）喵。
- 检测失败返回空列表喵。
    """
    if not sys.platform.startswith("win"):
        # 非 Windows 平台暂不支持自动检测喵
        return []

    # 用 PowerShell 查询显卡信息喵
    ps_cmd = (
        "Get-CimInstance Win32_VideoController | "
        "Select-Object Name, DriverVersion, VideoProcessor | ConvertTo-Json -Compress"
    )
    try:
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps_cmd],
            capture_output=True,
            text=True,
            timeout=15,
            encoding="utf-8",
            errors="replace",
        )
    except Exception:
        return []

    if result.returncode != 0 or not result.stdout.strip():
        return []

    # 解析 JSON（单卡返回对象，多卡返回数组）喵
    try:
        data = json.loads(result.stdout)
    except json.JSONDecodeError:
        return []

    if isinstance(data, dict):
        data = [data]

    gpus: List[GpuInfo] = []
    for item in data:
        name = (item.get("Name") or "").strip()
        if not name:
            continue
        # 排除虚拟显示器/虚拟显卡喵
        if _is_virtual(name):
            continue
        driver = (item.get("DriverVersion") or "").strip()
        vendor = _parse_vendor(name)
        is_igpu = _is_igpu(name, vendor)
        gpus.append(GpuInfo(name=name, driver=driver, vendor=vendor, is_igpu=is_igpu))

    return gpus


def recommend_high_performance(gpus: List[GpuInfo]) -> GpuInfo | None:
    """从 GPU 列表中推荐高性能独显喵。

    优先级（priority）：
- 1. 第一块非集显（独显）喵。
- 2. 列表第一块喵。
    """
    for g in gpus:
        if not g.is_igpu:
            return g
    return gpus[0] if gpus else None


def recommend_power_saving(gpus: List[GpuInfo]) -> GpuInfo | None:
    """从 GPU 列表中推荐节能集显喵。"""
    for g in gpus:
        if g.is_igpu:
            return g
    return gpus[0] if gpus else None