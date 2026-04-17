"""update goal uniqueness and nullable micro nutrients

Revision ID: 20260417_0003
Revises: 20260413_0002
Create Date: 2026-04-17 00:00:03

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260417_0003"
down_revision: Union[str, None] = "20260413_0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Keep only one goal per user before adding unique constraint.
    # Preference order: non-deleted first, then newest updated/created row.
    op.execute(
        sa.text(
            """
            WITH ranked AS (
                SELECT
                    id,
                    ROW_NUMBER() OVER (
                        PARTITION BY user_id
                        ORDER BY
                            CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END,
                            updated_at DESC,
                            created_at DESC,
                            id DESC
                    ) AS rn
                FROM user_goals
            )
            DELETE FROM user_goals ug
            USING ranked r
            WHERE ug.id = r.id
              AND r.rn > 1
            """
        )
    )

    op.create_unique_constraint(op.f("uq_user_goals_user_id"), "user_goals", ["user_id"])

    op.alter_column(
        "nutrition_intakes",
        "calcium_mg",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=True,
        server_default=None,
    )
    op.alter_column(
        "nutrition_intakes",
        "iron_mg",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=True,
        server_default=None,
    )
    op.alter_column(
        "nutrition_intakes",
        "fiber_g",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=True,
        server_default=None,
    )
    op.alter_column(
        "nutrition_intakes",
        "sodium_mg",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=True,
        server_default=None,
    )


def downgrade() -> None:
    op.alter_column(
        "nutrition_intakes",
        "sodium_mg",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=False,
        server_default=sa.text("0"),
    )
    op.alter_column(
        "nutrition_intakes",
        "fiber_g",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=False,
        server_default=sa.text("0"),
    )
    op.alter_column(
        "nutrition_intakes",
        "iron_mg",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=False,
        server_default=sa.text("0"),
    )
    op.alter_column(
        "nutrition_intakes",
        "calcium_mg",
        existing_type=sa.Numeric(precision=8, scale=2),
        nullable=False,
        server_default=sa.text("0"),
    )

    op.drop_constraint(op.f("uq_user_goals_user_id"), "user_goals", type_="unique")
