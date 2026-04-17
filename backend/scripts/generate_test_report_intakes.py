from __future__ import annotations

import argparse
import asyncio
import random
import uuid
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Dict
from zoneinfo import ZoneInfo

from sqlalchemy import delete, select

from app.core.config import get_settings
from app.core.database import AsyncSessionLocal
from app.models.models import MealType, NutritionIntake, SourceType, User, UserGoal


Q = Decimal("0.01")


@dataclass(frozen=True)
class DailyTargets:
    calories: Decimal
    protein: Decimal
    fat: Decimal
    carb: Decimal


MEAL_SLOTS = [
    (MealType.BREAKFAST, time(8, 0), ["oatmeal and eggs", "whole wheat toast with milk", "yogurt fruit bowl"]),
    (MealType.LUNCH, time(12, 30), ["grilled chicken rice", "beef noodles", "tofu vegetable bowl"]),
    (MealType.DINNER, time(18, 30), ["salmon quinoa plate", "lean pork stir-fry", "shrimp pasta"]),
    (MealType.SNACK, time(21, 0), ["banana and almonds", "protein yogurt", "apple and peanut butter"]),
]

BASE_WEIGHTS: Dict[MealType, Decimal] = {
    MealType.BREAKFAST: Decimal("0.25"),
    MealType.LUNCH: Decimal("0.35"),
    MealType.DINNER: Decimal("0.30"),
    MealType.SNACK: Decimal("0.10"),
}


def d2(value: Decimal) -> Decimal:
    return value.quantize(Q, rounding=ROUND_HALF_UP)


def random_daily_totals(targets: DailyTargets) -> DailyTargets:
    ratios = {
        "calories": Decimal(str(random.uniform(0.5, 1.5))),
        "protein": Decimal(str(random.uniform(0.5, 1.5))),
        "fat": Decimal(str(random.uniform(0.5, 1.5))),
        "carb": Decimal(str(random.uniform(0.5, 1.5))),
    }
    return DailyTargets(
        calories=d2(targets.calories * ratios["calories"]),
        protein=d2(targets.protein * ratios["protein"]),
        fat=d2(targets.fat * ratios["fat"]),
        carb=d2(targets.carb * ratios["carb"]),
    )


def random_meal_weights() -> Dict[MealType, Decimal]:
    raw: Dict[MealType, Decimal] = {}
    total = Decimal("0")
    for meal_type, base in BASE_WEIGHTS.items():
        factor = Decimal(str(random.uniform(0.8, 1.2)))
        value = base * factor
        raw[meal_type] = value
        total += value

    normalized: Dict[MealType, Decimal] = {}
    cumulative = Decimal("0")
    ordered_meals = [meal for meal, _, _ in MEAL_SLOTS]
    for meal_type in ordered_meals[:-1]:
        w = d2(raw[meal_type] / total)
        normalized[meal_type] = w
        cumulative += w
    normalized[ordered_meals[-1]] = Decimal("1") - cumulative
    return normalized


def split_daily_to_meals(daily_total: Decimal, weights: Dict[MealType, Decimal]) -> Dict[MealType, Decimal]:
    allocations: Dict[MealType, Decimal] = {}
    remaining = daily_total
    ordered_meals = [meal for meal, _, _ in MEAL_SLOTS]
    for meal_type in ordered_meals[:-1]:
        value = d2(daily_total * weights[meal_type])
        allocations[meal_type] = value
        remaining -= value
    allocations[ordered_meals[-1]] = d2(max(remaining, Decimal("0")))
    return allocations


def ensure_ratio(total: Decimal, target: Decimal, metric_name: str) -> None:
    ratio = total / target
    if ratio < Decimal("0.5") or ratio > Decimal("1.5"):
        raise ValueError(f"{metric_name} ratio out of range: {ratio}")


