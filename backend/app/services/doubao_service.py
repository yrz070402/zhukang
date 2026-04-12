"""
豆包大模型 API 服务
使用 OpenAI 兼容格式调用豆包 Vision API
"""
import base64
import json
import httpx
import logging
from typing import Optional
from app.core.config import get_settings
from app.models.schemas import FoodAnalysisResponse

logger = logging.getLogger(__name__)
settings = get_settings()

# 营养分析 Prompt
NUTRITION_PROMPT = """你是一个专业的营养师。请分析这张图片中的食物，并返回JSON格式的营养数据。

请严格按照以下JSON格式返回，不要包含任何其他文字、markdown标记或解释：
{
    "food_name": "食物名称",
    "calories": 热量(千卡，数字),
    "protein": 蛋白质(克，数字),
    "fat": 脂肪(克，数字),
    "carbs": 碳水化合物(克，数字)
}

注意事项：
1. 如果图片中有多种食物，请分析主要食物或估算总量
2. 热量单位为千卡(kcal)，其他单位为克(g)
3. 如果图片中没有食物或无法识别，请返回：
{
    "food_name": "无法识别",
    "calories": 0,
    "protein": 0,
    "fat": 0,
    "carbs": 0
}

只返回JSON，不要有任何其他内容。"""


async def analyze_food_image(image_bytes: bytes, filename: str = "food.jpg") -> FoodAnalysisResponse:
    """
    调用豆包 Vision API 分析食物图片

    Args:
        image_bytes: 图片二进制数据
        filename: 文件名

    Returns:
        FoodAnalysisResponse: 食物营养分析结果
    """
    # 检查配置
    if not settings.doubao_api_key or settings.doubao_api_key == "your_api_key_here":
        logger.warning("豆包 API Key 未配置，返回 Mock 数据")
        return FoodAnalysisResponse(
            food_name="Mock数据-请配置API Key",
            calories=450.0,
            protein=20.0,
            fat=15.0,
            carbs=50.0
        )

    # 将图片转为 base64
    image_base64 = base64.b64encode(image_bytes).decode("utf-8")

    # 根据文件扩展名确定 MIME 类型
    ext = filename.lower().split(".")[-1] if "." in filename else "jpg"
    mime_types = {
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
        "gif": "image/gif",
        "webp": "image/webp"
    }
    mime_type = mime_types.get(ext, "image/jpeg")

    # 构建图片 URL（base64 格式）
    image_url = f"data:{mime_type};base64,{image_base64}"

    # 构建请求体（OpenAI 兼容格式）
    payload = {
        "model": settings.doubao_model_id,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": NUTRITION_PROMPT
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": image_url
                        }
                    }
                ]
            }
        ],
        "max_tokens": 500,
        "temperature": 0.1  # 低温度保证输出稳定
    }

    # 请求头
    headers = {
        "Authorization": f"Bearer {settings.doubao_api_key}",
        "Content-Type": "application/json"
    }

    # API 端点
    url = f"{settings.doubao_base_url}/chat/completions"

    try:
        # 发送请求
        async with httpx.AsyncClient(timeout=60.0) as client:
            logger.info(f"调用豆包 API: {url}")
            response = await client.post(url, json=payload, headers=headers)

            # 检查响应状态
            if response.status_code != 200:
                logger.error(f"豆包 API 错误: {response.status_code} - {response.text}")
                raise Exception(f"API 调用失败: {response.status_code}")

            # 解析响应
            result = response.json()
            content = result["choices"][0]["message"]["content"]
            logger.info(f"豆包返回内容: {content}")

            # 解析 JSON
            # 尝试提取 JSON 内容（处理可能的额外文字）
            json_str = extract_json(content)
            data = json.loads(json_str)

            return FoodAnalysisResponse(
                food_name=data.get("food_name", "未知食物"),
                calories=float(data.get("calories", 0)),
                protein=float(data.get("protein", 0)),
                fat=float(data.get("fat", 0)),
                carbs=float(data.get("carbs", 0))
            )

    except httpx.TimeoutException:
        logger.error("豆包 API 请求超时")
        raise Exception("API 请求超时，请稍后重试")

    except json.JSONDecodeError as e:
        logger.error(f"JSON 解析错误: {e}")
        raise Exception(f"AI 返回格式错误: {content}")

    except Exception as e:
        logger.error(f"调用豆包 API 失败: {e}")
        raise e


def extract_json(text: str) -> str:
    """
    从文本中提取 JSON 内容
    处理 AI 可能返回的额外文字或 markdown 标记
    """
    import re

    # 尝试直接解析
    text = text.strip()

    # 如果以 { 开头，直接返回
    if text.startswith("{"):
        # 去除末尾可能的非 JSON 字符
        match = re.search(r'\{[^{}]*\}', text, re.DOTALL)
        if match:
            return match.group()

    # 尝试提取 markdown 代码块中的 JSON
    if "```" in text:
        match = re.search(r'```(?:json)?\s*(\{.*?\})\s*```', text, re.DOTALL)
        if match:
            return match.group(1)

    # 尝试匹配 JSON 对象
    match = re.search(r'\{[^{}]*\}', text, re.DOTALL)
    if match:
        return match.group()

    return text
