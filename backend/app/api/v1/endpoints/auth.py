"""用户注册与初始资料写入接口。"""
from __future__ import annotations

import base64
import hmac
import hashlib
import re
import secrets
from datetime import datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import delete, desc, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.database import get_db
from app.models.models import GoalType, NutritionIntake, SexType, Tag, User, UserGoal, UserProfile, UserTag
from app.models.schemas import (
    LoginRequest,
    LoginResponse,
    NicknameUpdateRequest,
    NicknameUpdateResponse,
    PopularTagItem,
    PopularTagsResponse,
    ProfileSetupRequest,
    ProfileSetupResponse,
    RegisterRequest,
    RegisterResponse,
    UserDailyIntakeSummaryResponse,
    UserDailyGoalTargetsResponse,
)

router = APIRouter()
settings = get_settings()

GOAL_INDEX_MAP: dict[int, GoalType] = {
    0: GoalType.MUSCLE_GAIN,
    1: GoalType.FAT_LOSS,
    2: GoalType.MAINTAIN,
}

ACTIVITY_LEVEL_ADJUSTMENT: dict[int, Decimal] = {
    0: Decimal("0"),
    1: Decimal("100"),
    2: Decimal("175"),
    3: Decimal("250"),
}

GOAL_CALORIE_FACTOR: dict[GoalType, Decimal] = {
    GoalType.MUSCLE_GAIN: Decimal("1.05"),
    GoalType.FAT_LOSS: Decimal("0.80"),
}


def _hash_password(raw_password: str) -> str:
    iterations = 120_000
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", raw_password.encode("utf-8"), salt, iterations)
    return (
        f"pbkdf2_sha256${iterations}$"
        f"{base64.urlsafe_b64encode(salt).decode('ascii')}$"
        f"{base64.urlsafe_b64encode(digest).decode('ascii')}"
    )


def _verify_password(raw_password: str, stored_hash: str) -> bool:
    try:
        algorithm, iter_text, salt_text, digest_text = stored_hash.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        iterations = int(iter_text)
        salt = base64.urlsafe_b64decode(salt_text.encode("ascii"))
        expected_digest = base64.urlsafe_b64decode(digest_text.encode("ascii"))
    except Exception:
        return False

    current_digest = hashlib.pbkdf2_hmac("sha256", raw_password.encode("utf-8"), salt, iterations)
    return hmac.compare_digest(current_digest, expected_digest)


def _build_tag_code(display_name: str) -> str:
    base = re.sub(r"[^a-z0-9]+", "_", display_name.lower()).strip("_")
    if base:
        return f"user_{base}"[:64]
    digest = hashlib.sha1(display_name.encode("utf-8")).hexdigest()[:12]
    return f"user_tag_{digest}"


async def _resolve_or_create_tag(db: AsyncSession, display_name: str) -> Tag:
    existing = await db.scalar(
        select(Tag).where(Tag.display_name == display_name)
    )
    if existing:
        if not existing.is_active:
            existing.is_active = True
        if existing.deleted_at is not None:
            existing.deleted_at = None
        return existing

    code_seed = _build_tag_code(display_name)
    code = code_seed
    index = 1

    while await db.scalar(select(Tag).where(Tag.code == code)):
        suffix = f"_{index}"
        code = f"{code_seed[:64 - len(suffix)]}{suffix}"
        index += 1

    tag = Tag(
        code=code,
        display_name=display_name,
        description=f"用户饮食偏好：{display_name}",
        is_active=True,
    )
    db.add(tag)
    await db.flush()
    return tag


