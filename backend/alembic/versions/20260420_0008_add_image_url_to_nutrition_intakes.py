"""add image_url to nutrition_intakes for Bitelog thumbnails

Revision ID: 20260420_0008
Revises: 20260418_0007
Create Date: 2026-04-20 21:00:00

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "20260420_0008"
down_revision: Union[str, None] = "20260418_0007"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "nutrition_intakes",
        sa.Column("image_url", sa.String(length=255), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("nutrition_intakes", "image_url")
