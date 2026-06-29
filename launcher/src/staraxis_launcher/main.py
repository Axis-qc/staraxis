"""StarAxis 启动器入口喵。

作用（description）：
- 启动器主入口，初始化并启动 GUI 喵。
- 支持命令行参数 --cli 进入纯命令行模式（无 GUI）喵。

使用方式（usage）：
- GUI 模式：python -m staraxis_launcher.main 喵。
- CLI 模式：python -m staraxis_launcher.main --cli 喵。
"""

from __future__ import annotations

import argparse
import sys


def main() -> int:
    """启动器主入口喵。"""
    parser = argparse.ArgumentParser(description="StarAxis 游戏启动器喵")
    parser.add_argument(
        "--cli",
        action="store_true",
        help="使用命令行模式（无 GUI）喵",
    )
    args = parser.parse_args()

    if args.cli:
        return _run_cli()
    return _run_gui()


def _run_gui() -> int:
    """启动 GUI 模式喵。"""
    try:
        import tkinter  # noqa: F401  检查 tkinter 可用性喵
    except ImportError:
        print("错误：当前 Python 环境不支持 tkinter，请用 --cli 模式或安装 tkinter 喵")
        return 1

    from .gui import LauncherWindow

    app = LauncherWindow()
    app.run()
    return 0


def _run_cli() -> int:
    """启动命令行模式喵。"""
    from .game_launcher import detect_launch_modes, launch_game
    from .gpu_detector import detect_gpus, recommend_high_performance
    from .gpu_preference import GPU_PREF_HIGH_PERFORMANCE, pref_label

    print("=" * 50)
    print("StarAxis 启动器（命令行模式）")
    print("=" * 50)

    # 检测 GPU 喵
    gpus = detect_gpus()
    if not gpus:
        print("未检测到 GPU 喵")
    else:
        print(f"\n检测到 {len(gpus)} 块 GPU：")
        for i, g in enumerate(gpus):
            tag = "集显" if g.is_igpu else "独显"
            print(f"  [{i}] {tag} | {g.vendor} | {g.name}")
        rec = recommend_high_performance(gpus)
        if rec:
            print(f"\n推荐高性能 GPU：{rec.name}")

    # 检测启动模式喵
    modes = detect_launch_modes()
    if not modes:
        print("\n未找到可用启动模式喵")
        return 1

    print("\n可用启动模式：")
    for i, m in enumerate(modes):
        print(f"  [{i}] {m.label}")
        if m.java_exe:
            from .gpu_preference import get_gpu_preference

            pref = get_gpu_preference(m.java_exe)
            print(f"      java.exe: {m.java_exe}")
            print(f"      当前 GPU 偏好: {pref_label(pref)}")

    # 简单交互：选模式并启动喵
    try:
        choice = input(f"\n选择启动模式 [0-{len(modes)-1}]（回车=0）：").strip()
        idx = int(choice) if choice else 0
    except (ValueError, EOFError):
        idx = 0

    if not (0 <= idx < len(modes)):
        print("无效选择喵")
        return 1

    mode = modes[idx]
    # 默认应用高性能偏好喵
    if mode.java_exe:
        from .game_launcher import apply_gpu_preference

        ok, msg = apply_gpu_preference(mode, GPU_PREF_HIGH_PERFORMANCE)
        print(msg)

    ok, msg = launch_game(mode)
    print(msg)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())