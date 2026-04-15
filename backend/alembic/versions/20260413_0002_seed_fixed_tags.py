"""seed fixed tags

Revision ID: 20260413_0002
Revises: 20260413_0001
Create Date: 2026-04-13 00:00:02

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260413_0002"
down_revision: Union[str, None] = "20260413_0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    tag_table = sa.table(
        "tags",
        sa.column("code", sa.String),
        sa.column("display_name", sa.String),
        sa.column("description", sa.String),
        sa.column("is_active", sa.Boolean),
    )

    op.bulk_insert(
        tag_table,
        [
            {
                "code": "fitness_enthusiast",
                "display_name": "健身爱好者",
                "description": "关注增肌、减脂与运动营养平衡",
                "is_active": True,
            },
            {
                "code": "chronic_disease",
                "display_name": "慢病管理",
                "description": "关注慢病相关饮食控制与营养监测",
                "is_active": True,
            },
            {
                "code": "sub_healthy",
                "display_name": "亚健康",
                "description": "关注日常膳食结构优化与体能恢复",
                "is_active": True,
            },
            {
                "code": "pregnant_postpartum",
                "display_name": "孕产期",
                "description": "关注孕期或产后阶段的营养摄入",
                "is_active": True,
            },
            {
                "code": "special_diet",
                "display_name": "特殊饮食需求",
                "description": "如低碳、低盐、素食或过敏规避",
                "is_active": True,
            },
        ],
    )


def downgrade() -> None:
    op.execute(
        sa.text(
            """
            DELETE FROM tags
            WHERE code IN (
                'fitness_enthusiast',
                'chronic_disease',
                'sub_healthy',
                'pregnant_postpartum',
                'special_diet'
            )
            """
        )
    )
