"""Bitelog 缩略图生成：rembg 抠图 + 居中裁剪 + 圆形 PNG。"""
from __future__ import annotations

import io
import logging
import threading
from typing import Optional

import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)

# rembg session 首次构建较慢且需要加载模型，通过锁懒加载后复用。
_session_lock = threading.Lock()
_rembg_session = None


def _get_rembg_session():
    """懒加载 rembg session，避免服务启动成本。"""
    global _rembg_session
    if _rembg_session is not None:
        return _rembg_session
    with _session_lock:
        if _rembg_session is not None:
            return _rembg_session
        try:
            from rembg import new_session

            # u2netp 是 ~4MB 的精简版，对食物识别效果足够且部署更轻。
            _rembg_session = new_session("u2netp")
        except Exception as exc:
            logger.warning("rembg session 初始化失败，后续将退化为中心圆裁剪: %s", exc)
            _rembg_session = False
        return _rembg_session


def _remove_background_bgr(bgr_image: np.ndarray) -> Optional[Image.Image]:
    """使用 rembg 去除背景，返回 RGBA PIL 图像；失败时返回 None。"""
    session = _get_rembg_session()
    if not session:
        return None

    try:
        from rembg import remove

        rgb_image = Image.fromarray(bgr_image[:, :, ::-1])  # BGR -> RGB
        rgba = remove(rgb_image, session=session)
        if rgba.mode != "RGBA":
            rgba = rgba.convert("RGBA")
        return rgba
    except Exception as exc:
        logger.warning("rembg 抠图失败，退化为中心圆裁剪: %s", exc)
        return None


def _alpha_bbox(rgba: Image.Image) -> Optional[tuple[int, int, int, int]]:
    """根据 alpha 通道计算前景 bounding box，空图时返回 None。"""
    alpha = rgba.split()[-1]
    bbox = alpha.getbbox()
    return bbox


def _center_square_crop(image: Image.Image) -> Image.Image:
    """按短边从中心裁剪成正方形。"""
    width, height = image.size
    edge = min(width, height)
    left = (width - edge) // 2
    top = (height - edge) // 2
    return image.crop((left, top, left + edge, top + edge))


def _pad_to_square(image: Image.Image) -> Image.Image:
    """用透明背景把前景贴到居中的正方形画布上。"""
    width, height = image.size
    edge = max(width, height)
    canvas = Image.new("RGBA", (edge, edge), (0, 0, 0, 0))
    canvas.paste(image, ((edge - width) // 2, (edge - height) // 2), image)
    return canvas


def _apply_circle_mask(image: Image.Image, size: int) -> Image.Image:
    """把正方形图像缩放并加圆形透明遮罩，生成统一尺寸的圆形缩略图。"""
    square = image.resize((size, size), Image.LANCZOS)
    if square.mode != "RGBA":
        square = square.convert("RGBA")

    mask = Image.new("L", (size, size), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)

    circular = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    circular.paste(square, (0, 0), mask)
    return circular


def build_thumbnail_png(bgr_image: np.ndarray, size: int = 256) -> bytes:
    """
    从 OpenCV BGR 图像生成统一规格（默认 256x256）的圆形 PNG 缩略图。

    流程：
        1. rembg 抠图（可用时）
        2. 按 alpha bbox 裁掉多余透明区域 -> 正方形居中 padding
        3. 若抠图失败则中心方形裁剪
        4. 缩放至 size x size，应用圆形遮罩
    """
    rgba = _remove_background_bgr(bgr_image)
    if rgba is not None:
        bbox = _alpha_bbox(rgba)
        if bbox is not None:
            rgba = rgba.crop(bbox)
        rgba = _pad_to_square(rgba)
        circular = _apply_circle_mask(rgba, size)
    else:
        rgb_image = Image.fromarray(bgr_image[:, :, ::-1])  # BGR -> RGB
        squared = _center_square_crop(rgb_image).convert("RGBA")
        circular = _apply_circle_mask(squared, size)

    buffer = io.BytesIO()
    circular.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()
