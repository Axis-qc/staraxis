"""
main.py

作用（description）：
- StarAxis AI 助手主入口喵。
- 启动后读取 config/config.yaml，初始化 LLM provider 和 HTTP API 服务器喵。
- 可选：登录 webnet，连接 /ws/ai（用于工具调用访问游戏数据）喵。
- 支持工具调用：snapshot.getEntity, snapshot.getLatestSummary 喵。
- 支持优雅退出：捕获 SIGINT/SIGTERM，并在退出前保存上下文喵。

使用方式（usage）：
- 通过 ai_system/run_agent.bat 启动喵。

注意事项（important_notes）：
- HTTP API 服务器默认监听 127.0.0.1:17891，供前端 AI 助手浮动球调用喵。
- WebSocket 连接用于工具调用（获取游戏数据），HTTP 用于对话接口喵。
"""

import asyncio
import json
import logging
import signal
from pathlib import Path
from typing import Any, Dict

from .config.loader import load_config
from .protocol.ws_client import AiWsClient
from .brain.openai_provider import OpenAiProvider
from .tools.snapshot_tools import register_all_snapshot_tools, get_latest_snapshot
from .tools.registry import registry
from .api.http_server import create_server


def _setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s][%(levelname)s][%(name)s] %(message)s",
    )


class ContextStore:
    def __init__(self, path: Path):
        self.path = path
        self.data: Dict[str, Any] = {
            "messages": []
        }
        self.load()

    def append_message(self, msg: Dict[str, Any]) -> None:
        self.data.setdefault("messages", []).append(msg)

    def load(self) -> None:
        """从文件加载历史上下文喵"""
        if self.path.exists():
            try:
                self.data = json.loads(self.path.read_text(encoding="utf-8"))
                logging.getLogger("ai_system").info(f"Loaded context from {self.path}")
            except Exception as e:
                logging.getLogger("ai_system").error(f"Failed to load context: {e}")

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, ensure_ascii=False, indent=2), encoding="utf-8")


async def run_agent() -> None:
    cfg = load_config()

    # 注册所有可用工具喵
    register_all_snapshot_tools()
    
    # 打印已加载的工具列表喵
    tools = registry.get_all_schemas()
    tool_names = [t["function"]["name"] for t in tools]
    logging.getLogger("ai_system").info(f"AI Assistant initialized with {len(tool_names)} tools: {', '.join(tool_names)}")

    # 初始化 LLM provider
    llm = OpenAiProvider(cfg)
    
    # 启动 HTTP API 服务器（供前端调用）
    http_server = create_server(cfg, host="127.0.0.1", port=17891)
    http_server.set_llm(llm)
    http_runner = await http_server.start()

    # 可选：连接到 WebSocket（用于工具调用获取游戏数据）
    ws: AiWsClient = None
    ctx: ContextStore = None
    
    # 判断是否启用 WebSocket 连接（用于 CLI 模式或需要工具调用的场景）
    enable_ws = cfg.server.auto_start  # 或者其他配置项
    
    if enable_ws:
        try:
            ws = AiWsClient(cfg)
            await ws.connect()
            http_server.set_ws_client(ws)  # HTTP 服务器可以使用 WS 客户端进行工具调用
            logging.getLogger("ai_system").info("WebSocket connected for tool calls")
        except Exception as e:
            logging.getLogger("ai_system").warning(f"Failed to connect WebSocket: {e}")
            logging.getLogger("ai_system").warning("Running in HTTP-only mode (no game data access)")
    
    ctx = ContextStore(Path("data/context.json"))

    stop_event = asyncio.Event()

    def _request_stop(*_args):
        stop_event.set()

    try:
        signal.signal(signal.SIGINT, _request_stop)
    except Exception:
        pass

    try:
        signal.signal(signal.SIGTERM, _request_stop)
    except Exception:
        pass

    print("StarAxis AI Assistant started.")
    print(f"HTTP API: http://127.0.0.1:17891/api/chat")
    print("Commands: entity <id> | ask <text> | exit")

    # CLI 交互循环（可选）
    while not stop_event.is_set():
        try:
            line = await asyncio.get_running_loop().run_in_executor(None, input, "> ")
        except (EOFError, KeyboardInterrupt):
            break

        if not line:
            continue
        line = line.strip()

        if line.lower() in ("exit", "quit"):
            break

        if line.startswith("entity "):
            print("warning: entity command is deprecated. Using get_latest_snapshot instead.")
            if not ws:
                print("error: WebSocket not connected")
                continue
            try:
                # 模拟一个 context 喵
                mock_ctx = {"playerToken": f"Bearer {ws.token}"} if ws.token else {}
                resp = await get_latest_snapshot({}, ws_client=ws, context=mock_ctx)
                print(json.dumps(resp, ensure_ascii=False, indent=2))
            except Exception as e:
                print(f"error: {e}")
            continue

        if line.startswith("ask "):
            user_text = line.split(" ", 1)[1].strip()
            
            # 准备上下文喵，包含系统提示词和历史消息喵
            chat_messages = [{"role": "system", "content": cfg.agent.system_prompt}]
            chat_messages.extend(ctx.data.get("messages", [])[-10:]) # 取最近 10 条喵
            chat_messages.append({"role": "user", "content": user_text})
            
            try:
                # 使用智能工具循环模式喵
                content = await llm.chat_with_tools(chat_messages, registry, ws_client=ws)
                print(content)
                
                # 仅保存用户和助手的最终对话，工具调用过程记录在 LLM 的交互中喵
                ctx.append_message({"role": "user", "content": user_text})
                ctx.append_message({"role": "assistant", "content": content})
            except Exception as e:
                print(f"llm error: {e}")
            continue

        print("unknown command. use: entity <id> | ask <text> | exit")

    # 清理和保存
    try:
        if ctx:
            ctx.save()
            logging.getLogger("ai_system").info("context saved to data/context.json")
    except Exception as e:
        logging.getLogger("ai_system").error(f"failed to save context: {e}")

    # 停止 HTTP 服务器
    await http_server.stop(http_runner)
    
    # 关闭 WebSocket 连接
    if ws:
        await ws.close()


def main() -> None:
    _setup_logging()
    asyncio.run(run_agent())


if __name__ == "__main__":
    main()
