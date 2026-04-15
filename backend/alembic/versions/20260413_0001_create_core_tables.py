"""create core tables

Revision ID: 20260413_0001
Revises: 
Create Date: 2026-04-13 00:00:01

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = "20260413_0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


sex_type = postgresql.ENUM("MALE", "FEMALE", "OTHER", name="sex_type", create_type=False)
goal_type = postgresql.ENUM(
    "MUSCLE_GAIN",
    "FAT_LOSS",
    "MAINTAIN",
    "CHRONIC_DISEASE_MANAGEMENT",
    name="goal_type",
    create_type=False,
)
meal_type = postgresql.ENUM("BREAKFAST", "LUNCH", "DINNER", "SNACK", name="meal_type", create_type=False)
source_type = postgresql.ENUM("AI", "MANUAL", name="source_type", create_type=False)


def upgrade() -> None:
    bind = op.get_bind()
    sex_type.create(bind, checkfirst=True)
    goal_type.create(bind, checkfirst=True)
    meal_type.create(bind, checkfirst=True)
    source_type.create(bind, checkfirst=True)

    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("account", sa.String(length=128), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("is_active", sa.Boolean(), server_default=sa.text("true"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
        sa.UniqueConstraint("account", name=op.f("uq_users_account")),
    )

    op.create_table(
        "tags",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("code", sa.String(length=64), nullable=False),
        sa.Column("display_name", sa.String(length=64), nullable=False),
        sa.Column("description", sa.String(length=255), nullable=True),
        sa.Column("is_active", sa.Boolean(), server_default=sa.text("true"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_tags")),
        sa.UniqueConstraint("code", name=op.f("uq_tags_code")),
        sa.UniqueConstraint("display_name", name=op.f("uq_tags_display_name")),
    )

    op.create_table(
        "user_goals",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("goal_type", goal_type, nullable=False),
        sa.Column("target_weight_kg", sa.Numeric(precision=5, scale=2), nullable=True),
        sa.Column("target_daily_calories_kcal", sa.Numeric(precision=8, scale=2), nullable=True),
        sa.Column("note", sa.Text(), nullable=True),
        sa.Column("is_active", sa.Boolean(), server_default=sa.text("true"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint("target_weight_kg IS NULL OR target_weight_kg > 0", name=op.f("ck_user_goals_target_weight_positive")),
        sa.CheckConstraint(
            "target_daily_calories_kcal IS NULL OR target_daily_calories_kcal > 0",
            name=op.f("ck_user_goals_target_daily_calories_positive"),
        ),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=op.f("fk_user_goals_user_id_users"), ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_user_goals")),
    )

    op.create_table(
        "user_profiles",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("height_cm", sa.Numeric(precision=5, scale=2), nullable=False),
        sa.Column("weight_kg", sa.Numeric(precision=5, scale=2), nullable=False),
        sa.Column("age", sa.Integer(), nullable=False),
        sa.Column("sex", sex_type, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint("height_cm > 0", name=op.f("ck_user_profiles_height_cm_positive")),
        sa.CheckConstraint("weight_kg > 0", name=op.f("ck_user_profiles_weight_kg_positive")),
        sa.CheckConstraint("age >= 1 AND age <= 120", name=op.f("ck_user_profiles_age_valid_range")),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=op.f("fk_user_profiles_user_id_users"), ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_user_profiles")),
        sa.UniqueConstraint("user_id", name=op.f("uq_user_profiles_user_id")),
    )

    op.create_table(
        "user_tags",
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("tag_id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["tag_id"], ["tags.id"], name=op.f("fk_user_tags_tag_id_tags"), ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=op.f("fk_user_tags_user_id_users"), ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("user_id", "tag_id", name=op.f("pk_user_tags")),
    )

    op.create_table(
        "nutrition_intakes",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("intake_time", sa.DateTime(timezone=True), nullable=False),
        sa.Column("meal_type", meal_type, nullable=False),
        sa.Column("food_name", sa.String(length=128), nullable=False),
        sa.Column("portion_size", sa.String(length=64), nullable=True),
        sa.Column("source_type", source_type, server_default=sa.text("'AI'"), nullable=False),
        sa.Column("energy_kcal", sa.Numeric(precision=8, scale=2), nullable=False),
        sa.Column("fat_g", sa.Numeric(precision=8, scale=2), nullable=False),
        sa.Column("carb_g", sa.Numeric(precision=8, scale=2), nullable=False),
        sa.Column("protein_g", sa.Numeric(precision=8, scale=2), nullable=False),
        sa.Column("calcium_mg", sa.Numeric(precision=8, scale=2), server_default=sa.text("0"), nullable=False),
        sa.Column("iron_mg", sa.Numeric(precision=8, scale=2), server_default=sa.text("0"), nullable=False),
        sa.Column("fiber_g", sa.Numeric(precision=8, scale=2), server_default=sa.text("0"), nullable=False),
        sa.Column("sodium_mg", sa.Numeric(precision=8, scale=2), server_default=sa.text("0"), nullable=False),
        sa.Column("remark", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint("energy_kcal >= 0", name=op.f("ck_nutrition_intakes_energy_kcal_non_negative")),
        sa.CheckConstraint("fat_g >= 0", name=op.f("ck_nutrition_intakes_fat_g_non_negative")),
        sa.CheckConstraint("carb_g >= 0", name=op.f("ck_nutrition_intakes_carb_g_non_negative")),
        sa.CheckConstraint("protein_g >= 0", name=op.f("ck_nutrition_intakes_protein_g_non_negative")),
        sa.CheckConstraint("calcium_mg >= 0", name=op.f("ck_nutrition_intakes_calcium_mg_non_negative")),
        sa.CheckConstraint("iron_mg >= 0", name=op.f("ck_nutrition_intakes_iron_mg_non_negative")),
        sa.CheckConstraint("fiber_g >= 0", name=op.f("ck_nutrition_intakes_fiber_g_non_negative")),
        sa.CheckConstraint("sodium_mg >= 0", name=op.f("ck_nutrition_intakes_sodium_mg_non_negative")),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=op.f("fk_nutrition_intakes_user_id_users"), ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_nutrition_intakes")),
    )
    op.create_index(
        "ix_nutrition_intakes_user_id_intake_time",
        "nutrition_intakes",
        ["user_id", "intake_time"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_nutrition_intakes_user_id_intake_time", table_name="nutrition_intakes")
    op.drop_table("nutrition_intakes")
    op.drop_table("user_tags")
    op.drop_table("user_profiles")
    op.drop_table("user_goals")
    op.drop_table("tags")
    op.drop_table("users")

    bind = op.get_bind()
    source_type.drop(bind, checkfirst=True)
    meal_type.drop(bind, checkfirst=True)
    goal_type.drop(bind, checkfirst=True)
    sex_type.drop(bind, checkfirst=True)
