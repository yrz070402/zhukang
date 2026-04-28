"""
食物分析接口 - 调用豆包大模型 Vision API
"""
import uuid
from datetime import datetime
from decimal import Decimal
from pathlib import Path
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
from app.utils.image_cutout import build_thumbnail_png
from app.utils.image_processor import ImageProcessor
import cv2
import numpy as np
import logging

router = APIRouter()
logger = logging.getLogger(__name__)
image_processor = ImageProcessor(max_size=1024)
settings = get_settings()


def _decode_preprocess_image(contents: bytes) -> np.ndarray:
    """解码上传图片并走预处理，返回 OpenCV BGR 数组。"""
    np_arr = np.frombuffer(contents, np.uint8)
    cv_image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    if cv_image is None:
        raise HTTPException(status_code=400, detail="无法解析图片，请检查格式")
    return image_processor.preprocess_image(cv_image)


def _encode_jpeg(processed_image: np.ndarray) -> bytes:
    """把预处理后的 BGR 图像编码为 JPEG bytes 供豆包识别使用。"""
    ok, encoded = cv2.imencode(".jpg", processed_image, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
    if not ok:
        raise HTTPException(status_code=500, detail="图片预处理后编码失败")
    return encoded.tobytes()


def _persist_thumbnail(user_id: UUID, processed_image: np.ndarray) -> str:
    """
    生成并落盘 Bitelog 圆形缩略图，返回可通过 /static 访问的相对路径。

    目录结构：storage/food_images/{user_id}/{YYYYMM}/{uuid}.png
    """
    png_bytes = build_thumbnail_png(processed_image, size=256)

    now_local = datetime.now(ZoneInfo(settings.timezone))
    sub_dir = Path(settings.food_image_subdir) / str(user_id) / now_local.strftime("%Y%m")
    abs_dir = Path(settings.static_root) / sub_dir
    abs_dir.mkdir(parents=True, exist_ok=True)

    filename = f"{uuid.uuid4().hex}.png"
    abs_path = abs_dir / filename
    abs_path.write_bytes(png_bytes)

    return f"/static/{sub_dir.as_posix()}/{filename}"


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
    image_url: str | None = None,
    meal_type: MealType | None = None,  # 前端传入的餐次，优先使用
) -> None:
    intake_time = datetime.now(ZoneInfo(settings.timezone))
    # 如果前端传了餐次，使用前端的值；否则根据时间自动判断
    resolved_meal_type = meal_type if meal_type else _resolve_meal_type(intake_time)

    intake = NutritionIntake(
        user_id=user_id,
        intake_time=intake_time,
        meal_type=resolved_meal_type,
        food_name=result.food_name,
        energy_kcal=Decimal(str(result.calories)),
        fat_g=Decimal(str(result.fat or 0)),
        carb_g=Decimal(str(result.carbs or 0)),
        protein_g=Decimal(str(result.protein)),
        image_url=image_url,
        created_at=intake_time,
        updated_at=intake_time,
    )
    db.add(intake)
    await db.commit()


@router.post("/analyze", response_model=FoodAnalysisResponse)
async def analyze_food(
    image: UploadFile = File(...),
    user_id: UUID = Form(...),
    meal_type: str | None = Form(None),  # 前端传入的餐次（可选）
    db: AsyncSession = Depends(get_db),
):
    """
    分析食物图片

    - **image**: 食物图片文件（支持 jpg, png, webp 等格式）
    - **meal_type**: 餐次类型（可选）：BREAKFAST, LUNCH, DINNER, SNACK

    使用豆包大模型 Vision API 识别食物并分析营养成分
    """
    await _ensure_user_exists(db, user_id)

    # 解析前端传入的餐次
    resolved_meal_type: MealType | None = None
    if meal_type:
        try:
            resolved_meal_type = MealType[meal_type.upper()]
        except KeyError:
            logger.warning(f"无效的 meal_type: {meal_type}，将使用时间自动判断")

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
        processed_image = _decode_preprocess_image(contents)
        processed_bytes = _encode_jpeg(processed_image)
        logger.info(f"图片预处理完成: 原始={len(contents)} bytes, 预处理后={len(processed_bytes)} bytes")

        # 调用豆包 API 分析食物
        result = await analyze_food_image(processed_bytes, image.filename or "food.jpg")

        # 抠图落盘，失败不阻断主流程
        image_url: str | None = None
        try:
            image_url = _persist_thumbnail(user_id, processed_image)
            result.image_url = image_url
        except Exception as thumb_err:
            logger.warning(f"Bitelog 缩略图落盘失败，忽略: {thumb_err}")

        await _insert_nutrition_intake(db, user_id, result, image_url=image_url, meal_type=resolved_meal_type)

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
    meal_type: str | None = Form(None),  # 前端传入的餐次（可选）
    db: AsyncSession = Depends(get_db),
):
    """
    调试接口：与 analyze_food 相同的图片处理流程，但不调用豆包 API，直接返回模拟结果
    """
    await _ensure_user_exists(db, user_id)

    # 解析前端传入的餐次
    resolved_meal_type: MealType | None = None
    if meal_type:
        try:
            resolved_meal_type = MealType[meal_type.upper()]
        except KeyError:
            logger.warning(f"[MOCK] 无效的 meal_type: {meal_type}，将使用时间自动判断")

    contents = await image.read()

    if not contents:
        raise HTTPException(status_code=400, detail="上传图片为空")

    logger.info(f"[MOCK] 图片上传: {image.filename}, 大小: {len(contents)} bytes")

    try:
        processed_image = _decode_preprocess_image(contents)
        processed_bytes = _encode_jpeg(processed_image)
        logger.info(
            f"[MOCK] 图片预处理完成: 原始={len(contents)} bytes, 预处理后={len(processed_bytes)} bytes"
        )

        # 返回模拟识别结果，便于前后端联调（不写入每日累计，避免污染用户真实数据）
        result = FoodAnalysisResponse(
            food_name="海鲜拼盘",
            calories=420.0,
            protein=34.0,
            fat=16.0,
            carbs=38.0
        )

        image_url: str | None = None
        try:
            image_url = _persist_thumbnail(user_id, processed_image)
            result.image_url = image_url
        except Exception as thumb_err:
            logger.warning(f"[MOCK] Bitelog 缩略图落盘失败，忽略: {thumb_err}")

        # mock 结果仅用于预览，不落库
        return result

    except HTTPException:
        raise

    except Exception as e:
        logger.error(f"[MOCK] 食物分析失败: {e}")
        raise HTTPException(status_code=500, detail=f"食物分析失败: {str(e)}")
