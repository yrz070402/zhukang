from __future__ import annotations

import asyncio
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import func, select

from app.core.config import get_settings
from app.core.database import AsyncSessionLocal
from app.models.models import NutritionIntake, User, UserGoal


async def main() -> None:
    settings = get_settings()
    tz = ZoneInfo(settings.timezone)
    today = datetime.now(tz).date()
    start_day = today - timedelta(days=29)

    async with AsyncSessionLocal() as session:
        user = (await session.execute(select(User).where(User.account == "test_report"))).scalar_one()
        goal = (await session.execute(select(UserGoal).where(UserGoal.user_id == user.id))).scalar_one()

        rows = (
            await session.execute(
                select(
                    func.date(NutritionIntake.intake_time).label("d"),
                    func.sum(NutritionIntake.energy_kcal).label("kcal"),
                    func.sum(NutritionIntake.protein_g).label("protein"),
                    func.sum(NutritionIntake.fat_g).label("fat"),
                    func.sum(NutritionIntake.carb_g).label("carb"),
                    func.count().label("n"),
                )
                .where(
                    NutritionIntake.user_id == user.id,
                    NutritionIntake.intake_time >= datetime.combine(start_day, datetime.min.time(), tzinfo=tz),
                    NutritionIntake.intake_time <= datetime.combine(today, datetime.max.time(), tzinfo=tz),
                )
                .group_by(func.date(NutritionIntake.intake_time))
                .order_by(func.date(NutritionIntake.intake_time))
            )
        ).all()

        violations = []
        for d, kcal, protein, fat, carb, count_rows in rows:
            kcal_ratio = float(kcal / goal.target_daily_calories_kcal)
            protein_ratio = float(protein / goal.target_protein_g)
            fat_ratio = float(fat / goal.target_fat_g)
            carb_ratio = float(carb / goal.target_carb_g)
            if not (
                0.5 <= kcal_ratio <= 1.5
                and 0.5 <= protein_ratio <= 1.5
                and 0.5 <= fat_ratio <= 1.5
                and 0.5 <= carb_ratio <= 1.5
                and count_rows == 4
            ):
                violations.append((str(d), kcal_ratio, protein_ratio, fat_ratio, carb_ratio, int(count_rows)))

        print(f"days={len(rows)}")
        print(f"violations={len(violations)}")
        if violations:
            print(f"first_violation={violations[0]}")


if __name__ == "__main__":
    asyncio.run(main())
