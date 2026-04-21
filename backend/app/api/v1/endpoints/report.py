"""用户营养报表接口（日/周/月）。"""
from __future__ import annotations

import calendar
from collections import defaultdict
from datetime import date, datetime, timedelta
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.database import get_db
from app.models.models import MealType, NutritionIntake, UserGoal
from app.models.schemas import (
    DietMapDay,
    DietMapIntakeItem,
    DietMapResponse,
    ReportGoalLine,
    ReportSeriesPoint,
    UserReportResponse,
)

router = APIRouter()
settings = get_settings()


def _resolve_day_window(now_local: datetime, days: int) -> tuple[datetime, datetime, date]:
    """基于 04:00 锚点计算 [start, end) 时间窗和当天业务日。"""
    anchor_today = now_local.replace(hour=4, minute=0, second=0, microsecond=0)
    if now_local < anchor_today:
        current_business_day = (anchor_today - timedelta(days=1)).date()
        window_end = anchor_today
    else:
        current_business_day = anchor_today.date()
        window_end = anchor_today + timedelta(days=1)

    window_start = window_end - timedelta(days=days)
    return window_start, window_end, current_business_day


async def _build_report(
    *,
    user_id: UUID,
    days: int,
    db: AsyncSession,
) -> UserReportResponse:
    user_goal = await db.scalar(
        select(UserGoal).where(
            UserGoal.user_id == user_id,
            UserGoal.deleted_at.is_(None),
            UserGoal.is_active.is_(True),
        )
    )
    if not user_goal:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户目标不存在")

    tz = ZoneInfo(settings.timezone)
    now_local = datetime.now(tz)
    start_at, end_at, current_business_day = _resolve_day_window(now_local, days)

    # 日报以四餐为点位：按真实摄入时间排序（加餐可出现在任意位置）。
    if days == 1:
        rows = (
            await db.execute(
                select(
                    NutritionIntake.meal_type.label("meal_type"),
                    func.min(NutritionIntake.intake_time).label("first_time"),
                    func.sum(NutritionIntake.energy_kcal).label("calories_kcal"),
                    func.sum(NutritionIntake.protein_g).label("protein_g"),
                    func.sum(NutritionIntake.fat_g).label("fat_g"),
                    func.sum(NutritionIntake.carb_g).label("carb_g"),
                )
                .where(
                    NutritionIntake.user_id == user_id,
                    NutritionIntake.deleted_at.is_(None),
                    NutritionIntake.intake_time >= start_at,
                    NutritionIntake.intake_time < end_at,
                )
                .group_by(NutritionIntake.meal_type)
            )
        ).all()

        meal_label_map = {
            MealType.BREAKFAST: "早餐",
            MealType.LUNCH: "午餐",
            MealType.DINNER: "晚餐",
            MealType.SNACK: "加餐",
        }
        meal_rows = sorted(rows, key=lambda x: x.first_time)
        points: list[ReportSeriesPoint] = [
            ReportSeriesPoint(
                date=meal_label_map[row.meal_type],
                calories_kcal=float(row.calories_kcal or 0),
                protein_g=float(row.protein_g or 0),
                fat_g=float(row.fat_g or 0),
                carb_g=float(row.carb_g or 0),
            )
            for row in meal_rows
        ]

        used_types = {row.meal_type for row in meal_rows}
        for meal_type in [MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK]:
            if meal_type not in used_types:
                points.append(
                    ReportSeriesPoint(
                        date=meal_label_map[meal_type],
                        calories_kcal=None,
                        protein_g=None,
                        fat_g=None,
                        carb_g=None,
                    )
                )

        points = points[:4]

        return UserReportResponse(
            user_id=user_id,
            start_at=start_at.isoformat(),
            end_at=end_at.isoformat(),
            points=points,
            goal_line=ReportGoalLine(
                calories_kcal=float((user_goal.target_daily_calories_kcal or 0) / 4),
                protein_g=float((user_goal.target_protein_g or 0) / 4),
                fat_g=float((user_goal.target_fat_g or 0) / 4),
                carb_g=float((user_goal.target_carb_g or 0) / 4),
            ),
        )

    date_expr = func.date(func.timezone(settings.timezone, NutritionIntake.intake_time))
    rows = (
        await db.execute(
            select(
                date_expr.label("day"),
                func.sum(NutritionIntake.energy_kcal).label("calories_kcal"),
                func.sum(NutritionIntake.protein_g).label("protein_g"),
                func.sum(NutritionIntake.fat_g).label("fat_g"),
                func.sum(NutritionIntake.carb_g).label("carb_g"),
            )
            .where(
                NutritionIntake.user_id == user_id,
                NutritionIntake.deleted_at.is_(None),
                NutritionIntake.intake_time >= start_at,
                NutritionIntake.intake_time < end_at,
            )
            .group_by(date_expr)
        )
    ).all()

    aggregates: dict[date, tuple[float, float, float, float]] = {
        row.day: (
            float(row.calories_kcal or 0),
            float(row.protein_g or 0),
            float(row.fat_g or 0),
            float(row.carb_g or 0),
        )
        for row in rows
    }

    points: list[ReportSeriesPoint] = []
    for offset in range(days):
        day = current_business_day - timedelta(days=offset)
        point = aggregates.get(day)
        if point is None:
            points.append(
                ReportSeriesPoint(
                    date=day.isoformat(),
                    calories_kcal=None,
                    protein_g=None,
                    fat_g=None,
                    carb_g=None,
                )
            )
        else:
            calories_kcal, protein_g, fat_g, carb_g = point
            points.append(
                ReportSeriesPoint(
                    date=day.isoformat(),
                    calories_kcal=calories_kcal,
                    protein_g=protein_g,
                    fat_g=fat_g,
                    carb_g=carb_g,
                )
            )

    return UserReportResponse(
        user_id=user_id,
        start_at=start_at.isoformat(),
        end_at=end_at.isoformat(),
        points=points,
        goal_line=ReportGoalLine(
            calories_kcal=float(user_goal.target_daily_calories_kcal or 0),
            protein_g=float(user_goal.target_protein_g or 0),
            fat_g=float(user_goal.target_fat_g or 0),
            carb_g=float(user_goal.target_carb_g or 0),
        ),
    )


