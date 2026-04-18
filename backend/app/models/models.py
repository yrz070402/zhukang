""" 领域 ORM 模型定义。"""
from __future__ import annotations

import enum
import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    Enum,
    Float,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Text,
    func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base


class SexType(str, enum.Enum):
    MALE = "male"
    FEMALE = "female"
    OTHER = "other"


class GoalType(str, enum.Enum):
    MUSCLE_GAIN = "muscle_gain"
    FAT_LOSS = "fat_loss"
    MAINTAIN = "maintain"
    CHRONIC_DISEASE_MANAGEMENT = "chronic_disease_management"


class MealType(str, enum.Enum):
    BREAKFAST = "breakfast"
    LUNCH = "lunch"
    DINNER = "dinner"
    SNACK = "snack"


class SourceType(str, enum.Enum):
    AI = "ai"
    MANUAL = "manual"


class TimestampMixin:
    """统一审计字段。"""

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class User(Base, TimestampMixin):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    account: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    nickname: Mapped[str] = mapped_column(String(128), nullable=False)
    avatar_index: Mapped[int] = mapped_column(Integer, nullable=False, server_default="0")
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="true")

    profile: Mapped[UserProfile | None] = relationship(back_populates="user", uselist=False)
    goal: Mapped[UserGoal | None] = relationship(back_populates="user", uselist=False)
    tags: Mapped[list[UserTag]] = relationship(back_populates="user", cascade="all, delete-orphan")
    nutrition_intakes: Mapped[list[NutritionIntake]] = relationship(
        back_populates="user",
        cascade="all, delete-orphan",
    )


class UserProfile(Base, TimestampMixin):
    __tablename__ = "user_profiles"
    __table_args__ = (
        CheckConstraint("height_cm > 0", name="height_cm_positive"),
        CheckConstraint("weight_kg > 0", name="weight_kg_positive"),
        CheckConstraint("age >= 1 AND age <= 120", name="age_valid_range"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
    )
    height_cm: Mapped[Decimal] = mapped_column(Numeric(5, 2), nullable=False)
    weight_kg: Mapped[Decimal] = mapped_column(Numeric(5, 2), nullable=False)
    age: Mapped[int] = mapped_column(Integer, nullable=False)
    sex: Mapped[SexType | None] = mapped_column(Enum(SexType, name="sex_type"), nullable=True)

    user: Mapped[User] = relationship(back_populates="profile")


class UserGoal(Base, TimestampMixin):
    __tablename__ = "user_goals"
    __table_args__ = (
        CheckConstraint("target_weight_kg IS NULL OR target_weight_kg > 0", name="target_weight_positive"),
        CheckConstraint(
            "target_daily_calories_kcal IS NULL OR target_daily_calories_kcal > 0",
            name="target_daily_calories_positive",
        ),
        CheckConstraint("target_carb_g IS NULL OR target_carb_g >= 0", name="target_carb_non_negative"),
        CheckConstraint("target_protein_g IS NULL OR target_protein_g >= 0", name="target_protein_non_negative"),
        CheckConstraint("target_fat_g IS NULL OR target_fat_g >= 0", name="target_fat_non_negative"),
        CheckConstraint("activity_level >= 0", name="activity_level_non_negative"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
    )
    goal_type: Mapped[GoalType] = mapped_column(Enum(GoalType, name="goal_type"), nullable=False)
    activity_level: Mapped[float] = mapped_column(Float, nullable=False, server_default="0")
    target_weight_kg: Mapped[Decimal | None] = mapped_column(Numeric(5, 2), nullable=True)
    target_daily_calories_kcal: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    target_carb_g: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    target_protein_g: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    target_fat_g: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="true")

    user: Mapped[User] = relationship(back_populates="goal")


class Tag(Base, TimestampMixin):
    __tablename__ = "tags"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    code: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    display_name: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    description: Mapped[str | None] = mapped_column(String(255), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="true")

    users: Mapped[list[UserTag]] = relationship(back_populates="tag", cascade="all, delete-orphan")


class UserTag(Base, TimestampMixin):
    __tablename__ = "user_tags"

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        primary_key=True,
    )
    tag_id: Mapped[int] = mapped_column(
        Integer,
        ForeignKey("tags.id", ondelete="CASCADE"),
        primary_key=True,
    )

    user: Mapped[User] = relationship(back_populates="tags")
    tag: Mapped[Tag] = relationship(back_populates="users")


class NutritionIntake(Base, TimestampMixin):
    __tablename__ = "nutrition_intakes"
    __table_args__ = (
        CheckConstraint("energy_kcal >= 0", name="energy_kcal_non_negative"),
        CheckConstraint("fat_g >= 0", name="fat_g_non_negative"),
        CheckConstraint("carb_g >= 0", name="carb_g_non_negative"),
        CheckConstraint("protein_g >= 0", name="protein_g_non_negative"),
        CheckConstraint("calcium_mg >= 0", name="calcium_mg_non_negative"),
        CheckConstraint("iron_mg >= 0", name="iron_mg_non_negative"),
        CheckConstraint("fiber_g >= 0", name="fiber_g_non_negative"),
        CheckConstraint("sodium_mg >= 0", name="sodium_mg_non_negative"),
        Index("ix_nutrition_intakes_user_id_intake_time", "user_id", "intake_time"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    intake_time: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    meal_type: Mapped[MealType] = mapped_column(Enum(MealType, name="meal_type"), nullable=False)
    food_name: Mapped[str] = mapped_column(String(128), nullable=False)
    portion_size: Mapped[str | None] = mapped_column(String(64), nullable=True)
    source_type: Mapped[SourceType] = mapped_column(
        Enum(SourceType, name="source_type"),
        nullable=False,
        server_default=SourceType.AI.value,
    )

    energy_kcal: Mapped[Decimal] = mapped_column(Numeric(8, 2), nullable=False)
    fat_g: Mapped[Decimal] = mapped_column(Numeric(8, 2), nullable=False)
    carb_g: Mapped[Decimal] = mapped_column(Numeric(8, 2), nullable=False)
    protein_g: Mapped[Decimal] = mapped_column(Numeric(8, 2), nullable=False)
    calcium_mg: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    iron_mg: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    fiber_g: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)
    sodium_mg: Mapped[Decimal | None] = mapped_column(Numeric(8, 2), nullable=True)

    remark: Mapped[str | None] = mapped_column(Text, nullable=True)

    user: Mapped[User] = relationship(back_populates="nutrition_intakes")
