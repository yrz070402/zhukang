"""
应用配置 - 从环境变量读取配置信息
"""
from functools import lru_cache
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置类"""

    # 豆包 API 配置
    doubao_api_key: str = ""
    doubao_base_url: str = "https://ark.cn-beijing.volces.com/api/v3"
    doubao_model_id: str = ""

    # 应用配置
    app_name: str = "筑康 API"
    debug: bool = True
    timezone: str = "Asia/Shanghai"

    # 数据库配置（PostgreSQL + asyncpg）
    database_url: str = ""

    # JWT 配置（后续认证阶段会使用）
    jwt_secret_key: str = "change-this-in-production"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 60

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    """获取配置单例"""
    return Settings()