@router.get("/daily", response_model=UserReportResponse)
async def get_daily_report(
    user_id: UUID = Query(...),
    db: AsyncSession = Depends(get_db),
) -> UserReportResponse:
    return await _build_report(user_id=user_id, days=1, db=db)


@router.get("/weekly", response_model=UserReportResponse)
async def get_weekly_report(
    user_id: UUID = Query(...),
    db: AsyncSession = Depends(get_db),
) -> UserReportResponse:
    return await _build_report(user_id=user_id, days=7, db=db)


@router.get("/monthly", response_model=UserReportResponse)
async def get_monthly_report(
    user_id: UUID = Query(...),
    db: AsyncSession = Depends(get_db),
) -> UserReportResponse:
    return await _build_report(user_id=user_id, days=30, db=db)


def _resolve_bitelog_window(
    now_local: datetime, period: str, offset: int
) -> tuple[datetime, datetime, date, date]:
    """
    Bitelog 网格按业务日划分，0 表示当前周/月，-1 表示上一期，以此类推。

    返回 (window_start, window_end, first_business_day, last_business_day)。
    """
    anchor_today = now_local.replace(hour=4, minute=0, second=0, microsecond=0)
    if now_local < anchor_today:
        current_business_day = (anchor_today - timedelta(days=1)).date()
    else:
        current_business_day = anchor_today.date()

    tz = now_local.tzinfo

    if period == "weekly":
        # ISO 周：周一为首日。
        week_start = current_business_day - timedelta(days=current_business_day.weekday())
        first_day = week_start + timedelta(weeks=offset)
        last_day = first_day + timedelta(days=6)
    else:
        year = current_business_day.year
        month = current_business_day.month + offset
        # 处理月份溢出。
        year += (month - 1) // 12
        month = (month - 1) % 12 + 1
        first_day = date(year, month, 1)
        last_day_index = calendar.monthrange(year, month)[1]
        last_day = date(year, month, last_day_index)

    window_start = datetime.combine(first_day, datetime.min.time(), tzinfo=tz).replace(hour=4)
    window_end = datetime.combine(last_day + timedelta(days=1), datetime.min.time(), tzinfo=tz).replace(hour=4)
    return window_start, window_end, first_day, last_day


@router.get("/diet-map", response_model=DietMapResponse)
async def get_diet_map(
    user_id: UUID = Query(...),
    period: str = Query("weekly", pattern="^(weekly|monthly)$"),
    offset: int = Query(0),
    db: AsyncSession = Depends(get_db),
) -> DietMapResponse:
    """
    Bitelog 网格数据：按天聚合餐次记录，附带缩略图 URL 与营养明细。
    """
    tz = ZoneInfo(settings.timezone)
    now_local = datetime.now(tz)
    window_start, window_end, first_day, last_day = _resolve_bitelog_window(now_local, period, offset)

    date_expr = func.date(func.timezone(settings.timezone, NutritionIntake.intake_time))
    rows = (
        await db.execute(
            select(
                NutritionIntake.id,
                NutritionIntake.intake_time,
                NutritionIntake.meal_type,
                NutritionIntake.food_name,
                NutritionIntake.image_url,
                NutritionIntake.energy_kcal,
                NutritionIntake.protein_g,
                NutritionIntake.fat_g,
                NutritionIntake.carb_g,
                date_expr.label("business_day"),
            )
            .where(
                NutritionIntake.user_id == user_id,
                NutritionIntake.deleted_at.is_(None),
                NutritionIntake.intake_time >= window_start,
                NutritionIntake.intake_time < window_end,
            )
            .order_by(NutritionIntake.intake_time.asc())
        )
    ).all()

    grouped: dict[date, list[DietMapIntakeItem]] = defaultdict(list)
    for row in rows:
        grouped[row.business_day].append(
            DietMapIntakeItem(
                id=row.id,
                intake_time=row.intake_time.isoformat(),
                meal_type=row.meal_type.value if hasattr(row.meal_type, "value") else str(row.meal_type),
                food_name=row.food_name,
                image_url=row.image_url,
                calories_kcal=float(row.energy_kcal or 0),
                protein_g=float(row.protein_g or 0),
                fat_g=float(row.fat_g or 0),
                carb_g=float(row.carb_g or 0),
            )
        )

    days: list[DietMapDay] = []
    total_days = (last_day - first_day).days + 1
    for offset_day in range(total_days):
        current = first_day + timedelta(days=offset_day)
        days.append(
            DietMapDay(
                business_day=current.isoformat(),
                weekday=current.isoweekday(),
                intakes=grouped.get(current, []),
            )
        )

    return DietMapResponse(
        user_id=user_id,
        period=period,
        offset=offset,
        start_at=window_start.isoformat(),
        end_at=window_end.isoformat(),
        days=days,
    )
