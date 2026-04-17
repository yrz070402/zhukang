"""add nickname column for users

Revision ID: 20260417_0004
Revises: 20260417_0003
Create Date: 2026-04-17 00:00:04

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260417_0004"
down_revision: Union[str, None] = "20260417_0003"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("nickname", sa.String(length=128), nullable=True))

    op.execute(
        sa.text(
            """
            UPDATE users
            SET nickname = account
            WHERE nickname IS NULL OR btrim(nickname) = ''
            """
        )
    )

    op.alter_column("users", "nickname", existing_type=sa.String(length=128), nullable=False)


def downgrade() -> None:
    op.drop_column("users", "nickname")
