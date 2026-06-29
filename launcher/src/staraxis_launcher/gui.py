"""Tkinter 图形界面模块喵。

作用（description）：
- 提供启动器主窗口，让玩家选择 GPU 偏好和启动模式喵。
- 显示系统 GPU 列表、当前 GPU 偏好状态、启动按钮喵。

界面布局（layout）：
- 顶部：标题和说明喵。
- 中部：GPU 列表展示 + GPU 偏好选择（高性能/节能/系统默认）喵。
- 中下：启动模式选择喵。
- 底部：应用设置按钮 + 启动游戏按钮 + 状态栏喵。

约束（constraints）：
- 仅作展示和触发，不承载游戏逻辑喵。
- GPU 偏好通过注册表设置，需重启游戏进程生效喵。
"""

from __future__ import annotations

import threading
import tkinter as tk
from tkinter import messagebox, ttk
from typing import List, Optional

from .game_launcher import LaunchMode, apply_gpu_preference, detect_launch_modes, launch_game
from .gpu_detector import GpuInfo, detect_gpus, recommend_high_performance
from .gpu_preference import (
    GPU_PREF_HIGH_PERFORMANCE,
    GPU_PREF_POWER_SAVING,
    clear_gpu_preference,
    get_gpu_preference,
    pref_label,
)


