"""add macro targets to user_goals

Revision ID: 20260417_0006
Revises: 20260417_0005
Create Date: 2026-04-17 00:00:06

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260417_0006"
down_revision: Union[str, None] = "20260417_0005"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("user_goals", sa.Column("target_carb_g", sa.Numeric(precision=8, scale=2), nullable=True))
    op.add_column("user_goals", sa.Column("target_protein_g", sa.Numeric(precision=8, scale=2), nullable=True))
    op.add_column("user_goals", sa.Column("target_fat_g", sa.Numeric(precision=8, scale=2), nullable=True))

    op.create_check_constraint(
        op.f("ck_user_goals_target_carb_non_negative"),
        "user_goals",
        "target_carb_g IS NULL OR target_carb_g >= 0",
    )
    op.create_check_constraint(
        op.f("ck_user_goals_target_protein_non_negative"),
        "user_goals",
        "target_protein_g IS NULL OR target_protein_g >= 0",
    )
    op.create_check_constraint(
        op.f("ck_user_goals_target_fat_non_negative"),
        "user_goals",
        "target_fat_g IS NULL OR target_fat_g >= 0",
    )


def downgrade() -> None:
    op.drop_constraint(op.f("ck_user_goals_target_fat_non_negative"), "user_goals", type_="check")
    op.drop_constraint(op.f("ck_user_goals_target_protein_non_negative"), "user_goals", type_="check")
    op.drop_constraint(op.f("ck_user_goals_target_carb_non_negative"), "user_goals", type_="check")

    op.drop_column("user_goals", "target_fat_g")
    op.drop_column("user_goals", "target_protein_g")
    op.drop_column("user_goals", "target_carb_g")
