"""
tools/registry.py

作用（description）：
- 负责管理 AI 可用的所有工具喵。
- 自动生成符合 OpenAI 规范的 Tool Schema 喵。
- 提供工具的分发执行能力喵。

提供的接口/API：
- register_tool(name, func, schema)：注册一个新工具喵。
- get_all_schemas()：获取所有已注册工具的描述喵。
- dispatch_tool(name, args)：根据名称执行对应工具喵。
"""

import logging
from typing import Dict, Any, List, Callable

logger = logging.getLogger("ai_system.tools")

class ToolRegistry:
    def __init__(self):
        self._tools: Dict[str, Callable] = {}
        self._schemas: List[Dict[str, Any]] = []

    def register_tool(self, name: str, func: Callable, schema: Dict[str, Any]):
        """注册工具喵"""
        self._tools[name] = func
        self._schemas.append({
            "type": "function",
            "function": schema
        })
        logger.info(f"Tool registered: {name}")

    def get_all_schemas(self) -> List[Dict[str, Any]]:
        """获取所有工具描述喵"""
        return self._schemas

    async def dispatch_tool(self, name: str, args: Dict[str, Any], **kwargs) -> Any:
        """分发执行工具喵"""
        if name not in self._tools:
            raise ValueError(f"Tool not found: {name}")
        
        logger.info(f"Dispatching tool: {name} with args: {args}")
        return await self._tools[name](args=args, **kwargs)

# 全局单例注册表喵
registry = ToolRegistry()
