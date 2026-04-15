"""
食物分析接口 - 调用豆包大模型 Vision API
"""
from fastapi import APIRouter, UploadFile, File, HTTPException
from app.models.schemas import FoodAnalysisResponse
from app.services.doubao_service import analyze_food_image
from app.utils.image_processor import ImageProcessor
import cv2
import numpy as np
import logging

router = APIRouter()
logger = logging.getLogger(__name__)
image_processor = ImageProcessor(max_size=1024)


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


@router.post("/analyze", response_model=FoodAnalysisResponse)
async def analyze_food(image: UploadFile = File(...)):
    """
    分析食物图片

    - **image**: 食物图片文件（支持 jpg, png, webp 等格式）

    使用豆包大模型 Vision API 识别食物并分析营养成分
    """
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

        print(f"[RESULT] 分析结果: {result.model_dump()}")
        return result

    except HTTPException:
        raise

    except Exception as e:
        logger.error(f"食物分析失败: {e}")
        raise HTTPException(status_code=500, detail=f"食物分析失败: {str(e)}")


@router.post("/analyze/mock", response_model=FoodAnalysisResponse)
async def analyze_food_mock(image: UploadFile = File(...)):
    """
    调试接口：与 analyze_food 相同的图片处理流程，但不调用豆包 API，直接返回模拟结果
    """
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
        return FoodAnalysisResponse(
            food_name="调试样例-鸡胸肉沙拉",
            calories=420.0,
            protein=32.0,
            fat=14.0,
            carbs=38.0
        )

    except HTTPException:
        raise

    except Exception as e:
        logger.error(f"[MOCK] 食物分析失败: {e}")
        raise HTTPException(status_code=500, detail=f"食物分析失败: {str(e)}")
