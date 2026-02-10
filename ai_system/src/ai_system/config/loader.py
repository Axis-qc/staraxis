"""
config/loader.py

作用（description）：
- 负责从 ai_system/config/config.yaml 加载 AI 助手的所有配置喵。
- 使用 Pydantic 进行模型校验，确保配置项缺失或格式错误时能及时发现喵。

提供的接口/API：
- load_config() -> AppConfig：解析并返回完整的配置对象喵。

注意事项（important_notes）：
- 本模块不读取任何环境变量，完全依赖本地 YAML 文件，实现解压即用喵。
- 路径处理相对于 ai_system 根目录喵。
"""

import os
import yaml
from pathlib import Path
from pydantic import BaseModel, Field
from typing import Optional

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

class AiConfig(BaseModel):
    provider: str = "openai"
    model: str
    api_key: str
    base_url: str
    temperature: float = 0.7
    max_tokens: int = 2048

class AgentConfig(BaseModel):
    name: str = "StarAxis Assistant"
    system_prompt: str

class AppConfig(BaseModel):
    server: ServerConfig
    auth: AuthConfig
    ai: AiConfig
    agent: AgentConfig

def load_config() -> AppConfig:
    # 假设运行路径在 ai_system 根目录或 src 下，我们寻找 config/config.yaml 喵
    # run_agent.bat 会在 ai_system 目录下运行喵
    config_path = Path("config/config.yaml")
    if not config_path.exists():
        # 尝试向上寻找（针对开发环境）喵
        config_path = Path(__file__).parent.parent.parent.parent / "config" / "config.yaml"
    
    if not config_path.exists():
        raise FileNotFoundError(f"Configuration file not found at {config_path.absolute()}")

    with open(config_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
        return AppConfig(**data)
