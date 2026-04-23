"""
饮食推荐接口 - 调用大模型生成个性化饮食建议
"""
import json
import logging
from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.database import get_db
from app.models.models import MealType, NutritionIntake, User, UserProfile, UserGoal, UserTag, Tag
from app.models.schemas import RecommendRequest, RecommendResponse, NextMealTarget, RecommendedDish
from app.services.doubao_service import call_doubao_text, RECOMMENDATION_PROMPT, extract_json

router = APIRouter()
logger = logging.getLogger(__name__)
settings = get_settings()

# 餐次目标分配比例
MEAL_RATIOS = {
    MealType.BREAKFAST: 0.30,
    MealType.LUNCH: 0.40,
    MealType.DINNER: 0.30,
    MealType.SNACK: 0.10,
}

# 目标类型中文映射
GOAL_TYPE_MAP = {
    "MUSCLE_GAIN": "增肌",
    "FAT_LOSS": "减脂",
    "MAINTAIN": "保持健康",
    "CHRONIC_DISEASE_MANAGEMENT": "慢病管理",
}

# 活动水平中文映射
ACTIVITY_LEVEL_MAP = {
    0: "久坐（几乎不运动）",
    1: "轻度活动（每周运动1-2次）",
    2: "中度活动（每周运动3-4次）",
    3: "高强度活动（每周运动5次以上）",
}

# 餐次类型中文映射
MEAL_TYPE_CN_MAP = {
    MealType.BREAKFAST: "早餐",
    MealType.LUNCH: "午餐",
    MealType.DINNER: "晚餐",
    MealType.SNACK: "加餐",
}


async def _get_user_profile(db: AsyncSession, user_id: UUID) -> Optional[UserProfile]:
    """获取用户资料"""
    return await db.scalar(
        select(UserProfile).where(UserProfile.user_id == user_id)
    )


async def _get_user_goal(db: AsyncSession, user_id: UUID) -> Optional[UserGoal]:
    """获取用户目标"""
    return await db.scalar(
        select(UserGoal).where(UserGoal.user_id == user_id)
    )


async def _get_user_dietary_tags(db: AsyncSession, user_id: UUID) -> list[str]:
    """获取用户饮食标签（含禁忌/偏好）"""
    result = await db.execute(
        select(Tag.display_name)
        .join(UserTag, UserTag.tag_id == Tag.id)
        .where(UserTag.user_id == user_id)
    )
    return [row[0] for row in result.all()]


async def _get_today_meal_intake(
    db: AsyncSession,
    user_id: UUID,
    meal_type: MealType
) -> dict:
    """获取今日指定餐次的摄入量"""
    now_local = datetime.now(ZoneInfo(settings.timezone))
    anchor_today = now_local.replace(hour=4, minute=0, second=0, microsecond=0)

    if now_local < anchor_today:
        start_at = anchor_today - __import__("datetime").timedelta(days=1)
    else:
        start_at = anchor_today

    result = await db.execute(
        select(
            func.coalesce(func.sum(NutritionIntake.energy_kcal), 0).label("calories"),
            func.coalesce(func.sum(NutritionIntake.protein_g), 0).label("protein"),
            func.coalesce(func.sum(NutritionIntake.fat_g), 0).label("fat"),
            func.coalesce(func.sum(NutritionIntake.carb_g), 0).label("carbs"),
        ).where(
            NutritionIntake.user_id == user_id,
            NutritionIntake.deleted_at.is_(None),
            NutritionIntake.intake_time >= start_at,
            NutritionIntake.meal_type == meal_type,
        )
    )
    row = result.one()

    return {
        "calories": float(row.calories or 0),
        "protein": float(row.protein or 0),
        "fat": float(row.fat or 0),
        "carbs": float(row.carbs or 0),
    }


def _calculate_nutrition_gap(
    user_goal: UserGoal,
    meal_type: MealType,
    meal_intake: dict
) -> dict:
    """计算营养缺口"""
    ratio = MEAL_RATIOS.get(meal_type, 0.30)

    target_calories = float(user_goal.target_daily_calories_kcal or 0) * ratio
    target_protein = float(user_goal.target_protein_g or 0) * ratio
    target_fat = float(user_goal.target_fat_g or 0) * ratio
    target_carbs = float(user_goal.target_carb_g or 0) * ratio

    return {
        "remaining_calories": max(0, target_calories - meal_intake["calories"]),
        "remaining_protein": max(0, target_protein - meal_intake["protein"]),
        "remaining_fat": max(0, target_fat - meal_intake["fat"]),
        "remaining_carbs": max(0, target_carbs - meal_intake["carbs"]),
    }


