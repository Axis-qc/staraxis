"""
tools/snapshot_tools.py

作用（description）：
- 提供“快照检索”相关的工具（Tool Call）给 AI 使用喵。
- 工具采用“按需拉取”策略，避免一次性加载全量数据导致 Token 爆炸喵。
- 强制使用 context.playerToken 通过 HTTP 调用 webnet 接口，确保权限一致性喵。

提供的接口/API：
- get_snapshot_meta: 获取当前世界摘要（天数、本国实体计数）喵。
- get_entity_by_id: 获取指定 entityId 的详细快照喵。
- search_owned_entities: 搜索本国实体（支持类型、星区、文本过滤与分页）喵。
- get_current_page_context: 感知玩家当前所在的 UI 页面路径喵。
"""

from __future__ import annotations
from typing import Any, Dict, Optional
import requests
import logging

from ..protocol.ws_client import AiWsClient
from .registry import registry

logger = logging.getLogger("ai_system.tools")

def _get_webnet_base_url(ws_client: Optional[AiWsClient]) -> str:
    """推导 webnet 基础地址，默认 127.0.0.1:17890 喵。"""
    if ws_client and hasattr(ws_client, "config"):
        server = ws_client.config.server
        scheme = "https" if server.use_ssl else "http"
        return f"{scheme}://{server.host}:{server.port}"
    return "http://127.0.0.1:17890"

async def get_snapshot_meta(args: Dict[str, Any], ws_client: AiWsClient, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """获取世界状态摘要喵。"""
    _ = args
    ctx = context or {}
    token = ctx.get("playerToken")
    if not token: return {"ok": False, "error": "Unauthorized: missing playerToken"}

    url = f"{_get_webnet_base_url(ws_client)}/api/snapshot/meta"
    try:
        resp = requests.get(url, headers={"Authorization": str(token)}, timeout=5)
        return resp.json()
    except Exception as e:
        return {"ok": False, "error": str(e)}

async def get_entity_by_id(args: Dict[str, Any], ws_client: AiWsClient, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """按 ID 查询实体详情喵。"""
    entity_id = args.get("entityId")
    if entity_id is None: return {"ok": False, "error": "missing entityId"}
    
    ctx = context or {}
    token = ctx.get("playerToken")
    if not token: return {"ok": False, "error": "Unauthorized"}

    url = f"{_get_webnet_base_url(ws_client)}/api/snapshot/entity?id={entity_id}"
    try:
        resp = requests.get(url, headers={"Authorization": str(token)}, timeout=5)
        if resp.status_code == 404: return {"ok": False, "error": f"Entity {entity_id} not found"}
        if resp.status_code == 403: return {"ok": False, "error": "Forbidden: No visibility"}
        return resp.json()
    except Exception as e:
        return {"ok": False, "error": str(e)}

async def search_owned_entities(args: Dict[str, Any], ws_client: AiWsClient, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """搜索本国实体（增强版）喵。"""
    ctx = context or {}
    token = ctx.get("playerToken")
    if not token: return {"ok": False, "error": "Unauthorized"}

    params = {
        "entityType": args.get("entityType"),
        "text": args.get("text"),
        "sectorQ": args.get("sectorQ"),
        "sectorR": args.get("sectorR"),
        "limit": args.get("limit", 20),
        "offset": args.get("offset", 0)
    }
    # 移除空值喵
    params = {k: v for k, v in params.items() if v is not None}

    url = f"{_get_webnet_base_url(ws_client)}/api/snapshot/owned/search"
    try:
        resp = requests.get(url, headers={"Authorization": str(token)}, params=params, timeout=10)
        return resp.json()
    except Exception as e:
        return {"ok": False, "error": str(e)}

async def get_current_page_context(args: Dict[str, Any], ws_client: AiWsClient, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """获取当前页面路径喵。"""
    _ = args; _ = ws_client
    ctx = context or {}
    return {
        "ok": True,
        "currentPath": ctx.get("currentPath", "unknown"),
        "playerId": ctx.get("playerId"),
        "username": ctx.get("username")
    }

# --- Schemas ---

GET_SNAPSHOT_META_SCHEMA = {
    "name": "get_snapshot_meta",
    "description": "获取当前世界的宏观摘要（如游戏天数、Tick、本国拥有的各类型实体总数）。在开始具体分析前，建议先调用此工具获取大盘数据喵。",
    "parameters": {"type": "object", "properties": {}}
}

GET_ENTITY_BY_ID_SCHEMA = {
    "name": "get_entity_by_id",
    "description": "通过 entityId 获取指定实体的完整快照细节。仅当你已知晓具体的 ID 且需要详细属性（如行星资源、飞船耐久等）时使用喵。",
    "parameters": {
        "type": "object",
        "properties": {
            "entityId": {"type": "integer", "description": "实体的唯一标识 ID"}
        },
        "required": ["entityId"]
    }
}

SEARCH_OWNED_ENTITIES_SCHEMA = {
    "name": "search_owned_entities",
    "description": "搜索并列出属于玩家国家的实体。支持按类型、文本关键词（匹配ID或属性）、星区坐标过滤。结果带分页，是查找资产的最常用工具喵。",
    "parameters": {
        "type": "object",
        "properties": {
            "entityType": {"type": "string", "enum": ["STAR", "PLANET", "SHIP", "STATION"], "description": "过滤实体类型"},
            "text": {"type": "string", "description": "搜索关键词（支持模糊匹配 ID 或详情描述）"},
            "sectorQ": {"type": "integer", "description": "限制在特定星区轴向坐标 Q"},
            "sectorR": {"type": "integer", "description": "限制在特定星区轴向坐标 R"},
            "limit": {"type": "integer", "default": 20, "description": "返回结果数量上限"},
            "offset": {"type": "integer", "default": 0, "description": "分页偏移量"}
        }
    }
}

GET_CURRENT_PAGE_CONTEXT_SCHEMA = {
    "name": "get_current_page_context",
    "description": "获取玩家当前在游戏界面中所在的页面路径（route.fullPath）。这有助于 AI 理解玩家的视觉上下文并给出更贴合界面的建议喵。",
    "parameters": {"type": "object", "properties": {}}
}

def register_all_snapshot_tools():
    registry.register_tool("get_snapshot_meta", get_snapshot_meta, GET_SNAPSHOT_META_SCHEMA)
    registry.register_tool("get_entity_by_id", get_entity_by_id, GET_ENTITY_BY_ID_SCHEMA)
    registry.register_tool("search_owned_entities", search_owned_entities, SEARCH_OWNED_ENTITIES_SCHEMA)
    registry.register_tool("get_current_page_context", get_current_page_context, GET_CURRENT_PAGE_CONTEXT_SCHEMA)
