"""
brain/openai_provider.py

作用（description）：
- 对接 OpenAI 兼容接口，并实现自动化的 Tool Calling 循环喵。
- 负责管理对话上下文，并根据模型需求调用本地工具注册表（registry）喵。
- 根据配置文件中 ai.active_provider 自动切换不同厂商的 API 配置喵。
- 追踪 Token 消耗量，包括提示词、生成文本和工具调用喵。

提供的接口/API：
- chat_with_tools(messages, registry, **kwargs) -> ChatResult：带工具循环的智能对话接口喵。
- get_usage_stats() -> Dict：获取累计 Token 使用统计喵。

注意事项（important_notes）：
- 采用异步模型处理 API 请求与工具执行喵。
- 运行时日志已移除"喵"以保持专业性喵。
- 支持 OpenAI、Anthropic、DeepSeek、Local(Ollama/LM Studio) 等兼容 OpenAI 接口的厂商喵。
- Anthropic 的 token 计算方式不同，需要注意兼容性喵。
"""

import asyncio
import logging
import json
import time
import requests
from typing import List, Dict, Any, Optional, Callable
from dataclasses import dataclass, field
from ..config.loader import AppConfig, get_active_provider_config, ProviderConfig

logger = logging.getLogger("ai_system.brain")


@dataclass
class TokenUsage:
    """单次请求的 Token 使用情况"""
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    
    def to_dict(self) -> Dict[str, int]:
        return {
            "prompt_tokens": self.prompt_tokens,
            "completion_tokens": self.completion_tokens,
            "total_tokens": self.total_tokens
        }


@dataclass
class ToolCallInfo:
    """工具调用信息"""
    name: str
    arguments: Dict[str, Any]
    result: Any = None
    duration_ms: int = 0
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "arguments": self.arguments,
            "result": self.result if isinstance(self.result, (dict, list, str, int, float, bool, type(None))) else str(self.result),
            "duration_ms": self.duration_ms
        }


@dataclass
class ThinkingStep:
    """思考过程的一步"""
    type: str  # "llm_call", "tool_call", "reasoning"
    content: str = ""
    tool_calls: List[ToolCallInfo] = field(default_factory=list)
    usage: Optional[TokenUsage] = None
    duration_ms: int = 0
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            "type": self.type,
            "content": self.content,
            "duration_ms": self.duration_ms
        }
        if self.tool_calls:
            result["tool_calls"] = [tc.to_dict() for tc in self.tool_calls]
        if self.usage:
            result["usage"] = self.usage.to_dict()
        return result


@dataclass
class ChatResult:
    """对话结果，包含回复内容和统计信息"""
    content: str
    thinking_steps: List[ThinkingStep] = field(default_factory=list)
    total_usage: TokenUsage = field(default_factory=TokenUsage)
    tool_calls_count: int = 0
    total_duration_ms: int = 0
    provider: str = ""
    model: str = ""
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "content": self.content,
            "thinking_steps": [step.to_dict() for step in self.thinking_steps],
            "usage": self.total_usage.to_dict(),
            "tool_calls_count": self.tool_calls_count,
            "total_duration_ms": self.total_duration_ms,
            "provider": self.provider,
            "model": self.model
        }


class TokenTracker:
    """Token 使用量追踪器"""
    
    def __init__(self):
        self.session_prompt_tokens = 0
        self.session_completion_tokens = 0
        self.session_total_tokens = 0
        self.request_count = 0
        self.tool_call_count = 0
        
    def record_usage(self, usage: TokenUsage):
        """记录一次请求的 Token 使用量"""
        self.session_prompt_tokens += usage.prompt_tokens
        self.session_completion_tokens += usage.completion_tokens
        self.session_total_tokens += usage.total_tokens
        self.request_count += 1
        
    def record_tool_call(self):
        """记录一次工具调用"""
        self.tool_call_count += 1
        
    def get_stats(self) -> Dict[str, Any]:
        """获取统计信息"""
        return {
            "session_prompt_tokens": self.session_prompt_tokens,
            "session_completion_tokens": self.session_completion_tokens,
            "session_total_tokens": self.session_total_tokens,
            "request_count": self.request_count,
            "tool_call_count": self.tool_call_count,
            "avg_tokens_per_request": self.session_total_tokens // max(1, self.request_count)
        }
        
    def reset(self):
        """重置统计"""
        self.session_prompt_tokens = 0
        self.session_completion_tokens = 0
        self.session_total_tokens = 0
        self.request_count = 0
        self.tool_call_count = 0