@router.post("/next-meal", response_model=RecommendResponse)
async def get_next_meal_recommendation(
    request: RecommendRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    获取下一餐饮食推荐

    根据用户信息、营养缺口和选择的食物，调用大模型生成个性化饮食建议
    """
    # 解析用户 ID
    try:
        user_id = UUID(request.userId)
    except ValueError:
        raise HTTPException(status_code=400, detail="无效的用户 ID")

    # 解析餐次
    try:
        meal_type = MealType[request.mealType.upper()]
    except KeyError:
        raise HTTPException(status_code=400, detail=f"无效的餐次类型: {request.mealType}")

    # 获取用户信息
    user = await db.scalar(select(User).where(User.id == user_id))
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    user_profile = await _get_user_profile(db, user_id)
    user_goal = await _get_user_goal(db, user_id)
    dietary_tags = await _get_user_dietary_tags(db, user_id)

    if not user_goal:
        raise HTTPException(status_code=400, detail="用户未设置健康目标，请先完善个人资料")

    # 获取该餐次已摄入
    meal_intake = await _get_today_meal_intake(db, user_id, meal_type)

    # 计算营养缺口
    nutrition_gap = _calculate_nutrition_gap(user_goal, meal_type, meal_intake)

    # 构建 Prompt
    prompt = RECOMMENDATION_PROMPT.format(
        age=user_profile.age if user_profile else "未知",
        sex="男" if user_profile and user_profile.sex.value == "MALE" else "女" if user_profile else "未知",
        height_cm=user_profile.height_cm if user_profile else "未知",
        weight_kg=user_profile.weight_kg if user_profile else "未知",
        goal_type=GOAL_TYPE_MAP.get(user_goal.goal_type.value, user_goal.goal_type.value),
        activity_level=ACTIVITY_LEVEL_MAP.get(int(user_goal.activity_level or 0), "未知"),
        dietary_restrictions="、".join(dietary_tags) if dietary_tags else "无特殊禁忌",
        remaining_calories=round(nutrition_gap["remaining_calories"], 1),
        remaining_protein=round(nutrition_gap["remaining_protein"], 1),
        remaining_fat=round(nutrition_gap["remaining_fat"], 1),
        remaining_carbs=round(nutrition_gap["remaining_carbs"], 1),
        selected_foods="、".join(request.selectedFoods) if request.selectedFoods else "无",
        manual_input=request.manualInput or "无",
        meal_type_cn=MEAL_TYPE_CN_MAP.get(meal_type, "正餐"),
    )

    # 调用 LLM
    try:
        llm_response = await call_doubao_text(prompt, max_tokens=1500, temperature=0.7)
        logger.info(f"LLM 返回: {llm_response}")

        # 解析 JSON
        json_str = extract_json(llm_response)
        data = json.loads(json_str)

        # 解析 next_meal_target
        target_data = data.get("next_meal_target", {})
        next_meal_target = NextMealTarget(
            target_calories=target_data.get("target_calories", ""),
            focus_macros=target_data.get("focus_macros", "")
        )

        # 解析 recommendations
        recommendations = []
        for item in data.get("recommendations", []):
            recommendations.append(RecommendedDish(
                dish_name=item.get("dish_name", ""),
                reason=item.get("reason", ""),
                estimated_calories=item.get("estimated_calories", ""),
                cooking_steps=item.get("cooking_steps", "")
            ))

        return RecommendResponse(
            analysis=data.get("analysis", ""),
            next_meal_target=next_meal_target,
            recommendations=recommendations
        )

    except json.JSONDecodeError as e:
        logger.error(f"JSON 解析错误: {e}")
        # 返回默认响应
        return RecommendResponse(
            analysis="抱歉，AI 返回格式异常，请稍后重试。",
            next_meal_target=NextMealTarget(target_calories="", focus_macros=""),
            recommendations=[]
        )

    except Exception as e:
        logger.error(f"获取推荐失败: {e}")
        raise HTTPException(status_code=500, detail=f"获取推荐失败: {str(e)}")


@router.post("/next-meal/mock", response_model=RecommendResponse)
async def get_next_meal_recommendation_mock(
    request: RecommendRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    调试接口：返回固定的 Mock 推荐数据，用于展示和测试
    """
    # 验证用户存在
    try:
        user_id = UUID(request.userId)
    except ValueError:
        raise HTTPException(status_code=400, detail="无效的用户 ID")

    user = await db.scalar(select(User).where(User.id == user_id))
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    # 根据选择的食材返回不同的 mock 数据
    selected_foods = request.selectedFoods or []

    if "牛肉" in selected_foods or "鸡肉" in selected_foods:
        return RecommendResponse(
            analysis="您选择的食材蛋白质含量丰富，适合增肌目标。建议搭配适量蔬菜补充膳食纤维。",
            next_meal_target=NextMealTarget(
                target_calories="450-550 kcal",
                focus_macros="蛋白质约30g、碳水约50g、脂肪约15g"
            ),
            recommendations=[
                RecommendedDish(
                    dish_name="黑椒牛柳炒西兰花",
                    reason="选用瘦牛肉提供优质蛋白，西兰花补充维生素C和膳食纤维，黑椒调味提升口感同时促进代谢",
                    estimated_calories="约 480 kcal",
                    cooking_steps="1.牛肉切条加生抽、料酒腌制10分钟；2.西兰花焯水备用；3.热锅少油快炒牛肉至变色；4.加入西兰花、黑椒粉翻炒均匀即可"
                ),
                RecommendedDish(
                    dish_name="番茄牛腩煲配糙米饭",
                    reason="牛腩富含铁质和蛋白质，番茄提供番茄红素，糙米作为低GI主食有助于稳定血糖",
                    estimated_calories="约 520 kcal",
                    cooking_steps="1.牛腩切块焯水去血沫；2.番茄切块爆炒出汁；3.加入牛腩、适量水炖煮40分钟；4.糙米饭另煮，配菜一起享用"
                ),
                RecommendedDish(
                    dish_name="凉拌牛肉丝配时蔬",
                    reason="低温烹饪保留牛肉营养，搭配多种蔬菜增加膳食纤维摄入，清爽少油",
                    estimated_calories="约 350 kcal",
                    cooking_steps="1.牛肉切丝焯水至刚熟捞出；2.黄瓜、胡萝卜切丝；3.调汁：生抽、醋、少许香油；4.所有食材拌匀即可"
                )
            ]
        )
    elif "鱼虾" in selected_foods or "鸡蛋" in selected_foods:
        return RecommendResponse(
            analysis="鱼虾和鸡蛋是优质蛋白来源，脂肪含量较低，非常适合减脂期的饮食安排。",
            next_meal_target=NextMealTarget(
                target_calories="400-500 kcal",
                focus_macros="蛋白质约35g、碳水约40g、脂肪约12g"
            ),
            recommendations=[
                RecommendedDish(
                    dish_name="清蒸鲈鱼配蒸蛋羹",
                    reason="鲈鱼肉质细嫩富含优质蛋白，蒸蛋羹口感顺滑，清蒸方式保留营养且低油低盐",
                    estimated_calories="约 380 kcal",
                    cooking_steps="1.鲈鱼处理干净，加葱姜料酒腌制；2.水开后蒸8分钟；3.鸡蛋打散加温水蒸10分钟；4.淋上少许蒸鱼豉油即可"
                ),
                RecommendedDish(
                    dish_name="虾仁滑蛋配杂粮饭",
                    reason="虾仁高蛋白低脂肪，鸡蛋增加饱腹感，杂粮饭提供复合碳水",
                    estimated_calories="约 450 kcal",
                    cooking_steps="1.虾仁去线焯水备用；2.鸡蛋打散加少许盐；3.热锅少油，倒入蛋液半凝固时加入虾仁；4.轻轻推炒至熟，配杂粮饭"
                ),
                RecommendedDish(
                    dish_name="番茄鸡蛋面",
                    reason="经典家常搭配，番茄提供维生素，鸡蛋补充蛋白，面条作为主食提供能量",
                    estimated_calories="约 420 kcal",
                    cooking_steps="1.番茄切块，鸡蛋打散炒熟盛出；2.锅中炒香番茄出汁；3.加水煮开，放入面条；4.加入鸡蛋、盐调味，撒上葱花"
                )
            ]
        )
    else:
        # 默认推荐
        return RecommendResponse(
            analysis="根据您的饮食目标，建议均衡摄入蛋白质、碳水化和脂肪。",
            next_meal_target=NextMealTarget(
                target_calories="500-600 kcal",
                focus_macros="蛋白质约25g、碳水约60g、脂肪约18g"
            ),
            recommendations=[
                RecommendedDish(
                    dish_name="鸡胸肉蔬菜沙拉",
                    reason="高蛋白低脂肪的鸡胸肉搭配多种蔬菜，营养均衡且热量可控",
                    estimated_calories="约 350 kcal",
                    cooking_steps="1.鸡胸肉煮熟撕成丝；2.生菜、番茄、黄瓜洗净切块；3.调汁：橄榄油、柠檬汁、黑胡椒；4.所有食材拌匀即可"
                ),
                RecommendedDish(
                    dish_name="糙米饭配清炒时蔬",
                    reason="糙米提供复合碳水，时蔬补充维生素和膳食纤维，适合作为主食搭配",
                    estimated_calories="约 320 kcal",
                    cooking_steps="1.糙米提前浸泡，煮熟备用；2.西兰花、胡萝卜、彩椒切块；3.热锅少油快炒蔬菜；4.加盐调味，配糙米饭享用"
                ),
                RecommendedDish(
                    dish_name="豆腐菌菇汤配全麦面包",
                    reason="豆腐提供植物蛋白，菌菇增加鲜味和营养，全麦面包作为主食",
                    estimated_calories="约 380 kcal",
                    cooking_steps="1.豆腐切块，菌菇洗净；2.锅中少油炒香菌菇；3.加水煮开，放入豆腐煮5分钟；4.加盐、胡椒粉调味，配全麦面包"
                )
            ]
        )
