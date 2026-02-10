"""
brain/openai_provider.py

作用（description）：
- 对接 OpenAI 兼容接口，并实现自动化的 Tool Calling 循环喵。
- 负责管理对话上下文，并根据模型需求调用本地工具注册表（registry）喵。

提供的接口/API：
- chat_with_tools(messages, registry, **kwargs) -> str：带工具循环的智能对话接口喵。

注意事项（important_notes）：
- 采用异步模型处理 API 请求与工具执行喵。
- 运行时日志已移除“喵”以保持专业性喵。
"""

import asyncio
import logging
import json
import requests
from typing import List, Dict, Any, Optional
from ..config.loader import AppConfig

logger = logging.getLogger("ai_system.brain")

class OpenAiProvider:
    def __init__(self, config: AppConfig):
        self.config = config.ai
        self.agent_config = config.agent
        self.api_key = self.config.api_key
        self.base_url = self.config.base_url
        self.model = self.config.model

    async def _request_completion(self, messages: List[Dict[str, Any]], tools: Optional[List[Dict[str, Any]]] = None) -> Dict[str, Any]:
        """封装基础的 Chat Completion 请求喵"""
        url = f"{self.base_url}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        loop = asyncio.get_event_loop()
        try:
            response = await loop.run_in_executor(
                None,
                lambda: requests.post(url, headers=headers, json=payload, timeout=60)
            )
            response.raise_for_status()
            return response.json().get("choices", [{}])[0].get("message", {})
        except Exception as e:
            logger.error(f"LLM request failed: {e}")
            raise

    async def chat_with_tools(self, messages: List[Dict[str, Any]], registry, ws_client) -> str:
        """带工具调用循环的智能对话喵"""
        max_turns = 5  # 防止死循环喵
        
        for _ in range(max_turns):
            tools = registry.get_all_schemas()
            response_msg = await self._request_completion(messages, tools=tools if tools else None)
            
            # 将模型回复加入上下文喵
            messages.append(response_msg)
            
            tool_calls = response_msg.get("tool_calls")
            if not tool_calls:
                # 模型不再需要调用工具，返回最终文本喵
                return response_msg.get("content", "")

            # 处理工具调用喵
            for tool_call in tool_calls:
                function_name = tool_call["function"]["name"]
                function_args = json.loads(tool_call["function"]["arguments"])
                call_id = tool_call["id"]
                
                try:
                    # 分发执行工具，传入 ws_client 喵
                    result = await registry.dispatch_tool(function_name, function_args, ws_client=ws_client)
                    
                    # 将工具执行结果反馈给模型喵
                    messages.append({
                        "tool_call_id": call_id,
                        "role": "tool",
                        "name": function_name,
                        "content": json.dumps(result, ensure_ascii=False)
                    })
                except Exception as e:
                    logger.error(f"Error executing tool {function_name}: {e}")
                    messages.append({
                        "tool_call_id": call_id,
                        "role": "tool",
                        "name": function_name,
                        "content": json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)
                    })
        
        return "达到最大决策轮次，请尝试简化问题喵。"