class LauncherWindow:
    """启动器主窗口喵。

    职责（responsibilities）：
- 初始化时检测 GPU 和启动模式喵。
- 提供按钮让用户设置 GPU 偏好并启动游戏喵。
    """

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("StarAxis 启动器")
        self.root.geometry("640x560")
        self.root.minsize(560, 500)

        # 数据状态喵
        self.gpus: List[GpuInfo] = []
        self.modes: List[LaunchMode] = []
        self.selected_mode: Optional[LaunchMode] = None
        self.selected_pref_var = tk.IntVar(value=GPU_PREF_HIGH_PERFORMANCE)

        self._build_ui()
        self._async_detect()

    # ---------- UI 构建 ----------

    def _build_ui(self) -> None:
        """构建界面布局喵。"""
        # 顶部标题喵
        title_frame = ttk.Frame(self.root, padding=(16, 12, 16, 4))
        title_frame.pack(fill=tk.X)
        ttk.Label(
            title_frame,
            text="StarAxis 游戏启动器",
            font=("Microsoft YaHei UI", 16, "bold"),
        ).pack(anchor=tk.W)
        ttk.Label(
            title_frame,
            text="选择 GPU 偏好并启动游戏（双显卡笔记本建议选高性能独显）",
            font=("Microsoft YaHei UI", 9),
            foreground="#666",
        ).pack(anchor=tk.W, pady=(2, 0))

        # GPU 列表区喵
        gpu_frame = ttk.LabelFrame(self.root, text="系统 GPU 列表", padding=12)
        gpu_frame.pack(fill=tk.BOTH, expand=True, padx=16, pady=(8, 4))

        self.gpu_tree = ttk.Treeview(
            gpu_frame,
            columns=("type", "vendor", "driver"),
            show="headings",
            height=6,
        )
        self.gpu_tree.heading("type", text="类型")
        self.gpu_tree.heading("vendor", text="厂商")
        self.gpu_tree.heading("driver", text="驱动版本")
        self.gpu_tree.column("type", width=80, anchor=tk.CENTER)
        self.gpu_tree.column("vendor", width=100, anchor=tk.CENTER)
        self.gpu_tree.column("driver", width=160, anchor=tk.W)
        # 第一列（名称）用 tree 自带的 #0 喵
        self.gpu_tree.heading("#0", text="显卡名称")
        self.gpu_tree.column("#0", width=260, anchor=tk.W)
        self.gpu_tree.pack(fill=tk.BOTH, expand=True)

        # GPU 偏好选择区喵
        pref_frame = ttk.LabelFrame(self.root, text="GPU 偏好设置", padding=12)
        pref_frame.pack(fill=tk.X, padx=16, pady=(4, 4))

        ttk.Radiobutton(
            pref_frame,
            text="高性能（独显）- 推荐，画面流畅",
            variable=self.selected_pref_var,
            value=GPU_PREF_HIGH_PERFORMANCE,
        ).pack(anchor=tk.W)
        ttk.Radiobutton(
            pref_frame,
            text="节能（集显）- 省电，性能较弱",
            variable=self.selected_pref_var,
            value=GPU_PREF_POWER_SAVING,
        ).pack(anchor=tk.W)
        ttk.Radiobutton(
            pref_frame,
            text="系统默认（清除设置，由 Windows 决定）",
            variable=self.selected_pref_var,
            value=0,
        ).pack(anchor=tk.W)

        hint_label = ttk.Label(
            pref_frame,
            text="提示：GPU 偏好通过 Windows 注册表设置，需重启游戏进程生效喵。\n"
            "如需指定具体某块 GPU，请到 Windows 设置 > 系统 > 显示 > 图形 手动操作喵。",
            font=("Microsoft YaHei UI", 8),
            foreground="#888",
            wraplength=560,
            justify=tk.LEFT,
        )
        hint_label.pack(anchor=tk.W, pady=(6, 0))

        # 启动模式选择区喵
        mode_frame = ttk.LabelFrame(self.root, text="启动模式", padding=12)
        mode_frame.pack(fill=tk.X, padx=16, pady=(4, 4))
        self.mode_var = tk.StringVar()
        self.mode_combo = ttk.Combobox(
            mode_frame,
            textvariable=self.mode_var,
            state="readonly",
            width=40,
        )
        self.mode_combo.pack(anchor=tk.W)
        self.mode_combo.bind("<<ComboboxSelected>>", self._on_mode_change)

        # 底部按钮区喵
        btn_frame = ttk.Frame(self.root, padding=(16, 4, 16, 12))
        btn_frame.pack(fill=tk.X)
        self.apply_btn = ttk.Button(
            btn_frame, text="应用 GPU 设置", command=self._on_apply
        )
        self.apply_btn.pack(side=tk.LEFT, padx=(0, 8))
        self.launch_btn = ttk.Button(
            btn_frame, text="启动游戏", command=self._on_launch
        )
        self.launch_btn.pack(side=tk.LEFT)

        # 状态栏喵
        self.status_var = tk.StringVar(value="正在检测系统 GPU...")
        status_bar = ttk.Label(
            self.root,
            textvariable=self.status_var,
            relief=tk.SUNKEN,
            anchor=tk.W,
            font=("Microsoft YaHei UI", 9),
        )
        status_bar.pack(fill=tk.X, side=tk.BOTTOM)

    # ---------- 异步检测 ----------

    def _async_detect(self) -> None:
        """异步检测 GPU 和启动模式，避免阻塞 UI 喵。"""
        self.apply_btn.config(state=tk.DISABLED)
        self.launch_btn.config(state=tk.DISABLED)

        def worker() -> None:
            gpus = detect_gpus()
            modes = detect_launch_modes()
            self.root.after(0, lambda: self._on_detect_done(gpus, modes))

        threading.Thread(target=worker, daemon=True).start()

    def _on_detect_done(self, gpus: List[GpuInfo], modes: List[LaunchMode]) -> None:
        """检测完成回调喵。"""
        self.gpus = gpus
        self.modes = modes

        # 填充 GPU 列表喵
        for g in gpus:
            type_tag = "集显" if g.is_igpu else "独显"
            self.gpu_tree.insert(
                "",
                tk.END,
                text=g.name,
                values=(type_tag, g.vendor, g.driver),
            )

        # 填充启动模式喵
        if modes:
            mode_labels = [m.label for m in modes]
            self.mode_combo["values"] = mode_labels
            self.mode_combo.current(0)
            self.selected_mode = modes[0]
            self._refresh_current_pref_status()
        else:
            self.mode_combo["values"] = ["未找到可用启动模式"]
            self.status_var.set("未找到可用启动模式，请检查项目结构喵")

        if not gpus:
            self.status_var.set("未检测到 GPU（非 Windows 或查询失败）喵")
        else:
            rec = recommend_high_performance(gpus)
            if rec:
                self.status_var.set(
                    f"检测到 {len(gpus)} 块 GPU，推荐高性能：{rec.name} 喵"
                )

        self.apply_btn.config(state=tk.NORMAL)
        self.launch_btn.config(state=tk.NORMAL if modes else tk.DISABLED)

    def _on_mode_change(self, _event) -> None:
        """启动模式切换喵。"""
        idx = self.mode_combo.current()
        if 0 <= idx < len(self.modes):
            self.selected_mode = self.modes[idx]
            self._refresh_current_pref_status()

    def _refresh_current_pref_status(self) -> None:
        """刷新当前启动模式的 GPU 偏好状态喵。"""
        if not self.selected_mode or not self.selected_mode.java_exe:
            return
        pref = get_gpu_preference(self.selected_mode.java_exe)
        # 同步单选按钮喵
        if pref is None:
            self.selected_pref_var.set(0)
        else:
            self.selected_pref_var.set(pref)

    # ---------- 按钮事件 ----------

    def _on_apply(self) -> None:
        """应用 GPU 偏好设置喵。"""
        if not self.selected_mode:
            messagebox.showwarning("提示", "请先选择启动模式喵")
            return

        pref = self.selected_pref_var.get()
        if pref == 0:
            # 清除设置喵
            if not self.selected_mode.java_exe:
                messagebox.showinfo("提示", "该启动模式未找到 java.exe，无需清除喵")
                return
            ok, msg = clear_gpu_preference(self.selected_mode.java_exe)
        else:
            ok, msg = apply_gpu_preference(self.selected_mode, pref)

        if ok:
            self.status_var.set(msg)
            messagebox.showinfo("成功", msg)
        else:
            self.status_var.set(msg)
            messagebox.showerror("失败", msg)

    def _on_launch(self) -> None:
        """启动游戏喵。"""
        if not self.selected_mode:
            messagebox.showwarning("提示", "请先选择启动模式喵")
            return

        # 启动前自动应用 GPU 偏好喵
        pref = self.selected_pref_var.get()
        if pref != 0 and self.selected_mode.java_exe:
            apply_gpu_preference(self.selected_mode, pref)

        ok, msg = launch_game(self.selected_mode)
        self.status_var.set(msg)
        if ok:
            messagebox.showinfo("启动", msg + "\n启动器将在 2 秒后关闭喵")
            self.root.after(2000, self.root.destroy)
        else:
            messagebox.showerror("失败", msg)

    # ---------- 运行 ----------

    def run(self) -> None:
        """启动主循环喵。"""
        self.root.mainloop()