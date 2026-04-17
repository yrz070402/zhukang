"""
API v1 路由聚合
"""
from fastapi import APIRouter
from app.api.v1.endpoints import auth, food

router = APIRouter()

# 注册食物分析路由
router.include_router(food.router, prefix="/food", tags=["食物分析"])
router.include_router(auth.router, prefix="/auth", tags=["用户注册"])
