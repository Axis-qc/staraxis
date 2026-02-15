"""
api/http_server.py

作用（description）：
- 为前端提供 HTTP API 接口，处理对话请求喵。
- 接收用户消息，调用 LLM 生成回复，支持工具调用喵。
- 使用玩家 token 进行 WebSocket 连接，确保 AI 只能访问玩家有权限的数据喵。
- 返回思考过程、Token 消耗统计等详细信息喵。

提供的接口/API：
- POST /api/chat: 对话接口，接收消息历史，返回 AI 回复和思考过程喵。
- GET /api/health: 健康检查喵。
- GET /api/usage: 获取累计 Token 使用统计喵。

请求/响应格式：
- Chat Request: {
    "messages": [...],
    "context": {
        "playerToken": "Bearer xxx",  # 玩家 token，用于 WebSocket 认证
        "playerId": "...",
        "username": "..."
    },
    "show_thinking": true
}

注意事项（important_notes）：
- 使用 aiohttp 实现异步 HTTP 服务器喵。
- 每个对话请求可能带有不同的玩家 token，需要为每个请求创建独立的 WebSocket 连接喵。
- 需要处理 CORS 以支持前端跨域请求喵。
"""

import asyncio
import json
import logging
import time
from typing import Dict, Any, List, Optional
from pathlib import Path
from aiohttp import web
import aiohttp_cors
import websockets

from ..config.loader import AppConfig, load_config
from ..brain.openai_provider import OpenAiProvider, ChatResult, ThinkingStep
from ..tools.registry import registry
from ..tools.snapshot_tools import register_all_snapshot_tools
from ..protocol.ws_client import AiWsClient

logger = logging.getLogger("ai_system.api")

# 强化路径定位：自动寻找项目根目录下的 gamedata/ai_chat 喵
def _resolve_history_root() -> Path:
    # 尝试基于当前文件位置向上查找喵
    current_file = Path(__file__).resolve()
    # 从 ai_system/src/ai_system/api/http_server.py 向上退 4 级到达项目根目录喵
    root = current_file.parents[4]
    target = root / "gamedata" / "ai_chat"
    target.mkdir(parents=True, exist_ok=True)
    return target

HISTORY_ROOT = _resolve_history_root()
logger.info(f"AI History path resolved to: {HISTORY_ROOT}")

