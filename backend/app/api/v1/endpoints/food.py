"""
食物分析接口 - 调用豆包大模型 Vision API
"""
from datetime import datetime
from decimal import Decimal
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, UploadFile, File, HTTPException, Form, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.database import get_db
from app.models.models import MealType, NutritionIntake, User
from app.models.schemas import FoodAnalysisResponse
from app.services.doubao_service import analyze_food_image
from app.utils.image_processor import ImageProcessor
import cv2
import numpy as np
import logging

router = APIRouter()
logger = logging.getLogger(__name__)
image_processor = ImageProcessor(max_size=1024)
settings = get_settings()


def _decode_preprocess_and_encode(contents: bytes) -> bytes:
    """
    统一处理上传图片：解码 -> 预处理 -> JPEG 编码
    """
    np_arr = np.frombuffer(contents, np.uint8)
    cv_image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    if cv_image is None:
        raise HTTPException(status_code=400, detail="无法解析图片，请检查格式")

    # 暂时不进行 ROI 检测，直接预处理整个图片
    processed_image = image_processor.preprocess_image(cv_image)

    ok, encoded = cv2.imencode(".jpg", processed_image, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
    if not ok:
        raise HTTPException(status_code=500, detail="图片预处理后编码失败")

    return encoded.tobytes()


def _resolve_meal_type(dt: datetime) -> MealType:
    hour = dt.hour
    if 5 <= hour <= 10:
        return MealType.BREAKFAST
    if 11 <= hour <= 14:
        return MealType.LUNCH
    if 17 <= hour <= 20:
        return MealType.DINNER
    return MealType.SNACK


async def _ensure_user_exists(db: AsyncSession, user_id: UUID) -> None:
    user = await db.scalar(
        select(User.id).where(
            User.id == user_id,
            User.deleted_at.is_(None),
            User.is_active.is_(True),
        )
    )
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")


async def _insert_nutrition_intake(
    db: AsyncSession,
    user_id: UUID,
    result: FoodAnalysisResponse,
) -> None:
    intake_time = datetime.now(ZoneInfo(settings.timezone))
    meal_type = _resolve_meal_type(intake_time)

    intake = NutritionIntake(
        user_id=user_id,
        intake_time=intake_time,
        meal_type=meal_type,
        food_name=result.food_name,
        energy_kcal=Decimal(str(result.calories)),
        fat_g=Decimal(str(result.fat or 0)),
        carb_g=Decimal(str(result.carbs or 0)),
        protein_g=Decimal(str(result.protein)),
        created_at=intake_time,
        updated_at=intake_time,
    )
    db.add(intake)
    await db.commit()


@router.post("/analyze", response_model=FoodAnalysisResponse)
async def analyze_food(
    image: UploadFile = File(...),
    user_id: UUID = Form(...),
    db: AsyncSession = Depends(get_db),
):
    """
    分析食物图片

    - **image**: 食物图片文件（支持 jpg, png, webp 等格式）

    使用豆包大模型 Vision API 识别食物并分析营养成分
    """
    await _ensure_user_exists(db, user_id)

    # 读取图片内容
    contents = await image.read()

    if not contents:
        raise HTTPException(status_code=400, detail="上传图片为空")

    # 打印图片信息到控制台
    print(f"\n{'='*50}")
    print(f"[INFO] 收到图片上传请求")
    print(f"   文件名: {image.filename}")
    print(f"   文件大小: {len(contents)} bytes ({len(contents)/1024:.2f} KB)")
    print(f"   文件类型: {image.content_type}")
    print(f"{'='*50}\n")

    # 记录日志
    logger.info(f"图片上传: {image.filename}, 大小: {len(contents)} bytes")

    try:
        processed_bytes = _decode_preprocess_and_encode(contents)
        logger.info(f"图片预处理完成: 原始={len(contents)} bytes, 预处理后={len(processed_bytes)} bytes")

        # 调用豆包 API 分析食物
        result = await analyze_food_image(processed_bytes, image.filename or "food.jpg")
        await _insert_nutrition_intake(db, user_id, result)

        print(f"[RESULT] 分析结果: {result.model_dump()}")
        return result

    except HTTPException:
        raise

    except Exception as e:
        logger.error(f"食物分析失败: {e}")
        raise HTTPException(status_code=500, detail=f"食物分析失败: {str(e)}")


@router.post("/analyze/mock", response_model=FoodAnalysisResponse)
async def analyze_food_mock(
    image: UploadFile = File(...),
    user_id: UUID = Form(...),
    db: AsyncSession = Depends(get_db),
):
    """
    调试接口：与 analyze_food 相同的图片处理流程，但不调用豆包 API，直接返回模拟结果
    """
    await _ensure_user_exists(db, user_id)

    contents = await image.read()

    if not contents:
        raise HTTPException(status_code=400, detail="上传图片为空")

    logger.info(f"[MOCK] 图片上传: {image.filename}, 大小: {len(contents)} bytes")

    try:
        processed_bytes = _decode_preprocess_and_encode(contents)
        logger.info(
            f"[MOCK] 图片预处理完成: 原始={len(contents)} bytes, 预处理后={len(processed_bytes)} bytes"
        )

        # 返回模拟豆包识别结果，便于前后端联调
        result = FoodAnalysisResponse(
            food_name="调试样例-鸡胸肉沙拉",
            calories=1220.0,
            protein=82.0,
            fat=15.0,
            carbs=280.0
        )
        await _insert_nutrition_intake(db, user_id, result)
        return result

    except HTTPException:
        raise

    except Exception as e:
        logger.error(f"[MOCK] 食物分析失败: {e}")
        raise HTTPException(status_code=500, detail=f"食物分析失败: {str(e)}")
