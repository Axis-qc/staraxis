"""
config/loader.py

作用（description）：
- 负责从 ai_system/config/config.yaml 加载 AI 助手的所有配置喵。
- 使用 Pydantic 进行模型校验，确保配置项缺失或格式错误时能及时发现喵。
- 支持多厂商配置，根据 active_provider 自动加载对应厂商的配置喵。

提供的接口/API：
- load_config() -> AppConfig：解析并返回完整的配置对象喵。
- get_active_provider_config(config: AppConfig) -> ProviderConfig: 获取当前生效的厂商配置喵。

注意事项（important_notes）：
- 本模块不读取任何环境变量，完全依赖本地 YAML 文件，实现解压即用喵。
- 路径处理相对于 ai_system 根目录喵。
- 配置文件中 ai.providers 存储各厂商配置，ai.active_provider 指定当前使用哪个厂商喵。
"""

import os
import yaml
from pathlib import Path
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any

class ServerConfig(BaseModel):
    host: str = "127.0.0.1"
    port: int = 17890
    use_ssl: bool = False
    auto_start: bool = True
    show_console: bool = False
    auto_exit: bool = True
    idle_exit_seconds: int = 120

class AuthConfig(BaseModel):
    username: str
    password: str

class ProviderConfig(BaseModel):
    """单个AI厂商的配置"""
    base_url: str
    api_key: str
    model: str

class AiProvidersConfig(BaseModel):
    """所有厂商的配置集合"""
    openai: Optional[ProviderConfig] = None
    anthropic: Optional[ProviderConfig] = None
    deepseek: Optional[ProviderConfig] = None
    local: Optional[ProviderConfig] = None
    
    def get_provider(self, provider_id: str) -> Optional[ProviderConfig]:
        """根据厂商ID获取配置"""
        return getattr(self, provider_id, None)

class AiConfig(BaseModel):
    active_provider: str = "openai"  # 当前生效的厂商 ID
    temperature: float = 0.7
    max_tokens: int = 2048
    providers: AiProvidersConfig  # 各厂商配置
    
    def get_active_config(self) -> Optional[ProviderConfig]:
        """获取当前生效厂商的配置"""
        return self.providers.get_provider(self.active_provider)

class AgentConfig(BaseModel):
    name: str = "StarAxis Assistant"
    system_prompt: str

class AppConfig(BaseModel):
    server: ServerConfig
    auth: AuthConfig
    ai: AiConfig
    agent: AgentConfig

def _get_config_path() -> Path:
    """获取配置文件路径"""
    # 首先尝试当前工作目录下的 config/config.yaml
    config_path = Path("config/config.yaml")
    if config_path.exists():
        return config_path
    
    # 尝试从本文件位置向上寻找（开发环境）
    dev_path = Path(__file__).parent.parent.parent.parent / "config" / "config.yaml"
    if dev_path.exists():
        return dev_path
    
    raise FileNotFoundError(f"Configuration file not found at {config_path.absolute()} or {dev_path.absolute()}")

def _transform_raw_data(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    将原始 YAML 数据转换为符合 Pydantic 模型的格式。
    处理新旧配置格式的兼容性。
    """
    if "ai" not in data:
        data["ai"] = {}
    
    ai_data = data["ai"]
    
    # 如果存在旧格式配置（单层结构），转换为新格式
    if "provider" in ai_data and "providers" not in ai_data:
        old_provider = ai_data.get("provider", "openai")
        old_config = {
            "base_url": ai_data.get("base_url", ""),
            "api_key": ai_data.get("api_key", ""),
            "model": ai_data.get("model", "")
        }
        ai_data["providers"] = {
            old_provider: old_config
        }
        ai_data["active_provider"] = old_provider
    
    # 确保 providers 存在
    if "providers" not in ai_data:
        ai_data["providers"] = {}
    
    # 确保 active_provider 存在
    if "active_provider" not in ai_data:
        # 尝试从第一个有配置的厂商中选择
        providers = ai_data.get("providers", {})
        if providers:
            ai_data["active_provider"] = list(providers.keys())[0]
        else:
            ai_data["active_provider"] = "openai"
    
    return data

def load_config() -> AppConfig:
    """
    加载并解析配置文件。
    
    Returns:
        AppConfig: 完整的配置对象
        
    Raises:
        FileNotFoundError: 配置文件不存在
        ValueError: 配置格式错误
    """
    config_path = _get_config_path()
    
    with open(config_path, "r", encoding="utf-8") as f:
        raw_data = yaml.safe_load(f)
    
    # 转换数据格式（处理兼容性）
    transformed_data = _transform_raw_data(raw_data)
    
    return AppConfig(**transformed_data)

def get_active_provider_config(config: AppConfig) -> Optional[ProviderConfig]:
    """
    获取当前生效的厂商配置。
    
    Args:
        config: 应用配置对象
        
    Returns:
        ProviderConfig: 当前生效厂商的配置，如果不存在则返回 None
    """
    return config.ai.get_active_config()