def _calculate_target_daily_calories(
    *,
    age: int,
    sex: SexType,
    height_cm: float,
    weight_kg: float,
    activity_level: int,
    goal_type: GoalType,
) -> Decimal:
    age_decimal = Decimal(age)
    height_decimal = Decimal(str(height_cm))
    weight_decimal = Decimal(str(weight_kg))

    # BMR 公式按需求区分男/女。
    base_metabolism = (
        weight_decimal * Decimal("9.99")
        + height_decimal * Decimal("6.25")
        - age_decimal * Decimal("4.92")
    )
    if sex == SexType.MALE:
        base_metabolism += Decimal("5")
    else:
        base_metabolism -= Decimal("161")

    activity_adjustment = ACTIVITY_LEVEL_ADJUSTMENT[activity_level]
    if sex == SexType.FEMALE:
        activity_adjustment = max(activity_adjustment - Decimal("50"), Decimal("0"))

    total_consumption = (base_metabolism / Decimal("0.7")) + activity_adjustment
    factor = GOAL_CALORIE_FACTOR.get(goal_type, Decimal("1"))

    return (total_consumption * factor).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _calculate_target_fat_g(*, sex: SexType, goal_type: GoalType, weight_kg: float) -> Decimal:
    weight_decimal = Decimal(str(weight_kg))

    if goal_type == GoalType.MUSCLE_GAIN:
        fat = Decimal("80") if sex == SexType.MALE else Decimal("70")
    elif goal_type == GoalType.FAT_LOSS:
        if sex == SexType.MALE:
            fat = Decimal("60") if weight_decimal <= Decimal("120") else Decimal("70")
        else:
            fat = Decimal("50")
    else:
        fat = Decimal("70") if sex == SexType.MALE else Decimal("60")

    return fat.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _calculate_target_carb_g(*, sex: SexType, goal_type: GoalType, weight_kg: float) -> Decimal:
    weight_decimal = Decimal(str(weight_kg))

    if goal_type == GoalType.MUSCLE_GAIN:
        factor = Decimal("3.6") if sex == SexType.MALE else Decimal("3.2")
    elif goal_type == GoalType.FAT_LOSS:
        factor = Decimal("2.2") if sex == SexType.MALE else Decimal("1.9")
    else:
        factor = Decimal("3.0") if sex == SexType.MALE else Decimal("2.6")

    return (weight_decimal * factor).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _calculate_target_protein_g(*, sex: SexType, goal_type: GoalType, weight_kg: float) -> Decimal:
    weight_decimal = Decimal(str(weight_kg))

    if goal_type == GoalType.MUSCLE_GAIN:
        factor = Decimal("1.7") if sex == SexType.MALE else Decimal("1.5")
    elif goal_type == GoalType.FAT_LOSS:
        factor = Decimal("1.2") if sex == SexType.MALE else Decimal("1.1")
    else:
        factor = Decimal("1.4") if sex == SexType.MALE else Decimal("1.3")

    return (weight_decimal * factor).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


@router.post("/register", response_model=RegisterResponse, status_code=status.HTTP_201_CREATED)
async def register(payload: RegisterRequest, db: AsyncSession = Depends(get_db)) -> RegisterResponse:
    existing = await db.scalar(select(User).where(User.account == payload.account))
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="账号已存在")

    user = User(
        account=payload.account,
        nickname=payload.account,
        password_hash=_hash_password(payload.password),
        is_active=True,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    return RegisterResponse(
        user_id=user.id,
        account=user.account,
        nickname=user.nickname,
        message="注册成功，请继续完善个人信息",
    )


@router.post("/login", response_model=LoginResponse)
async def login(payload: LoginRequest, db: AsyncSession = Depends(get_db)) -> LoginResponse:
    user = await db.scalar(select(User).where(User.account == payload.account, User.deleted_at.is_(None)))
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户尚未注册")

    if not _verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="密码错误")

    return LoginResponse(
        user_id=user.id,
        account=user.account,
        nickname=user.nickname,
        message="登录成功",
    )


@router.patch("/user/nickname", response_model=NicknameUpdateResponse)
async def update_nickname(payload: NicknameUpdateRequest, db: AsyncSession = Depends(get_db)) -> NicknameUpdateResponse:
    user = await db.scalar(select(User).where(User.id == payload.user_id, User.deleted_at.is_(None)))
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")

    nickname = payload.nickname.strip()
    if not nickname:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="昵称不能为空")

    user.nickname = nickname
    await db.commit()

    return NicknameUpdateResponse(
        user_id=user.id,
        nickname=user.nickname,
        message="昵称更新成功",
    )


@router.post("/register/profile", response_model=ProfileSetupResponse)
async def setup_profile(payload: ProfileSetupRequest, db: AsyncSession = Depends(get_db)) -> ProfileSetupResponse:
    user = await db.scalar(select(User).where(User.id == payload.user_id, User.deleted_at.is_(None)))
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")

    goal_type = GOAL_INDEX_MAP.get(payload.goal_index)
    if goal_type is None:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="goal_index 仅支持 0/1/2")

    if payload.sex not in (SexType.MALE, SexType.FEMALE):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="sex 仅支持 male/female 用于卡路里计算")

    target_daily_calories = _calculate_target_daily_calories(
        age=payload.age,
        sex=payload.sex,
        height_cm=payload.height_cm,
        weight_kg=payload.weight_kg,
        activity_level=payload.activity_level,
        goal_type=goal_type,
    )
    target_fat_g = _calculate_target_fat_g(
        sex=payload.sex,
        goal_type=goal_type,
        weight_kg=payload.weight_kg,
    )
    target_carb_g = _calculate_target_carb_g(
        sex=payload.sex,
        goal_type=goal_type,
        weight_kg=payload.weight_kg,
    )
    target_protein_g = _calculate_target_protein_g(
        sex=payload.sex,
        goal_type=goal_type,
        weight_kg=payload.weight_kg,
    )

    profile = await db.scalar(select(UserProfile).where(UserProfile.user_id == user.id))
    if profile is None:
        profile = UserProfile(
            user_id=user.id,
            age=payload.age,
            sex=payload.sex,
            height_cm=payload.height_cm,
            weight_kg=payload.weight_kg,
        )
        db.add(profile)
    else:
        profile.age = payload.age
        profile.sex = payload.sex
        profile.height_cm = payload.height_cm
        profile.weight_kg = payload.weight_kg

    user_goal = await db.scalar(select(UserGoal).where(UserGoal.user_id == user.id))
    if user_goal is None:
        user_goal = UserGoal(
            user_id=user.id,
            goal_type=goal_type,
            activity_level=payload.activity_level,
            target_daily_calories_kcal=target_daily_calories,
            target_carb_g=target_carb_g,
            target_protein_g=target_protein_g,
            target_fat_g=target_fat_g,
            is_active=True,
        )
        db.add(user_goal)
    else:
        user_goal.goal_type = goal_type
        user_goal.activity_level = payload.activity_level
        user_goal.target_daily_calories_kcal = target_daily_calories
        user_goal.target_carb_g = target_carb_g
        user_goal.target_protein_g = target_protein_g
        user_goal.target_fat_g = target_fat_g
        user_goal.is_active = True
        user_goal.deleted_at = None

    await db.execute(delete(UserTag).where(UserTag.user_id == user.id))

    tag_ids: list[int] = []
    for preference in payload.dietary_preferences:
        tag = await _resolve_or_create_tag(db, preference)
        db.add(UserTag(user_id=user.id, tag_id=tag.id))
        tag_ids.append(tag.id)

    await db.commit()

    return ProfileSetupResponse(
        user_id=user.id,
        goal_type=goal_type.value,
        tag_ids=tag_ids,
        message="用户资料、目标与饮食偏好已保存",
    )


