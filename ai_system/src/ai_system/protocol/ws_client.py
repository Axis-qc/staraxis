"""
protocol/ws_client.py

作用（description）：
- 负责与 WebNet 服务端的 WebSocket 通信喵。
- 实现自动登录获取 Token、连接建立、心跳维持以及消息分发喵。
- 提供异步的 call_tool 方法，将工具请求与响应通过 requestId 进行映射喵。

提供的接口/API：
- connect()：登录并建立 WS 连接喵。
- call_tool(tool_name, args) -> dict：发送工具调用请求并等待结果喵。
- close()：关闭连接喵。

注意事项（important_notes）：
- 基于 asyncio 和 websockets 库实现喵。
- 必须在事件循环中运行喵。
"""

import asyncio
import json
import uuid
import logging
import requests
from typing import Dict, Any, Optional
import websockets
from ..config.loader import AppConfig

logger = logging.getLogger("ai_system.protocol")

class AiWsClient:
    def __init__(self, config: AppConfig):
        self.config = config
        self.token: Optional[str] = None
        self.ws: Optional[websockets.WebSocketClientProtocol] = None
        self._pending_requests: Dict[str, asyncio.Future] = {}
        self._is_running = False
        self._receive_task: Optional[asyncio.Task] = None

    async def _login(self):
        """调用 HTTP 接口登录获取 Token 喵"""
        url = f"http{'s' if self.config.server.use_ssl else ''}://{self.config.server.host}:{self.config.server.port}/api/auth/login"
        payload = {
            "username": self.config.auth.username,
            "password": self.config.auth.password
        }
        try:
            # 这里的 requests 调用是同步的，但在启动阶段执行一次通常可以接受喵
            # 如果需要严格异步可以改用 httpx 喵
            response = requests.post(url, json=payload, timeout=10)
            response.raise_for_status()
            data = response.json()
            if data.get("ok"):
                self.token = data.get("token")
                logger.info(f"Login successful for user: {self.config.auth.username}")
            else:
                raise Exception(f"Login failed: {data.get('error')}")
        except Exception as e:
            logger.error(f"Login request failed: {e}")
            raise

    async def connect(self):
        """建立 WebSocket 连接并开始接收消息 喵"""
        await self._login()
        
        ws_url = f"ws{'s' if self.config.server.use_ssl else ''}://{self.config.server.host}:{self.config.server.port}/ws/ai"
        headers = {
            "Authorization": f"Bearer {self.token}"
        }
        
        logger.info(f"Connecting to WS: {ws_url}")
        self.ws = await websockets.connect(ws_url, extra_headers=headers)
        self._is_running = True
        self._receive_task = asyncio.create_task(self._receive_loop())
        logger.info("WS connection established")

    async def _receive_loop(self):
        """持续接收并分发消息的循环 喵"""
        try:
            async for message in self.ws:
                data = json.loads(message)
                msg_type = data.get("type")
                
                if msg_type == "ai.tool.result":
                    req_id = data.get("requestId")
                    if req_id in self._pending_requests:
                        future = self._pending_requests.pop(req_id)
                        if not future.done():
                            future.set_result(data)
                elif msg_type == "ai.hello":
                    logger.info(f"Received hello from server: {data}")
                elif msg_type == "ai.error":
                    logger.error(f"Server error: {data.get('error')}")
        except websockets.ConnectionClosed:
            logger.warning("WS connection closed")
        except Exception as e:
            logger.error(f"Error in receive loop: {e}")
        finally:
            self._is_running = False

    async def call_tool(self, tool_name: str, args: Dict[str, Any]) -> Dict[str, Any]:
        """异步调用工具并等待结果 喵"""
        if not self._is_running or not self.ws:
            raise Exception("WS connection not active")
            
        req_id = str(uuid.uuid4())
        payload = {
            "type": "ai.tool.call",
            "requestId": req_id,
            "tool": tool_name,
            "args": args
        }
        
        future = asyncio.get_running_loop().create_future()
        self._pending_requests[req_id] = future
        
        await self.ws.send(json.dumps(payload))
        
        try:
            # 等待结果，默认 30 秒超时 喵
            result = await asyncio.wait_for(future, timeout=30.0)
            return result
        except asyncio.TimeoutError:
            self._pending_requests.pop(req_id, None)
            raise Exception(f"Tool call '{tool_name}' timed out")

    async def close(self):
        """关闭连接并清理资源 喵"""
        self._is_running = False
        if self._receive_task:
            self._receive_task.cancel()
        if self.ws:
            await self.ws.close()
        logger.info("WS connection closed gracefully")
