"""数据库连接与会话管理。"""
from collections.abc import AsyncGenerator

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import get_settings

settings = get_settings()

# future=True 在 SQLAlchemy 2 已是默认行为；保留显式配置便于阅读。
engine = create_async_engine(
    settings.database_url,
    echo=settings.debug,
    pool_pre_ping=True,
)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    autocommit=False,
    autoflush=False,
    expire_on_commit=False,
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """为 FastAPI 依赖注入提供异步会话。"""
    async with AsyncSessionLocal() as session:
        yield session


async def check_database_connection() -> None:
    """启动时执行轻量连通性检查。"""
    async with engine.connect() as connection:
        await connection.execute(text("SELECT 1"))


async def close_db_engine() -> None:
    """应用关闭时释放数据库连接池。"""
    await engine.dispose()
