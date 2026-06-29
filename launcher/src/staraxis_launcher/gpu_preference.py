"""GPU 偏好注册表设置模块喵。

作用（description）：
- 通过写 Windows 注册表设置指定 exe 的 GPU 偏好喵。
- 让 Windows 自动用高性能独显运行游戏，玩家无需手动去系统设置喵。

注册表路径（registry_path）：
- HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences 喵。
- 值名：exe 完整路径喵。
- 值数据：GpuPreference=2（高性能）或 GpuPreference=1（节能）喵。

约束（constraints）：
- 仅 Windows 10 1903+ 支持此功能喵。
- GpuPreference 只能选"高性能"或"节能"，不能指定具体哪块 GPU 喵。
- 要指定具体 GPU 需用户手动在 Windows 设置 > 系统 > 显示 > 图形 中操作喵。
"""

from __future__ import annotations

import sys
import winreg
from typing import Tuple

# 注册表根路径喵
_REG_PATH = r"Software\Microsoft\DirectX\UserGpuPreferences"

# GPU 偏好值喵
GPU_PREF_POWER_SAVING = 1  # 节能（集显）喵
GPU_PREF_HIGH_PERFORMANCE = 2  # 高性能（独显）喵


def _format_value(pref: int) -> str:
    """格式化注册表值数据喵。"""
    return f"GpuPreference={pref}"


def set_gpu_preference(exe_path: str, pref: int) -> Tuple[bool, str]:
    """设置指定 exe 的 GPU 偏好喵。

    参数（args）：
- exe_path：exe 完整路径（如 C:\\...\\java.exe）喵。
- pref：GPU_PREF_HIGH_PERFORMANCE 或 GPU_PREF_POWER_SAVING 喵。

    返回（returns）：
- (success, message)：成功与否及说明喵。
    """
    if not sys.platform.startswith("win"):
        return False, "仅 Windows 支持此功能喵"

    exe_path = exe_path.replace("/", "\\")
    try:
        # 打开或创建注册表键喵
        try:
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER, _REG_PATH, 0, winreg.KEY_SET_VALUE
            )
        except FileNotFoundError:
            key = winreg.CreateKey(winreg.HKEY_CURRENT_USER, _REG_PATH)

        winreg.SetValueEx(key, exe_path, 0, winreg.REG_SZ, _format_value(pref))
        winreg.CloseKey(key)
        return True, f"已设置 {exe_path} -> GpuPreference={pref} 喵"
    except PermissionError:
        return False, "权限不足，无法写注册表喵"
    except Exception as e:
        return False, f"写注册表失败：{e} 喵"


def get_gpu_preference(exe_path: str) -> int | None:
    """读取指定 exe 当前的 GPU 偏好喵。

    返回（returns）：
- 1（节能）/ 2（高性能）/ None（未设置）喵。
    """
    if not sys.platform.startswith("win"):
        return None

    exe_path = exe_path.replace("/", "\\")
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER, _REG_PATH, 0, winreg.KEY_READ
        )
        try:
            value, _ = winreg.QueryValueEx(key, exe_path)
        finally:
            winreg.CloseKey(key)
        # 解析 GpuPreference=N 喵
        if isinstance(value, str) and "GpuPreference=" in value:
            n = value.split("GpuPreference=")[-1].strip()
            return int(n)
        return None
    except FileNotFoundError:
        return None
    except Exception:
        return None


def clear_gpu_preference(exe_path: str) -> Tuple[bool, str]:
    """清除指定 exe 的 GPU 偏好设置（恢复系统默认）喵。"""
    if not sys.platform.startswith("win"):
        return False, "仅 Windows 支持此功能喵"

    exe_path = exe_path.replace("/", "\\")
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER, _REG_PATH, 0, winreg.KEY_SET_VALUE
        )
        winreg.DeleteValue(key, exe_path)
        winreg.CloseKey(key)
        return True, f"已清除 {exe_path} 的 GPU 偏好喵"
    except FileNotFoundError:
        return True, f"{exe_path} 未设置 GPU 偏好喵"
    except Exception as e:
        return False, f"清除注册表失败：{e} 喵"


def pref_label(pref: int | None) -> str:
    """GPU 偏好值转中文标签喵。"""
    if pref == GPU_PREF_HIGH_PERFORMANCE:
        return "高性能（独显）"
    if pref == GPU_PREF_POWER_SAVING:
        return "节能（集显）"
    return "系统默认"