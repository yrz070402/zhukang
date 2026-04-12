"""
筑康后端服务 - FastAPI 主应用
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime

# 导入 API 路由
from app.api.v1 import router as api_v1_router

# 创建 FastAPI 应用实例
app = FastAPI(
    title="筑康 API",
    description="饮食、保健品与医疗一体化健康管理平台后端服务",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# 配置 CORS（允许 Android 客户端访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

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
        "timestamp": datetime.now().isoformat()
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
