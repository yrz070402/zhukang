"""
食物分析接口 - 调用豆包大模型 Vision API
"""
from fastapi import APIRouter, UploadFile, File, HTTPException
from app.models.schemas import FoodAnalysisResponse
from app.services.doubao_service import analyze_food_image
import logging

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/analyze", response_model=FoodAnalysisResponse)
async def analyze_food(image: UploadFile = File(...)):
    """
    分析食物图片

    - **image**: 食物图片文件（支持 jpg, png, webp 等格式）

    使用豆包大模型 Vision API 识别食物并分析营养成分
    """
    # 读取图片内容
    contents = await image.read()

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
        # 调用豆包 API 分析食物
        result = await analyze_food_image(contents, image.filename or "food.jpg")

        print(f"[RESULT] 分析结果: {result.model_dump()}")
        return result

    except Exception as e:
        logger.error(f"食物分析失败: {e}")
        raise HTTPException(status_code=500, detail=f"食物分析失败: {str(e)}")
