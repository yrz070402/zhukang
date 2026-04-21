"""
筑康后端服务 - FastAPI 主应用
"""
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

# 导入 API 路由
from app.api.v1 import router as api_v1_router
from app.core.config import get_settings
from app.core.database import check_database_connection, close_db_engine


settings = get_settings()


def get_local_now_iso() -> str:
    """返回应用配置时区下的当前时间，默认使用北京时区。"""
    return datetime.now(ZoneInfo(settings.timezone)).isoformat()


@asynccontextmanager
async def lifespan(_: FastAPI):
    """应用生命周期：启动时检测数据库可达，关闭时释放连接池。"""
    await check_database_connection()
    yield
    await close_db_engine()

# 创建 FastAPI 应用实例
app = FastAPI(
    title="筑康 API",
    description="饮食、保健品与医疗一体化健康管理平台后端服务",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# 配置 CORS（允许 Android 客户端访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 挂载静态资源目录（供 Bitelog 缩略图、后续其他静态文件访问）
_static_root = Path(settings.static_root)
_static_root.mkdir(parents=True, exist_ok=True)
(_static_root / settings.food_image_subdir).mkdir(parents=True, exist_ok=True)
app.mount("/static", StaticFiles(directory=str(_static_root)), name="static")

# 注册 API 路由
app.include_router(api_v1_router, prefix="/api/v1")


@app.get("/ping", tags=["健康检查"])
async def ping():
    """
    健康检查接口
    用于验证服务是否正常运行
    """
    return {
        "status": "ok",
        "message": "筑康后端已启动",
        "timestamp": get_local_now_iso(),
    }


@app.get("/", tags=["健康检查"])
async def root():
    """
    根路径 - 返回 API 信息
    """
    return {
        "name": "筑康 API",
        "version": "1.0.0",
        "description": "饮食、保健品与医疗一体化健康管理平台",
        "docs": "/docs"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
