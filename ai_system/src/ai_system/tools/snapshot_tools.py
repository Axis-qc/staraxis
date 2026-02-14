"""
tools/snapshot_tools.py

作用（description）：
- 提供“快照读取”相关的工具（Tool Call）给 AI 使用喵。
- 工具必须使用 context.playerToken（玩家令牌）通过 HTTP 调用 webnet 接口，以确保读取权限与玩家一致喵。
- 同时提供 currentPath（当前页面路径）的读取工具，便于 AI 根据玩家当前页面给出更相关的建议喵。

提供的接口/API：
- get_latest_snapshot(args, ws_client, context) -> dict：通过 HTTP 拉取 /api/snapshot/latest 的快照喵。
- get_current_page_context(args, ws_client, context) -> dict：返回当前页面路径等上下文信息喵。

注意事项（important_notes）：
- context（上下文）来自 webnet /api/ai/chat 转发，且由 webnet 强制写入 playerToken/playerId/username，可信喵。
- playerToken 期望是完整的 Authorization header（如 "Bearer xxx"）喵。
"""

from __future__ import annotations

from typing import Any, Dict, Optional

import requests

from ..protocol.ws_client import AiWsClient
from .registry import registry


def _get_webnet_base_url(ws_client: Optional[AiWsClient]) -> Optional[str]:
    """从 ws_client.config 推导 webnet baseUrl 喵。"""
    if ws_client is None:
        return None
    cfg = getattr(ws_client, "config", None)
    if cfg is None:
        return None

    use_ssl = getattr(getattr(cfg, "server", None), "use_ssl", False)
    host = getattr(getattr(cfg, "server", None), "host", None)
    port = getattr(getattr(cfg, "server", None), "port", None)

    if not host or not port:
        return None

    scheme = "https" if use_ssl else "http"
    return f"{scheme}://{host}:{port}"


async def get_latest_snapshot(args: Dict[str, Any], ws_client: AiWsClient, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """获取玩家最新快照（按玩家权限过滤）喵。"""
    _ = args
    ctx = context or {}

    player_token = ctx.get("playerToken")
    if not player_token:
        return {"ok": False, "error": "missing context.playerToken"}

    base_url = _get_webnet_base_url(ws_client)
    if not base_url:
        return {"ok": False, "error": "missing webnet base url"}

    url = f"{base_url}/api/snapshot/latest"

    try:
        resp = requests.get(
            url,
            headers={
                "Authorization": str(player_token),
                "Accept": "application/json",
            },
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json()
        return data
    except Exception as e:
        return {"ok": False, "error": str(e)}


async def get_current_page_context(args: Dict[str, Any], ws_client: AiWsClient, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """返回当前页面路径等上下文喵。"""
    _ = args
    _ = ws_client
    ctx = context or {}

    return {
        "ok": True,
        "currentPath": ctx.get("currentPath"),
        "playerId": ctx.get("playerId"),
        "username": ctx.get("username"),
    }


GET_LATEST_SNAPSHOT_SCHEMA = {
    "name": "get_latest_snapshot",
    "description": "通过玩家令牌从 webnet 拉取最新快照（含本国全量实体 + 可见星区数据），用于生产建设/资产状态分析喵。",
    "parameters": {
        "type": "object",
        "properties": {},
    },
}

GET_CURRENT_PAGE_CONTEXT_SCHEMA = {
    "name": "get_current_page_context",
    "description": "获取玩家当前页面上下文（route.fullPath），用于让 AI 更贴合当前界面提供建议喵。",
    "parameters": {
        "type": "object",
        "properties": {},
    },
}


def register_all_snapshot_tools():
    registry.register_tool("get_latest_snapshot", get_latest_snapshot, GET_LATEST_SNAPSHOT_SCHEMA)
    registry.register_tool("get_current_page_context", get_current_page_context, GET_CURRENT_PAGE_CONTEXT_SCHEMA)
