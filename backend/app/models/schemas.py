"""
Pydantic 数据模型 - API 请求/响应模型
"""
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator

from app.models.models import GoalType, SexType


class FoodAnalysisResponse(BaseModel):
    """食物分析响应模型"""
    food_name: str
    calories: float
    protein: float
    fat: Optional[float] = None
    carbs: Optional[float] = None
    image_url: Optional[str] = None

    class Config:
        json_schema_extra = {
            "example": {
                "food_name": "测试汉堡",
                "calories": 450.0,
                "protein": 20.0,
                "fat": 15.0,
                "carbs": 50.0,
                "image_url": "/static/food_images/xxx.png"
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


class UserDailyIntakeSummaryResponse(BaseModel):
    user_id: UUID
    start_at: str
    end_at: str
    total_calories_kcal: float
    total_protein_g: float
    total_fat_g: float
    total_carb_g: float


class ReportSeriesPoint(BaseModel):
    date: str
    calories_kcal: float | None
    protein_g: float | None
    fat_g: float | None
    carb_g: float | None


class ReportGoalLine(BaseModel):
    calories_kcal: float
    protein_g: float
    fat_g: float
    carb_g: float


class UserReportResponse(BaseModel):
    user_id: UUID
    start_at: str
    end_at: str
    points: list[ReportSeriesPoint]
    goal_line: ReportGoalLine


class UserTagInfo(BaseModel):
    id: int
    display_name: str


class UserProfileDetailResponse(BaseModel):
    user_id: UUID
    account: str
    nickname: str
    avatar_index: int
    age: int
    sex: str
    height_cm: float
    weight_kg: float
    activity_level: int
    goal_type: str
    goal_index: int
    target_daily_calories_kcal: float
    target_protein_g: float
    target_fat_g: float
    target_carb_g: float
    dietary_tags: list[UserTagInfo]


class UserProfileUpdateRequest(BaseModel):
    user_id: UUID
    nickname: str = Field(min_length=1, max_length=128)
    avatar_index: int = Field(ge=0, le=11)
    age: int = Field(ge=1, le=120)
    sex: SexType
    height_cm: float = Field(gt=0)
    weight_kg: float = Field(gt=0)
    activity_level: int = Field(ge=0, le=3)
    goal_type: GoalType
    target_daily_calories_kcal: float | None = Field(default=None, gt=0)
    target_protein_g: float | None = Field(default=None, ge=0)
    target_fat_g: float | None = Field(default=None, ge=0)
    target_carb_g: float | None = Field(default=None, ge=0)


class UserTagsUpdateRequest(BaseModel):
    user_id: UUID
    dietary_preferences: list[str] = Field(default_factory=list)

    @field_validator("dietary_preferences")
    @classmethod
    def validate_dietary_preferences(cls, value: list[str]) -> list[str]:
        cleaned = [item.strip() for item in value if item and item.strip()]
        if len(cleaned) != len(set(cleaned)):
            raise ValueError("dietary_preferences contains duplicate values")
        return cleaned


class UserTagsUpdateResponse(BaseModel):
    user_id: UUID
    tag_ids: list[int]
    tags: list[UserTagInfo]
    message: str


class DietMapIntakeItem(BaseModel):
    """Bitelog 网格中的单条餐次条目。"""
    id: UUID
    intake_time: str
    meal_type: str
    food_name: str
    image_url: Optional[str] = None
    calories_kcal: float
    protein_g: float
    fat_g: float
    carb_g: float


class DietMapDay(BaseModel):
    """Bitelog 网格一天的聚合结果。"""
    business_day: str
    weekday: int  # ISO 周几：1=周一 ... 7=周日
    intakes: list[DietMapIntakeItem]


class DietMapResponse(BaseModel):
    """Bitelog 网格接口整体响应。"""
    user_id: UUID
    period: str  # weekly 或 monthly
    offset: int
    start_at: str
    end_at: str
    days: list[DietMapDay]


# ==================== 饮食推荐相关模型 ====================

class NextMealTarget(BaseModel):
    """下一餐目标"""
    target_calories: str
    focus_macros: str


class RecommendedDish(BaseModel):
    """推荐菜品"""
    dish_name: str
    reason: str
    estimated_calories: str
    cooking_steps: str = ""


class RecommendRequest(BaseModel):
    """饮食推荐请求"""
    userId: str
    selectedFoods: list[str] = Field(default_factory=list)
    manualInput: Optional[str] = None
    mealType: str  # BREAKFAST, LUNCH, DINNER


class RecommendResponse(BaseModel):
    """饮食推荐响应"""
    analysis: str
    next_meal_target: NextMealTarget
    recommendations: list[RecommendedDish]

    class Config:
        json_schema_extra = {
            "example": {
                "analysis": "上一餐碳水偏高，蛋白质摄入不足。",
                "next_meal_target": {
                    "target_calories": "400-500 kcal",
                    "focus_macros": "蛋白质、膳食纤维"
                },
                "recommendations": [
                    {
                        "dish_name": "清蒸鲈鱼配西兰花",
                        "reason": "鲈鱼富含优质蛋白，清蒸保留营养；西兰花补充膳食纤维",
                        "estimated_calories": "约 380 kcal"
                    }
                ]
            }
        }
