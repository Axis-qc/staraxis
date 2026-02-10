"""
tools/snapshot_tools.py

作用（description）：
- 封装 webnet 提供的 snapshot.getEntity 工具调用喵。
- 提供更友好的 Python API 给 Agent 使用喵。

提供的接口/API：
- get_entity(ws_client, entity_id) -> dict：调用 snapshot.getEntity 并返回结果喵。

注意事项（important_notes）：
- 底层依赖 protocol.ws_client.AiWsClient 的 call_tool 喵。
"""

from typing import Any, Dict
from ..protocol.ws_client import AiWsClient
from .registry import registry

async def get_entity(args: Dict[str, Any], ws_client: AiWsClient) -> Dict[str, Any]:
    """
    通过 entityId 获取游戏实体的实时快照信息。
    """
    entity_id = args.get("entityId")
    if entity_id is None:
        return {"ok": False, "error": "missing entityId"}
    
    resp = await ws_client.call_tool("snapshot.getEntity", {"entityId": entity_id})
    return resp

async def get_latest_summary(args: Dict[str, Any], ws_client: AiWsClient) -> Dict[str, Any]:
    """
    获取星系的宏观统计简报，包括天数、实体统计等。
    """
    resp = await ws_client.call_tool("snapshot.getLatestSummary", {})
    return resp

# 定义 get_entity 的 JSON Schema
GET_ENTITY_SCHEMA = {
    "name": "get_entity",
    "description": "通过 entityId 获取游戏实体的实时快照信息（如行星、国家、舰队等详情）。",
    "parameters": {
        "type": "object",
        "properties": {
            "entityId": {
                "type": "integer",
                "description": "游戏实体的唯一标识 ID。"
            }
        },
        "required": ["entityId"]
    }
}

# 定义 get_latest_summary 的 JSON Schema
GET_LATEST_SUMMARY_SCHEMA = {
    "name": "get_latest_summary",
    "description": "获取星系的宏观统计简报，包括当前天数、Tick 以及各类实体（如星球、舰队）的总数。建议在开始分析前先调用此工具掌握全局。",
    "parameters": {
        "type": "object",
        "properties": {}
    }
}

# 执行注册
def register_all_snapshot_tools():
    registry.register_tool("get_entity", get_entity, GET_ENTITY_SCHEMA)
    registry.register_tool("get_latest_summary", get_latest_summary, GET_LATEST_SUMMARY_SCHEMA)