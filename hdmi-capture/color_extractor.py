"""
Android tarafındaki `ColorExtractor.kt` mantığının numpy ile yazılmış portu:
kırpılmış siyah bar (letterbox) tespiti + kenar başına ortalama renk çıkarımı.
Girdi karesi OpenCV'nin varsayılan BGR düzenindedir; çıktı renkleri RGB sırasında
(r, g, b) döner.
"""
from __future__ import annotations

import numpy as np

from led_layout import Edge, LedLayoutConfig

Rect = tuple[int, int, int, int]  # x0, y0, x1, y1 (x1/y1 hariç)


def detect_content_rect(frame: np.ndarray, black_threshold: int = 16, max_trim_fraction: float = 0.4) -> Rect:
    """Sinemaskop/letterbox içerikte üst-alt (veya pillarbox'ta yan) katı
    siyah şeritleri tespit edip gerçek görüntü alanını döner. Aşırı kırpmayı
    önlemek için toplam kırpma oranı `max_trim_fraction` ile sınırlanır."""
    height, width = frame.shape[:2]
    gray = frame.mean(axis=2)
    max_trim_rows = int(height * max_trim_fraction)
    max_trim_cols = int(width * max_trim_fraction)

    top = 0
    while top < max_trim_rows and gray[top, :].mean() < black_threshold:
        top += 1

    bottom = height - 1
    trimmed_bottom = 0
    while trimmed_bottom < max_trim_rows and bottom > top and gray[bottom, :].mean() < black_threshold:
        bottom -= 1
        trimmed_bottom += 1

    left = 0
    while left < max_trim_cols and gray[top : bottom + 1, left].mean() < black_threshold:
        left += 1

    right = width - 1
    trimmed_right = 0
    while trimmed_right < max_trim_cols and right > left and gray[top : bottom + 1, right].mean() < black_threshold:
        right -= 1
        trimmed_right += 1

    return left, top, min(right + 1, width), min(bottom + 1, height)


def _top_zones(rect: Rect, count: int, margin: int) -> list[Rect]:
    x0, y0, x1, y1 = rect
    width = x1 - x0
    height = y1 - y0
    zones = []
    for i in range(count):
        a = x0 + round(width * i / count)
        b = x0 + min(round(width * (i + 1) / count), width)
        zones.append((a, y0, b, y0 + min(margin, height)))
    return zones


def _right_zones(rect: Rect, count: int, margin: int) -> list[Rect]:
    x0, y0, x1, y1 = rect
    width = x1 - x0
    height = y1 - y0
    zones = []
    for i in range(count):
        a = y0 + round(height * i / count)
        b = y0 + min(round(height * (i + 1) / count), height)
        zones.append((x0 + max(width - margin, 0), a, x1, b))
    return zones


def _bottom_zones(rect: Rect, count: int, margin: int) -> list[Rect]:
    x0, y0, x1, y1 = rect
    width = x1 - x0
    height = y1 - y0
    zones = []
    for i in range(count):
        b = x0 + round(width * (count - i) / count)
        a = x0 + round(width * (count - i - 1) / count)
        zones.append((a, y0 + max(height - margin, 0), min(b, x1), y1))
    return zones


def _left_zones(rect: Rect, count: int, margin: int) -> list[Rect]:
    x0, y0, x1, y1 = rect
    width = x1 - x0
    height = y1 - y0
    zones = []
    for i in range(count):
        b = y0 + round(height * (count - i) / count)
        a = y0 + round(height * (count - i - 1) / count)
        zones.append((x0, a, x0 + min(margin, width), min(b, y1)))
    return zones


def build_zones_for_layout(rect: Rect, layout: LedLayoutConfig, margin_percent: int) -> list[Rect]:
    x0, y0, x1, y1 = rect
    width = x1 - x0
    height = y1 - y0
    if width <= 0 or height <= 0:
        return []

    margin = max(1, round(min(width, height) * margin_percent / 100))
    zones: list[Rect] = []
    for edge in layout.wiring_order():
        count = layout.config_for(edge).led_count
        if edge == Edge.TOP:
            zones += _top_zones(rect, count, margin)
        elif edge == Edge.RIGHT:
            zones += _right_zones(rect, count, margin)
        elif edge == Edge.BOTTOM:
            zones += _bottom_zones(rect, count, margin)
        elif edge == Edge.LEFT:
            zones += _left_zones(rect, count, margin)
    return zones


def average_colors(frame_bgr: np.ndarray, zones: list[Rect]) -> np.ndarray:
    """Her zonun ortalama rengini (N, 3) uint8 dizisi olarak RGB sırasında döner."""
    colors = np.zeros((len(zones), 3), dtype=np.uint8)
    for i, (x0, y0, x1, y1) in enumerate(zones):
        region = frame_bgr[y0:y1, x0:x1]
        if region.size == 0:
            continue
        mean_bgr = region.reshape(-1, 3).mean(axis=0)
        colors[i] = (mean_bgr[2], mean_bgr[1], mean_bgr[0])
    return colors