class AiHttpServer:
    """AI HTTP API 服务器"""
    
    def __init__(self, config: AppConfig, host: str = "127.0.0.1", port: int = 17891):
        self.config = config
        self.host = host
        self.port = port
        self.app = web.Application()
        self.llm: OpenAiProvider = None
        self.ws_client: AiWsClient = None
        # 确保历史目录存在喵
        HISTORY_ROOT.mkdir(parents=True, exist_ok=True)
        self._setup_routes()
        
    def _setup_routes(self):
        """设置路由和 CORS"""
        self.app.router.add_post("/api/chat", self.handle_chat)
        self.app.router.add_get("/api/health", self.handle_health)
        self.app.router.add_get("/api/usage", self.handle_usage)
        self.app.router.add_get("/api/history", self.handle_get_history)
        self.app.router.add_post("/api/history/clear", self.handle_clear_history)
        
        cors = aiohttp_cors.setup(self.app, defaults={
            "*": aiohttp_cors.ResourceOptions(
                allow_credentials=True,
                expose_headers="*",
                allow_headers="*",
                allow_methods="*"
            )
        })
        
        for route in list(self.app.router.routes()):
            cors.add(route)
    
    def set_llm(self, llm: OpenAiProvider):
        """设置 LLM provider"""
        self.llm = llm

    def set_ws_client(self, ws_client: AiWsClient):
        """设置 WebSocket 客户端喵"""
        self.ws_client = ws_client
    
    def _get_history_file(self, player_id: str, session_id: str) -> Path:
        """获取历史记录文件路径喵"""
        # 移除非法字符，确保文件名安全喵
        safe_pid = "".join(c for c in player_id if c.isalnum() or c in ('-', '_'))
        safe_sid = "".join(c for c in session_id if c.isalnum() or c in ('-', '_'))
        return HISTORY_ROOT / f"chat_{safe_pid}_{safe_sid}.json"

    def _load_history(self, player_id: str, session_id: str) -> List[Dict[str, Any]]:
        """从文件加载对话历史喵"""
        path = self._get_history_file(player_id, session_id)
        if path.exists():
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                msgs = data.get("messages", [])
                logger.info(f"Loaded {len(msgs)} history messages for {player_id}/{session_id} from {path}喵.")
                return msgs
            except Exception as e:
                logger.error(f"Failed to load history for {player_id}/{session_id}: {e}喵.")
        return []

    def _save_history(self, player_id: str, session_id: str, new_messages: List[Dict[str, Any]]):
        """追加并保存对话历史到文件喵"""
        path = self._get_history_file(player_id, session_id)
        try:
            # 1. 先读取旧历史喵
            existing = self._load_history(player_id, session_id)
            
            # 2. 合并新对话（避免重复保存）喵
            # 这里简单处理：仅追加不在旧历史里的新消息喵
            # 或者由前端全量传过来时，我们只取最后的新内容追加喵
            # 方案优化：直接把当前传入的完整上下文（messages）作为新基准合并喵
            combined = list(existing)
            existing_ids = {m.get("id") for m in existing if m.get("id")}
            
            for m in new_messages:
                mid = m.get("id")
                if not mid or mid not in existing_ids:
                    combined.append(m)
            
            # 3. 限制长度（保留最近 200 条）并写入喵
            final_list = combined[-200:]
            path.write_text(json.dumps({"messages": final_list}, ensure_ascii=False, indent=2), encoding="utf-8")
            logger.info(f"Saved {len(final_list)} messages to {path}喵.")
        except Exception as e:
            logger.error(f"Failed to save history for {player_id}/{session_id}: {e}喵.")

    async def handle_get_history(self, request: web.Request) -> web.Response:
        """获取特定玩家/会话的历史记录喵"""
        player_id = request.query.get("playerId")
        session_id = request.query.get("sessionId")

        # sessionId 容错：空或仅空白时回退到 default，避免生成尾随下划线文件名喵
        if session_id is not None and session_id.strip() == "":
            session_id = "default"

        if not player_id or not session_id:
            return web.json_response({"ok": False, "error": "playerId and sessionId required"}, status=400)
        
        history = self._load_history(player_id, session_id)
        return web.json_response({"ok": True, "messages": history})

    async def handle_clear_history(self, request: web.Request) -> web.Response:
        """清空特定玩家/会话的历史记录喵"""
        try:
            body = await request.json()
            player_id = body.get("playerId")
            session_id = body.get("sessionId")
            if not player_id or not session_id:
                return web.json_response({"ok": False, "error": "playerId and sessionId required"}, status=400)
            
            path = self._get_history_file(player_id, session_id)
            if path.exists():
                path.unlink()
            return web.json_response({"ok": True})
        except Exception as e:
            return web.json_response({"ok": False, "error": str(e)}, status=500)

    async def handle_health(self, request: web.Request) -> web.Response:
        """健康检查接口"""
        return web.json_response({
            "ok": True,
            "status": "healthy",
            "provider": self.config.ai.active_provider if self.config else None
        })
    
    async def handle_usage(self, request: web.Request) -> web.Response:
        """获取 Token 使用统计"""
        if not self.llm:
            return web.json_response({
                "ok": False,
                "error": "LLM not initialized"
            }, status=503)
        
        stats = self.llm.get_usage_stats()
        return web.json_response({
            "ok": True,
            "usage": stats
        })
    
    async def handle_chat(self, request: web.Request) -> web.Response:
        """
        对话接口
        
        使用玩家 token 建立 WebSocket 连接，确保数据访问权限与玩家一致
        """
        try:
            if not self.llm:
                return web.json_response({
                    "ok": False,
                    "error": "LLM not initialized"
                }, status=503)
            
            body = await request.json()
            messages = body.get("messages", [])
            context = body.get("context", {})
            show_thinking = body.get("show_thinking", True)
            
            if not messages:
                return web.json_response({
                    "ok": False,
                    "error": "messages is required"
                }, status=400)
            
            # 提取元数据喵
            player_token = context.get("playerToken")
            player_id = context.get("playerId")
            session_id = context.get("sessionId")
            
            # sessionId 容错：空或仅空白时回退到 default，避免保存为非法文件名喵
            if not session_id or session_id.strip() == "":
                session_id = "default"
                
            username = context.get("username")
            
            if not player_token or not player_id:
                logger.warning("No player identity provided, history will not be saved")
            else:
                logger.info(f"AI Chat for player: {username} ({player_id}) session={session_id}")
            
            # 整理发送给 LLM 的完整消息列表喵
            final_messages = []
            
            # 1. 注入系统提示词喵
            has_system = any(m.get("role") == "system" for m in messages)
            if not has_system:
                system_prompt = self.config.agent.system_prompt
                if context:
                    context_info = self._format_context(context)
                    system_prompt += f"\n\n当前游戏上下文：\n{context_info}"
                final_messages.append({"role": "system", "content": system_prompt})
            
            # 2. 合并当前传入的消息喵
            final_messages.extend(messages)
            
            # 创建带有玩家 token 的 WebSocket 客户端
            ws_client = None
            if player_token:
                try:
                    ws_client = await self._create_ws_client_with_token(player_token)
                except Exception as e:
                    logger.error(f"Failed to create WS client with player token: {e}")
            
            try:
                # 调用 LLM 生成回复喵
                result: ChatResult = await self.llm.chat_with_tools(
                    final_messages, 
                    registry, 
                    ws_client=ws_client,
                    context=context
                )
                
                # 持久化：保存最新历史记录到文件喵
                if player_id:
                    # 我们从前端传来的 messages 中提取最新对话喵
                    # 前端传来的 messages 已经包含了历史，我们只需要把最新的助手回复追加进去喵
                    full_history = list(messages)
                    full_history.append({
                        "role": "assistant",
                        "text": result.content,
                        "id": "ai_" + str(int(time.time() * 1000)),
                        "usage": result.total_usage.to_dict()
                    })
                    self._save_history(player_id, session_id, full_history)

                response_data = {
                    "ok": True,
                    "message": result.content,
                    "usage": result.total_usage.to_dict(),
                    "tool_calls_count": result.tool_calls_count,
                    "total_duration_ms": result.total_duration_ms,
                    "provider": result.provider,
                    "model": result.model
                }
                
                if show_thinking:
                    response_data["thinking"] = [
                        step.to_dict() for step in result.thinking_steps
                    ]
                
                return web.json_response(response_data)
            finally:
                # 清理 WebSocket 连接
                if ws_client:
                    try:
                        await ws_client.close()
                    except Exception as e:
                        logger.error(f"Error closing WS client: {e}")
            
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON: {e}")
            return web.json_response({
                "ok": False,
                "error": "Invalid JSON"
            }, status=400)
        except Exception as e:
            logger.error(f"Chat error: {e}")
            return web.json_response({
                "ok": False,
                "error": str(e)
            }, status=500)
    
    async def _create_ws_client_with_token(self, player_token: str) -> AiWsClient:
        """
        使用玩家 token 创建 WebSocket 客户端
        
        Args:
            player_token: 玩家的 Authorization header (Bearer xxx)
            
        Returns:
            AiWsClient: 已连接并使用玩家身份认证的 WebSocket 客户端
        """
        # 创建临时配置，使用玩家 token 而非固定 AI 账户
        ws_client = PlayerWsClient(self.config, player_token)
        await ws_client.connect()
        return ws_client
    
    def _format_context(self, context: Dict[str, Any]) -> str:
        """格式化游戏上下文为字符串"""
        parts = []
        if "username" in context:
            parts.append(f"玩家: {context['username']}")
        if "playerId" in context:
            parts.append(f"玩家ID: {context['playerId']}")
        if "scene" in context:
            scene_map = {
                "galaxy_map": "星系地图",
                "system_view": "星系视图",
                "combat": "战斗界面",
                "diplomacy": "外交界面"
            }
            scene = scene_map.get(context['scene'], context['scene'])
            parts.append(f"当前场景: {scene}")
        return "\n".join(parts) if parts else "无"
    
    async def start(self):
        """启动 HTTP 服务器"""
        runner = web.AppRunner(self.app)
        await runner.setup()
        site = web.TCPSite(runner, self.host, self.port)
        await site.start()
        logger.info(f"AI HTTP API server started on http://{self.host}:{self.port}")
        return runner
    
    async def stop(self, runner):
        """停止 HTTP 服务器"""
        await runner.cleanup()
        logger.info("AI HTTP API server stopped")


