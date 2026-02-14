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
from typing import Dict, Any, List, Optional
from aiohttp import web
import aiohttp_cors
import websockets

from ..config.loader import AppConfig, load_config
from ..brain.openai_provider import OpenAiProvider, ChatResult, ThinkingStep
from ..tools.registry import registry
from ..tools.snapshot_tools import register_all_snapshot_tools
from ..protocol.ws_client import AiWsClient

logger = logging.getLogger("ai_system.api")


class AiHttpServer:
    """AI HTTP API 服务器"""
    
    def __init__(self, config: AppConfig, host: str = "127.0.0.1", port: int = 17891):
        self.config = config
        self.host = host
        self.port = port
        self.app = web.Application()
        self.llm: OpenAiProvider = None
        self._setup_routes()
        
    def _setup_routes(self):
        """设置路由和 CORS"""
        self.app.router.add_post("/api/chat", self.handle_chat)
        self.app.router.add_get("/api/health", self.handle_health)
        self.app.router.add_get("/api/usage", self.handle_usage)
        
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
            
            # 提取玩家 token
            player_token = context.get("playerToken")
            player_id = context.get("playerId")
            username = context.get("username")
            
            if not player_token:
                logger.warning("No player token provided, AI will have limited access")
            else:
                logger.info(f"AI Chat for player: {username} ({player_id})")
            
            # 确保有系统提示词
            has_system = any(m.get("role") == "system" for m in messages)
            if not has_system:
                system_prompt = self.config.agent.system_prompt
                if context:
                    context_info = self._format_context(context)
                    system_prompt += f"\n\n当前游戏上下文：\n{context_info}"
                messages.insert(0, {"role": "system", "content": system_prompt})
            
            # 创建带有玩家 token 的 WebSocket 客户端
            ws_client = None
            if player_token:
                try:
                    ws_client = await self._create_ws_client_with_token(player_token)
                except Exception as e:
                    logger.error(f"Failed to create WS client with player token: {e}")
                    # 继续执行，只是没有游戏数据访问权限
            
            try:
                # 调用 LLM 生成回复
                result: ChatResult = await self.llm.chat_with_tools(
                    messages, 
                    registry, 
                    ws_client=ws_client,
                    context=context
                )
                
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
