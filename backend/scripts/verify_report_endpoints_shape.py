from __future__ import annotations

import asyncio
from uuid import UUID

from app.api.v1.endpoints.report import _build_report
from app.core.database import AsyncSessionLocal


async def main() -> None:
    user_id = UUID("99c8c807-f8b9-4d2f-b8ac-07dd1e0d9c16")
    async with AsyncSessionLocal() as session:
        daily = await _build_report(user_id=user_id, days=1, db=session)
        weekly = await _build_report(user_id=user_id, days=7, db=session)
        monthly = await _build_report(user_id=user_id, days=30, db=session)

        print(f"daily_points={len(daily.points)}")
        print(f"daily_labels={[p.date for p in daily.points]}")
        print(f"weekly_points={len(weekly.points)}")
        print(f"monthly_points={len(monthly.points)}")
        print(f"weekly_none_count={sum(1 for p in weekly.points if p.calories_kcal is None)}")
        print(f"monthly_none_count={sum(1 for p in monthly.points if p.calories_kcal is None)}")


if __name__ == "__main__":
    asyncio.run(main())
