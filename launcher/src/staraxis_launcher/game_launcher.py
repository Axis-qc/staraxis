"""游戏启动模块喵。

作用（description）：
- 负责定位游戏可执行入口并启动游戏喵。
- 支持两种启动模式：开发模式（gradlew run）和发布模式（StarAxis.bat）喵。

启动模式（launch_modes）：
- dev：开发模式，调用 gradlew :lwjgl3:run，使用项目 JDK 喵。
- release：发布模式，调用 StarAxis Game\\StarAxis.bat，使用打包 JDK 喵。

GPU 偏好应用（gpu_pref_apply）：
- 启动前根据用户选择，给对应模式的 java.exe 路径写注册表 GPU 偏好喵。
- dev 模式：从 gradle.properties 的 org.gradle.java.home 读取 JDK 路径喵。
- release 模式：从 StarAxis Game\\openjdk-21.0.2\\bin\\java.exe 读取喵。
"""

from __future__ import annotations

import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Tuple

from .gpu_preference import (
    GPU_PREF_HIGH_PERFORMANCE,
    GPU_PREF_POWER_SAVING,
    set_gpu_preference,
)


@dataclass
class LaunchMode:
    """启动模式描述喵。

    字段（fields）：
- key：模式标识（dev/release）喵。
- label：显示名称喵。
- java_exe：对应的 java.exe 完整路径（用于写注册表 GPU 偏好）喵。
- cmd：启动命令列表喵。
- cwd：工作目录喵。
    """

    key: str
    label: str
    java_exe: str | None
    cmd: List[str]
    cwd: str


def _project_root() -> Path:
    """定位项目根目录喵。

    策略（strategy）：
- 启动器位于 <root>/launcher/，向上两级即项目根喵。
- 打包成 exe 后，用 exe 所在目录的上级喵。
    """
    if getattr(sys, "frozen", False):
        # PyInstaller 打包后喵
        return Path(sys.executable).resolve().parent.parent
    # 开发环境：launcher/src/staraxis_launcher/game_launcher.py -> 上三级喵
    return Path(__file__).resolve().parents[3]


def _read_gradle_jdk() -> str | None:
    """从 gradle.properties 读取 org.gradle.java.home 喵。"""
    root = _project_root()
    props = root / "gradle.properties"
    if not props.exists():
        return None
    try:
        for line in props.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("org.gradle.java.home"):
                # 格式：org.gradle.java.home=C:\\path\\to\\jdk 喵
                val = line.split("=", 1)[1].strip()
                # 去掉可能的引号喵
                val = val.strip("'\"")
                return str(Path(val) / "bin" / "java.exe")
    except Exception:
        return None
    return None


def detect_launch_modes() -> List[LaunchMode]:
    """检测可用的启动模式喵。

    返回（returns）：
- List[LaunchMode]：所有可用的启动模式（dev/release）喵。
- 不可用的模式会被跳过喵。
    """
    root = _project_root()
    modes: List[LaunchMode] = []

    # 开发模式：gradlew :lwjgl3:run 喵
    gradlew = root / "gradlew.bat" if sys.platform.startswith("win") else root / "gradlew"
    if gradlew.exists():
        java_exe = _read_gradle_jdk()
        modes.append(
            LaunchMode(
                key="dev",
                label="开发模式 (gradlew run)",
                java_exe=java_exe,
                cmd=[str(gradlew), ":lwjgl3:run"],
                cwd=str(root),
            )
        )

    # 发布模式：StarAxis Game\StarAxis.bat 喵
    staraxis_bat = root / "StarAxis Game" / "StarAxis.bat"
    if staraxis_bat.exists():
        release_java = root / "StarAxis Game" / "openjdk-21.0.2" / "bin" / "java.exe"
        modes.append(
            LaunchMode(
                key="release",
                label="发布模式 (StarAxis.bat)",
                java_exe=str(release_java) if release_java.exists() else None,
                cmd=[str(staraxis_bat)],
                cwd=str(root / "StarAxis Game"),
            )
        )

    return modes


def apply_gpu_preference(mode: LaunchMode, pref: int) -> Tuple[bool, str]:
    """给启动模式对应的 java.exe 写 GPU 偏好喵。

    参数（args）：
- mode：启动模式喵。
- pref：GPU_PREF_HIGH_PERFORMANCE 或 GPU_PREF_POWER_SAVING 喵。

    返回（returns）：
- (success, message) 喵。
    """
    if not mode.java_exe:
        return False, "该启动模式未找到 java.exe 路径，无法设置 GPU 偏好喵"
    if not Path(mode.java_exe).exists():
        return False, f"java.exe 不存在：{mode.java_exe} 喵"
    return set_gpu_preference(mode.java_exe, pref)


def launch_game(mode: LaunchMode) -> Tuple[bool, str]:
    """启动游戏喵。

    参数（args）：
- mode：选定的启动模式喵。

    返回（returns）：
- (success, message) 喵。
- 启动成功后本进程不阻塞，游戏在新进程运行喵。
    """
    if not mode.cmd:
        return False, "启动命令为空喵"
    if not Path(mode.cmd[0]).exists():
        return False, f"启动入口不存在：{mode.cmd[0]} 喵"

    try:
        # 用独立进程启动游戏，不继承 stdout 喵
        subprocess.Popen(
            mode.cmd,
            cwd=mode.cwd,
            # 释放标准输入输出，避免启动器等待喵
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            close_fds=True,
        )
        return True, f"已启动游戏（{mode.label}）喵"
    except Exception as e:
        return False, f"启动失败：{e} 喵"