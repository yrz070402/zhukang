"""add avatar_index to users

Revision ID: 20260418_0007
Revises: 20260417_0006
Create Date: 2026-04-18 00:00:07

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "20260418_0007"
down_revision: Union[str, None] = "20260417_0006"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("avatar_index", sa.Integer(), nullable=False, server_default=sa.text("0")))
    op.create_check_constraint(
        op.f("ck_users_avatar_index_range"),
        "users",
        "avatar_index >= 0 AND avatar_index <= 11",
    )


def downgrade() -> None:
    op.drop_constraint(op.f("ck_users_avatar_index_range"), "users", type_="check")
    op.drop_column("users", "avatar_index")
