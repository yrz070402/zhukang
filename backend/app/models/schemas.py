"""
Pydantic 数据模型 - API 请求/响应模型
"""
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator

from app.models.models import SexType


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


class RegisterRequest(BaseModel):
    account: str = Field(min_length=3, max_length=128)
    password: str = Field(min_length=6, max_length=128)


class LoginRequest(BaseModel):
    account: str = Field(min_length=3, max_length=128)
    password: str = Field(min_length=6, max_length=128)


class RegisterResponse(BaseModel):
    user_id: UUID
    account: str
    nickname: str
    message: str


class LoginResponse(BaseModel):
    user_id: UUID
    account: str
    nickname: str
    message: str


class ProfileSetupRequest(BaseModel):
    user_id: UUID
    age: int = Field(ge=1, le=120)
    sex: Optional[SexType] = None
    height_cm: float = Field(gt=0)
    weight_kg: float = Field(gt=0)
    activity_level: int = Field(ge=0, le=3, default=0)
    # 前端目标三选一，按约定映射为 0/1/2。
    goal_index: int = Field(ge=0, le=2)
    dietary_preferences: list[str] = Field(default_factory=list)

    @field_validator("dietary_preferences")
    @classmethod
    def validate_dietary_preferences(cls, value: list[str]) -> list[str]:
        cleaned = [item.strip() for item in value if item and item.strip()]
        if len(cleaned) != len(set(cleaned)):
            raise ValueError("dietary_preferences contains duplicate values")
        return cleaned


class ProfileSetupResponse(BaseModel):
    user_id: UUID
    goal_type: str
    tag_ids: list[int]
    message: str


class NicknameUpdateRequest(BaseModel):
    user_id: UUID
    nickname: str = Field(min_length=1, max_length=128)


class NicknameUpdateResponse(BaseModel):
    user_id: UUID
    nickname: str
    message: str


class PopularTagItem(BaseModel):
    display_name: str
    user_num: int


class PopularTagsResponse(BaseModel):
    items: list[PopularTagItem]


class UserDailyGoalTargetsResponse(BaseModel):
    user_id: UUID
    target_daily_calories_kcal: float
    target_protein_g: float
    target_fat_g: float
    target_carb_g: float
