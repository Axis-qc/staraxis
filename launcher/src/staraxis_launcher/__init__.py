"""StarAxis 游戏启动器包。

作用（description）：
- 提供独立的游戏启动器，负责 GPU 检测、GPU 偏好设置和游戏启动喵。
- 让玩家可以选择用哪块 GPU 运行游戏，解决双显卡笔记本默认走集显的问题喵。

模块组成（modules）：
- gpu_detector：检测系统所有 GPU 喵。
- gpu_preference：写 Windows 注册表设置 GPU 偏好喵。
- game_launcher：启动游戏（支持开发模式和发布模式）喵。
- gui：Tkinter 图形界面喵。
- main：程序入口喵。
"""

__version__ = "1.0.0"