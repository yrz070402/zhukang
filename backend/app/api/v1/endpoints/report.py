"""用户营养报表接口（日/周/月）。"""
from __future__ import annotations

from datetime import date, datetime, timedelta
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.database import get_db
from app.models.models import MealType, NutritionIntake, UserGoal
from app.models.schemas import ReportGoalLine, ReportSeriesPoint, UserReportResponse

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