class PlayerWsClient(AiWsClient):
    """
    使用玩家 token 的 WebSocket 客户端
    
    继承自 AiWsClient，但使用玩家提供的 token 进行认证，
    而不是配置文件中的固定 AI 账户。
    """
    
    def __init__(self, config: AppConfig, player_token: str):
        super().__init__(config)
        self.player_token = player_token
        # 直接使用玩家 token，不进行登录
        self.token = player_token.replace("Bearer ", "") if player_token.startswith("Bearer ") else player_token
    
    async def _login(self):
        """跳过登录，直接使用玩家 token"""
        logger.info("Using player token for WebSocket authentication")
        # 不需要登录，token 已经在初始化时设置
        pass
    
    async def connect(self):
        """使用玩家 token 建立 WebSocket 连接"""
        ws_url = f"ws{'s' if self.config.server.use_ssl else ''}://{self.config.server.host}:{self.config.server.port}/ws/ai"
        headers = {
            "Authorization": f"Bearer {self.token}"
        }
        
        logger.info(f"Connecting to WS with player token: {ws_url}")
        self.ws = await websockets.connect(ws_url, extra_headers=headers)
        self._is_running = True
        self._receive_task = asyncio.create_task(self._receive_loop())
        logger.info("WS connection established with player authentication")


# 全局服务器实例
_http_server: AiHttpServer = None


def create_server(config: AppConfig, host: str = "127.0.0.1", port: int = 17891) -> AiHttpServer:
    """创建 HTTP 服务器实例"""
    global _http_server
    _http_server = AiHttpServer(config, host, port)
    return _http_server


def get_server() -> AiHttpServer:
    """获取当前 HTTP 服务器实例"""
    return _http_server
