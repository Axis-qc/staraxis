"""
main.py

作用（description）：
- StarAxis AI 助手主入口喵。
- 启动后读取 config/config.yaml，登录 webnet，连接 /ws/ai，并进入对话循环喵。
- 支持工具调用：snapshot.getEntity喵。
- 支持优雅退出：捕获 SIGINT/SIGTERM，并在退出前保存上下文到 ai_system/data/context.json喵。

使用方式（usage）：
- 通过 ai_system/run_agent.bat 启动喵。

注意事项（important_notes）：
- 本实现为框架骨架：当前提供一个简单 REPL，后续可替换为事件驱动（订阅游戏事件）喵。
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
from .tools.snapshot_tools import get_entity, register_all_snapshot_tools
from .tools.registry import registry


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

    ws = AiWsClient(cfg)
    await ws.connect()

    llm = OpenAiProvider(cfg)

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

    print("StarAxis AI Assistant started. Type 'exit' to quit. Commands: entity <id>, ask <text>")

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
            try:
                entity_id = int(line.split(" ", 1)[1].strip())
                resp = await get_entity({"entityId": entity_id}, ws_client=ws)
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

    try:
        ctx.save()
        logging.getLogger("ai_system").info("context saved to data/context.json")
    except Exception as e:
        logging.getLogger("ai_system").error(f"failed to save context: {e}")

    await ws.close()


def main() -> None:
    _setup_logging()
    asyncio.run(run_agent())


if __name__ == "__main__":
    main()
