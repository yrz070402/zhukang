"""add activity_level to user_goals

Revision ID: 20260417_0005
Revises: 20260417_0004
Create Date: 2026-04-17 00:00:05

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260417_0005"
down_revision: Union[str, None] = "20260417_0004"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "user_goals",
        sa.Column("activity_level", sa.Float(), nullable=True, server_default=sa.text("0")),
    )
    op.execute(sa.text("UPDATE user_goals SET activity_level = 0 WHERE activity_level IS NULL"))
    op.alter_column("user_goals", "activity_level", existing_type=sa.Float(), nullable=False)
    op.create_check_constraint(
        op.f("ck_user_goals_activity_level_non_negative"),
        "user_goals",
        "activity_level >= 0",
    )


def downgrade() -> None:
    op.drop_constraint(op.f("ck_user_goals_activity_level_non_negative"), "user_goals", type_="check")
    op.drop_column("user_goals", "activity_level")