async def generate(account: str, days: int, seed: int | None) -> None:
    if seed is not None:
        random.seed(seed)

    settings = get_settings()
    tz = ZoneInfo(settings.timezone)

    today = datetime.now(tz).date()
    start_day = today - timedelta(days=days - 1)

    async with AsyncSessionLocal() as session:
        user = (
            await session.execute(
                select(User).where(User.account == account)
            )
        ).scalar_one_or_none()

        if user is None:
            raise ValueError(f"User not found: {account}")

        goal = (
            await session.execute(
                select(UserGoal).where(UserGoal.user_id == user.id)
            )
        ).scalar_one_or_none()

        if goal is None:
            raise ValueError(f"No user_goals row found for user: {account}")

        if (
            goal.target_daily_calories_kcal is None
            or goal.target_protein_g is None
            or goal.target_fat_g is None
            or goal.target_carb_g is None
        ):
            raise ValueError("user_goals target fields cannot be null")

        targets = DailyTargets(
            calories=d2(goal.target_daily_calories_kcal),
            protein=d2(goal.target_protein_g),
            fat=d2(goal.target_fat_g),
            carb=d2(goal.target_carb_g),
        )

        start_dt = datetime.combine(start_day, time.min, tzinfo=tz)
        end_dt = datetime.combine(today, time.max, tzinfo=tz)

        await session.execute(
            delete(NutritionIntake).where(
                NutritionIntake.user_id == user.id,
                NutritionIntake.intake_time >= start_dt,
                NutritionIntake.intake_time <= end_dt,
            )
        )

        rows_to_insert: list[NutritionIntake] = []

        for day_idx in range(days):
            current_day: date = start_day + timedelta(days=day_idx)
            day_totals = random_daily_totals(targets)
            meal_weights = random_meal_weights()

            day_calories = split_daily_to_meals(day_totals.calories, meal_weights)
            day_protein = split_daily_to_meals(day_totals.protein, meal_weights)
            day_fat = split_daily_to_meals(day_totals.fat, meal_weights)
            day_carb = split_daily_to_meals(day_totals.carb, meal_weights)

            ensure_ratio(sum(day_calories.values()), targets.calories, "calories")
            ensure_ratio(sum(day_protein.values()), targets.protein, "protein")
            ensure_ratio(sum(day_fat.values()), targets.fat, "fat")
            ensure_ratio(sum(day_carb.values()), targets.carb, "carb")

            for meal_type, base_clock, foods in MEAL_SLOTS:
                minute = random.randint(0, 45)
                intake_at = datetime.combine(current_day, base_clock, tzinfo=tz) + timedelta(minutes=minute)
                food_name = random.choice(foods)
                portion = f"{random.randint(1, 2)} serving"

                rows_to_insert.append(
                    NutritionIntake(
                        id=uuid.uuid4(),
                        user_id=user.id,
                        intake_time=intake_at,
                        meal_type=meal_type,
                        food_name=food_name,
                        portion_size=portion,
                        source_type=SourceType.MANUAL,
                        energy_kcal=day_calories[meal_type],
                        protein_g=day_protein[meal_type],
                        fat_g=day_fat[meal_type],
                        carb_g=day_carb[meal_type],
                        calcium_mg=None,
                        iron_mg=None,
                        fiber_g=None,
                        sodium_mg=None,
                        remark="auto generated for test_report",
                    )
                )

        session.add_all(rows_to_insert)
        await session.commit()

        print(
            f"Generated {len(rows_to_insert)} nutrition_intakes rows for '{account}' "
            f"from {start_day.isoformat()} to {today.isoformat()}."
        )
        print(
            f"Targets: kcal={targets.calories}, protein={targets.protein}, "
            f"fat={targets.fat}, carb={targets.carb}"
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate 30-day meal intake rows based on user_goals targets."
    )
    parser.add_argument("--account", default="test_report", help="users.account value")
    parser.add_argument("--days", type=int, default=30, help="Number of days (including today)")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducibility")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.days <= 0:
        raise ValueError("days must be positive")
    asyncio.run(generate(account=args.account, days=args.days, seed=args.seed))


if __name__ == "__main__":
    main()