class OpenAiProvider:
    def __init__(self, config: AppConfig):
        self.config = config
        self.ai_config = config.ai
        
        # 获取当前生效的厂商配置
        provider_config = get_active_provider_config(config)
        if provider_config is None:
            active_provider = self.ai_config.active_provider
            available = list(config.ai.providers.model_dump().keys())
            raise ValueError(
                f"AI provider '{active_provider}' not configured. "
                f"Available providers: {available}"
            )
        
        self.provider_config: ProviderConfig = provider_config
        self.active_provider = self.ai_config.active_provider
        self.token_tracker = TokenTracker()
        
        logger.info(f"AI Provider initialized: {self.active_provider} ({self.provider_config.model})")

    async def _request_completion(
        self, 
        messages: List[Dict[str, Any]], 
        tools: Optional[List[Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        """封装基础的 Chat Completion 请求，返回完整响应"""
        url = f"{self.provider_config.base_url}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.provider_config.api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": self.provider_config.model,
            "messages": messages,
            "temperature": self.ai_config.temperature,
            "max_tokens": self.ai_config.max_tokens
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
            return response.json()  # 返回完整响应，包含 usage
        except requests.exceptions.HTTPError as e:
            logger.error(f"LLM HTTP error: {e.response.status_code} - {e.response.text}")
            raise
        except requests.exceptions.ConnectionError as e:
            logger.error(f"LLM connection error: {e}")
            raise
        except Exception as e:
            logger.error(f"LLM request failed: {e}")
            raise

    def _extract_usage(self, response_data: Dict[str, Any]) -> TokenUsage:
        """从响应中提取 Token 使用量"""
        usage_data = response_data.get("usage", {})
        return TokenUsage(
            prompt_tokens=usage_data.get("prompt_tokens", 0),
            completion_tokens=usage_data.get("completion_tokens", 0),
            total_tokens=usage_data.get("total_tokens", 0)
        )

    async def chat_with_tools(
        self, 
        messages: List[Dict[str, Any]], 
        registry, 
        ws_client,
        on_thinking_update: Optional[Callable[[ThinkingStep], None]] = None
    ) -> ChatResult:
        """
        带工具调用循环的智能对话
        
        Args:
            messages: 消息历史
            registry: 工具注册表
            ws_client: WebSocket 客户端（用于工具调用）
            on_thinking_update: 思考过程更新回调
            
        Returns:
            ChatResult: 包含回复内容和统计信息
        """
        max_turns = 5  # 防止死循环喵
        thinking_steps: List[ThinkingStep] = []
        total_usage = TokenUsage()
        tool_calls_count = 0
        start_time = time.time()
        
        for turn in range(max_turns):
            turn_start = time.time()
            tools = registry.get_all_schemas()
            
            try:
                response_data = await self._request_completion(
                    messages, 
                    tools=tools if tools else None
                )
            except Exception as e:
                logger.error(f"Failed to get completion on turn {turn + 1}: {e}")
                return ChatResult(
                    content=f"AI 服务请求失败: {str(e)}",
                    thinking_steps=thinking_steps,
                    total_usage=total_usage,
                    provider=self.active_provider,
                    model=self.provider_config.model
                )
            
            # 提取 Token 使用量
            usage = self._extract_usage(response_data)
            total_usage.prompt_tokens += usage.prompt_tokens
            total_usage.completion_tokens += usage.completion_tokens
            total_usage.total_tokens += usage.total_tokens
            self.token_tracker.record_usage(usage)
            
            response_msg = response_data.get("choices", [{}])[0].get("message", {})
            
            # 将模型回复加入上下文喵
            messages.append(response_msg)
            
            tool_calls = response_msg.get("tool_calls")
            content = response_msg.get("content", "")
            
            if not tool_calls:
                # 模型不再需要调用工具，返回最终文本喵
                step = ThinkingStep(
                    type="reasoning",
                    content=content,
                    usage=usage,
                    duration_ms=int((time.time() - turn_start) * 1000)
                )
                thinking_steps.append(step)
                if on_thinking_update:
                    on_thinking_update(step)
                    
                total_duration = int((time.time() - start_time) * 1000)
                return ChatResult(
                    content=content,
                    thinking_steps=thinking_steps,
                    total_usage=total_usage,
                    tool_calls_count=tool_calls_count,
                    total_duration_ms=total_duration,
                    provider=self.active_provider,
                    model=self.provider_config.model
                )

            # 处理工具调用喵
            step = ThinkingStep(
                type="tool_call",
                content=content or "正在调用工具获取数据...",
                usage=usage,
                duration_ms=int((time.time() - turn_start) * 1000)
            )
            
            for tool_call in tool_calls:
                function_name = tool_call["function"]["name"]
                function_args = json.loads(tool_call["function"]["arguments"])
                call_id = tool_call["id"]
                
                tool_start = time.time()
                tool_calls_count += 1
                self.token_tracker.record_tool_call()
                
                try:
                    # 分发执行工具，传入 ws_client 喵
                    result = await registry.dispatch_tool(
                        function_name, 
                        function_args, 
                        ws_client=ws_client
                    )
                    tool_duration = int((time.time() - tool_start) * 1000)
                    
                    tool_info = ToolCallInfo(
                        name=function_name,
                        arguments=function_args,
                        result=result,
                        duration_ms=tool_duration
                    )
                    step.tool_calls.append(tool_info)
                    
                    # 将工具执行结果反馈给模型喵
                    tool_content = json.dumps(result, ensure_ascii=False)
                    messages.append({
                        "tool_call_id": call_id,
                        "role": "tool",
                        "name": function_name,
                        "content": tool_content
                    })
                    
                    # 估算工具返回内容的 Token 数（粗略估算：1 token ≈ 4 字符）
                    tool_tokens = len(tool_content) // 4
                    total_usage.prompt_tokens += tool_tokens
                    total_usage.total_tokens += tool_tokens
                    
                except Exception as e:
                    tool_duration = int((time.time() - tool_start) * 1000)
                    logger.error(f"Error executing tool {function_name}: {e}")
                    
                    tool_info = ToolCallInfo(
                        name=function_name,
                        arguments=function_args,
                        result={"ok": False, "error": str(e)},
                        duration_ms=tool_duration
                    )
                    step.tool_calls.append(tool_info)
                    
                    error_content = json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)
                    messages.append({
                        "tool_call_id": call_id,
                        "role": "tool",
                        "name": function_name,
                        "content": error_content
                    })
            
            thinking_steps.append(step)
            if on_thinking_update:
                on_thinking_update(step)
        
        total_duration = int((time.time() - start_time) * 1000)
        return ChatResult(
            content="达到最大决策轮次，请尝试简化问题喵。",
            thinking_steps=thinking_steps,
            total_usage=total_usage,
            tool_calls_count=tool_calls_count,
            total_duration_ms=total_duration,
            provider=self.active_provider,
            model=self.provider_config.model
        )
    
    def get_usage_stats(self) -> Dict[str, Any]:
        """获取累计 Token 使用统计"""
        return self.token_tracker.get_stats()
    
    def reset_usage_stats(self):
        """重置 Token 使用统计"""
        self.token_tracker.reset()
