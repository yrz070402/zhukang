"""
Pydantic 数据模型 - API 请求/响应模型
"""
from pydantic import BaseModel
from typing import Optional


class FoodAnalysisResponse(BaseModel):
    """食物分析响应模型"""
    food_name: str
    calories: float
    protein: float
    fat: Optional[float] = None
    carbs: Optional[float] = None

    class Config:
        json_schema_extra = {
            "example": {
                "food_name": "测试汉堡",
                "calories": 450.0,
                "protein": 20.0,
                "fat": 15.0,
                "carbs": 50.0
            }
        }