@router.get("/tags/popular", response_model=PopularTagsResponse)
async def get_popular_tags(
    limit: int = Query(default=6, ge=1, le=20),
    db: AsyncSession = Depends(get_db),
) -> PopularTagsResponse:
    result = await db.execute(
        select(
            Tag.display_name,
            func.count(UserTag.user_id).label("user_num"),
            Tag.created_at,
        )
        .outerjoin(
            UserTag,
            (UserTag.tag_id == Tag.id) & (UserTag.deleted_at.is_(None)),
        )
        .where(Tag.deleted_at.is_(None), Tag.is_active.is_(True))
        .group_by(Tag.id, Tag.display_name, Tag.created_at)
        .order_by(desc("user_num"), Tag.created_at.desc())
        .limit(limit)
    )
    rows = result.all()
    return PopularTagsResponse(
        items=[PopularTagItem(display_name=row.display_name, user_num=row.user_num) for row in rows]
    )


@router.get("/user/goal-targets", response_model=UserDailyGoalTargetsResponse)
async def get_user_goal_targets(
    user_id: UUID = Query(...),
    db: AsyncSession = Depends(get_db),
) -> UserDailyGoalTargetsResponse:
    user_goal = await db.scalar(
        select(UserGoal).where(
            UserGoal.user_id == user_id,
            UserGoal.deleted_at.is_(None),
            UserGoal.is_active.is_(True),
        )
    )
    if not user_goal:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户目标不存在")

    return UserDailyGoalTargetsResponse(
        user_id=user_id,
        target_daily_calories_kcal=float(user_goal.target_daily_calories_kcal or 0),
        target_protein_g=float(user_goal.target_protein_g or 0),
        target_fat_g=float(user_goal.target_fat_g or 0),
        target_carb_g=float(user_goal.target_carb_g or 0),
    )


@router.get("/user/intake-summary", response_model=UserDailyIntakeSummaryResponse)
async def get_user_daily_intake_summary(
    user_id: UUID = Query(...),
    db: AsyncSession = Depends(get_db),
) -> UserDailyIntakeSummaryResponse:
    now_local = datetime.now(ZoneInfo(settings.timezone))
    anchor_today = now_local.replace(hour=4, minute=0, second=0, microsecond=0)

    if now_local < anchor_today:
        start_at = anchor_today - timedelta(days=1)
        end_at = anchor_today
    else:
        start_at = anchor_today
        end_at = anchor_today + timedelta(days=1)

    result = await db.execute(
        select(
            func.coalesce(func.sum(NutritionIntake.energy_kcal), 0).label("total_calories_kcal"),
            func.coalesce(func.sum(NutritionIntake.protein_g), 0).label("total_protein_g"),
            func.coalesce(func.sum(NutritionIntake.fat_g), 0).label("total_fat_g"),
            func.coalesce(func.sum(NutritionIntake.carb_g), 0).label("total_carb_g"),
        ).where(
            NutritionIntake.user_id == user_id,
            NutritionIntake.deleted_at.is_(None),
            NutritionIntake.intake_time >= start_at,
            NutritionIntake.intake_time < end_at,
        )
    )
    row = result.one()

    return UserDailyIntakeSummaryResponse(
        user_id=user_id,
        start_at=start_at.isoformat(),
        end_at=end_at.isoformat(),
        total_calories_kcal=float(row.total_calories_kcal or 0),
        total_protein_g=float(row.total_protein_g or 0),
        total_fat_g=float(row.total_fat_g or 0),
        total_carb_g=float(row.total_carb_g or 0),
    )
