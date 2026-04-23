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


# 饮食推荐 Prompt 模板
RECOMMENDATION_PROMPT = """# Role
你是一位专业的 AI 营养师。你的任务是根据用户上一餐的饮食记录和营养摄入数据，为他们的下一餐提供健康、均衡且符合其整体饮食目标的建议。

# Input Data
- 用户年龄：{age}岁
- 用户性别：{sex}
- 用户身高：{height_cm}cm
- 用户体重：{weight_kg}kg
- 健康目标：{goal_type}
- 活动水平：{activity_level}
- 饮食禁忌/过敏：{dietary_restrictions}
- 当前餐次剩余热量需求：{remaining_calories} kcal
- 当前餐次剩余蛋白质需求：{remaining_protein} g
- 当前餐次剩余脂肪需求：{remaining_fat} g
- 当前餐次剩余碳水需求：{remaining_carbs} g
- 用户选择的食材偏好：{selected_foods}
- 用户手动输入的其他食物：{manual_input}
- 下一餐类型：{meal_type_cn}

# Rules & Logic
1. 营养互补：分析上一餐的营养短板或过剩。例如，上一餐碳水过高而蛋白质不足，下一餐应推荐高蛋白、低碳水。
2. 热量控制：结合每日饮食目标和已摄入热量，严格控制下一餐的推荐热量范围。
3. 食材偏好响应 (重要)：
   - 条件 A (有偏好)：如果"用户选择的食材偏好"中包含具体食材（如：牛肉、番茄），请在至少 1-2 个推荐菜品中作为核心食材使用。你需要巧妙地采用健康的烹饪方式（如清蒸、少油快炒）来处理这些食材，以确保依然符合用户的饮食目标。
   - 条件 B (无偏好/随机)：如果"用户选择的食材偏好"为空或为"无"，请在符合前两条营养逻辑的前提下，利用你庞大的食谱库，为用户随机组合推荐多样化、时令性强且健康的菜谱，保持饮食的新鲜感。
4. 实用性：推荐的菜品应当是日常容易获取或制作的，提供 2-3 个不同口味的选项。
5. 烹饪做法：每个推荐菜品必须提供简洁实用的烹饪步骤，帮助用户轻松制作。

# Output Format
请务必以严格的 JSON 格式输出结果，不要包含任何额外的 Markdown 标记或解释性文字。JSON 结构如下：
{{
    "analysis": "对上一餐摄入的简短营养评价（限50字内）",
    "next_meal_target": {{
        "target_calories": "建议热量值或范围",
        "focus_macros": "建议重点补充的营养素"
    }},
    "recommendations": [
        {{
            "dish_name": "推荐菜品名称",
            "reason": "推荐理由（如果使用了指定食材，请在此处说明该食材的健康烹饪方式；如果是随机推荐，说明其营养价值）",
            "estimated_calories": "预估热量",
            "cooking_steps": "烹饪步骤（简洁实用，2-4步即可，用逗号或分号分隔）"
        }}
    ]
}}

只返回JSON，不要有任何其他内容。"""

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
        async with httpx.AsyncClient(timeout=120.0) as client:
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
    支持嵌套的 JSON 对象
    """
    import re

    text = text.strip()

    # 尝试提取 markdown 代码块中的 JSON
    if "```" in text:
        # 匹配 ```json ... ``` 或 ``` ... ```
        match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', text, re.DOTALL)
        if match:
            candidate = match.group(1).strip()
            if candidate.startswith("{"):
                return candidate

    # 如果以 { 开头，提取完整的嵌套 JSON 对象
    if text.startswith("{"):
        # 找到匹配的闭合花括号
        depth = 0
        end_idx = 0
        for i, char in enumerate(text):
            if char == '{':
                depth += 1
            elif char == '}':
                depth -= 1
                if depth == 0:
                    end_idx = i + 1
                    break
        if end_idx > 0:
            return text[:end_idx]

    # 尝试找到第一个完整的 JSON 对象（支持嵌套）
    start_idx = text.find('{')
    if start_idx != -1:
        depth = 0
        end_idx = 0
        for i in range(start_idx, len(text)):
            if text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
                if depth == 0:
                    end_idx = i + 1
                    break
        if end_idx > start_idx:
            return text[start_idx:end_idx]

    return text


async def call_doubao_text(prompt: str, max_tokens: int = 1000, temperature: float = 0.7) -> str:
    """
    调用豆包 API 进行文本生成（非图片分析）

    Args:
        prompt: 文本提示词
        max_tokens: 最大生成 token 数
        temperature: 生成温度（0-1，越高越随机）

    Returns:
        str: 生成的文本内容
    """
    # 检查配置
    if not settings.doubao_api_key or settings.doubao_api_key == "your_api_key_here":
        logger.warning("豆包 API Key 未配置，返回 Mock 数据")
        return json.dumps({
            "analysis": "API Key 未配置，无法生成个性化建议",
            "recommendations": ["请配置豆包 API Key 以获取个性化饮食建议"],
            "suggestedMeals": []
        }, ensure_ascii=False)

    # 构建请求体（OpenAI 兼容格式）
    payload = {
        "model": settings.doubao_model_id,
        "messages": [
            {
                "role": "user",
                "content": prompt
            }
        ],
        "max_tokens": max_tokens,
        "temperature": temperature
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
        async with httpx.AsyncClient(timeout=120.0) as client:
            logger.info(f"调用豆包文本 API: {url}")
            response = await client.post(url, json=payload, headers=headers)

            # 检查响应状态
            if response.status_code != 200:
                logger.error(f"豆包 API 错误: {response.status_code} - {response.text}")
                raise Exception(f"API 调用失败: {response.status_code}")

            # 解析响应
            result = response.json()
            content = result["choices"][0]["message"]["content"]
            logger.info(f"豆包返回内容: {content}")

            return content

    except httpx.TimeoutException:
        logger.error("豆包 API 请求超时")
        raise Exception("API 请求超时，请稍后重试")

    except Exception as e:
        logger.error(f"调用豆包文本 API 失败: {e}")
        raise e
