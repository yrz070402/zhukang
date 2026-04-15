import cv2
import numpy as np
from typing import Optional, Tuple

class ImageProcessor:
    def __init__(self, max_size: int = 1024):
        """
        初始化图片处理器

        Args:
            max_size: 图片最大边长限制，默认1024px
        """
        self.max_size = max_size

    def preprocess_image(self, image: np.ndarray, roi: Optional[Tuple[int, int, int, int]] = None) -> np.ndarray:
        """
        对图片进行预处理，包括缩放、亮度补偿、CLAHE（如果需要）、高斯滤波

        Args:
            image: 输入的OpenCV图片（BGR格式）
            roi: 区域 of interest，格式为(x, y, width, height)，可选

        Returns:
            预处理后的图片
        """
        # 1. ROI提取（如果指定）
        if roi is not None:
            x, y, w, h = roi
            image = image[y:y+h, x:x+w]

        # 2. 等比例缩放
        processed_image = self._resize_image(image)

        # 3. 亮度分析
        needs_clahe, needs_gamma = self._analyze_brightness(processed_image)

        # 4. Gamma Correction（如果需要）
        if needs_gamma:
            processed_image = self._gamma_correction(processed_image)

        # 5. CLAHE（如果需要）
        if needs_clahe:
            processed_image = self._adaptive_histogram_equalization(processed_image)

        # 6. 高斯滤波
        filtered_image = cv2.GaussianBlur(processed_image, (5, 5), 0)

        return filtered_image

    def _resize_image(self, image: np.ndarray) -> np.ndarray:
        """
        等比例缩放图片，限制最大边长为max_size

        Args:
            image: 输入图片

        Returns:
            缩放后的图片
        """
        height, width = image.shape[:2]

        # 如果图片已经小于最大尺寸，直接返回
        if max(height, width) <= self.max_size:
            return image

        # 计算缩放比例
        scale = self.max_size / max(height, width)
        new_width = int(width * scale)
        new_height = int(height * scale)

        # 等比例缩放
        resized_image = cv2.resize(image, (new_width, new_height), interpolation=cv2.INTER_AREA)
        return resized_image

    def _analyze_brightness(self, image: np.ndarray) -> Tuple[bool, bool]:
        """
        分析图片亮度和对比度，决定是否需要CLAHE和Gamma Correction

        Args:
            image: 输入图片

        Returns:
            (needs_clahe, needs_gamma): 是否需要CLAHE和Gamma Correction
        """
        # 转换为HSV颜色空间
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        h, s, v = cv2.split(hsv)

        # 计算亮度统计
        mean_brightness = np.mean(v)
        std_brightness = np.std(v)

        # 判断条件
        needs_clahe = std_brightness < 30  # 对比度低
        needs_gamma = mean_brightness < 100  # 亮度低

        return needs_clahe, needs_gamma

    def _gamma_correction(self, image: np.ndarray, gamma: float = 0.8) -> np.ndarray:
        """
        Gamma Correction，轻微提升亮度

        Args:
            image: 输入图片
            gamma: gamma值，小于1增加亮度，大于1降低亮度

        Returns:
            调整后的图片
        """
        inv_gamma = 1.0 / gamma
        table = np.array([((i / 255.0) ** inv_gamma) * 255 for i in range(256)]).astype("uint8")
        return cv2.LUT(image, table)

    def _adaptive_histogram_equalization(self, image: np.ndarray) -> np.ndarray:
        """
        自适应直方图均衡化，增强图片对比度，保持颜色

        Args:
            image: 输入图片（BGR格式）

        Returns:
            均衡化后的图片
        """
        # 转换为YUV颜色空间
        yuv = cv2.cvtColor(image, cv2.COLOR_BGR2YUV)

        # 对Y通道进行CLAHE
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        yuv[:, :, 0] = clahe.apply(yuv[:, :, 0])

        # 转换回BGR
        equalized = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR)
        return equalized

    def get_roi(self, image: np.ndarray, roi: Tuple[int, int, int, int]) -> np.ndarray:
        """
        获取图片的ROI区域

        Args:
            image: 输入图片
            roi: 区域坐标(x, y, width, height)

        Returns:
            ROI区域图片
        """
        x, y, w, h = roi
        return image[y:y+h, x:x+w]

    def set_roi(self, image: np.ndarray, roi: Tuple[int, int, int, int], new_roi_image: np.ndarray) -> np.ndarray:
        """
        设置图片的ROI区域

        Args:
            image: 原始图片
            roi: 目标区域坐标(x, y, width, height)
            new_roi_image: 要替换的新ROI图片

        Returns:
            替换后的图片
        """
        x, y, w, h = roi
        result = image.copy()
        result[y:y+h, x:x+w] = new_roi_image
        return result

    def detect_food_region(self, image: np.ndarray) -> Optional[Tuple[int, int, int, int]]:
        """
        简单的食物区域检测（基于颜色和边缘）

        Args:
            image: 输入图片

        Returns:
            检测到的食物区域坐标(x, y, width, height)，如果未检测到返回None
        """
        # 转换为HSV颜色空间
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)

        # 定义食物颜色范围（可根据实际需求调整）
        lower_color = np.array([0, 40, 40])
        upper_color = np.array([30, 255, 255])

        # 创建颜色掩码
        mask = cv2.inRange(hsv, lower_color, upper_color)

        # 形态学操作去除噪声
        kernel = np.ones((5, 5), np.uint8)
        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)

        # 查找轮廓
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        if not contours:
            return None

        # 找到最大的轮廓
        largest_contour = max(contours, key=cv2.contourArea)

        # 获取边界框
        x, y, w, h = cv2.boundingRect(largest_contour)

        # 添加一些边距
        margin = 20
        x = max(0, x - margin)
        y = max(0, y - margin)
        w = min(image.shape[1] - x, w + 2 * margin)
        h = min(image.shape[0] - y, h + 2 * margin)

        return (x, y, w, h)