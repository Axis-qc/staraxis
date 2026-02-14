"""
main.py

作用（description）：
- StarAxis AI 助手主入口喵。
- 启动后读取 config/config.yaml，初始化 LLM provider 和 HTTP API 服务器喵。
- 采用 HTTP 权限模型，使用玩家 Token 访问游戏数据喵。
- 支持工具调用：get_snapshot_meta, search_owned_entities, get_entity_by_id, get_current_page_context 喵。
- 支持优雅退出：捕获 SIGINT/SIGTERM，并在退出前保存状态喵。

使用方式（usage）：
- 通过 ai_system/run_agent.bat 启动喵。

注意事项（important_notes）：
- HTTP API 服务器默认监听 127.0.0.1:17891，供前端 AI 助手浮动球调用喵。
- 历史记录持久化已下沉到 HTTP Server 层实现喵。
"""

import asyncio
import logging
import signal
from pathlib import Path

from .config.loader import load_config
from .brain.openai_provider import OpenAiProvider
from .tools.snapshot_tools import register_all_snapshot_tools, get_snapshot_meta
from .tools.registry import registry
from .api.http_server import create_server


def _setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s][%(levelname)s][%(name)s] %(message)s",
    )


async def run_agent() -> None:
    cfg = load_config()

    # 注册所有可用工具喵
    register_all_snapshot_tools()
    
    # 打印已加载的工具列表喵
    tools = registry.get_all_schemas()
    tool_names = [t["function"]["name"] for t in tools]
    logging.getLogger("ai_system").info(f"AI Assistant initialized with {len(tool_names)} tools: {', '.join(tool_names)}")

    # 初始化 LLM provider喵
    llm = OpenAiProvider(cfg)
    
    # 启动 HTTP API 服务器（供前端调用）喵
    # 端口 17891 是 webnet 预热探测的目标端口喵
    http_server = create_server(cfg, host="127.0.0.1", port=17891)
    http_server.set_llm(llm)
    http_runner = await http_server.start()

    stop_event = asyncio.Event()

    def _request_stop(*_args):
        stop_event.set()

    try:
        signal.signal(signal.SIGINT, _request_stop)
        signal.signal(signal.SIGTERM, _request_stop)
    except Exception:
        pass

    print("StarAxis AI Assistant started.")
    print(f"HTTP API: http://127.0.0.1:17891/api/chat")
    print("Preheat Status: Ready for requests喵.")

    # 等待退出信号喵
    await stop_event.wait()

    # 停止 HTTP 服务器喵
    await http_server.stop(http_runner)
    logging.getLogger("ai_system").info("AI Assistant exited gracefully喵.")


def main() -> None:
    _setup_logging()
    try:
        asyncio.run(run_agent())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
