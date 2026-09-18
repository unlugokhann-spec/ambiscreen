#!/usr/bin/env python3
"""
DRM korumalı akışlar (Netflix, Widevine L1 vb.) Android'in ekran yakalama
API'sini (`MediaProjection`) donanım seviyesinde engellediği için, uygulama
içi çözüm bu içerikte çalışmaz. Bu betik onun yerine ayrı bir HDMI capture
cihazından (projektörün HDMI çıkışına bir splitter ile takılı) gelen görüntüyü
okuyup Android tarafıyla aynı algoritmayla (kenar örnekleme + letterbox
tespiti) renk çıkarır ve aynı DDP protokolüyle WLED'e gönderir.

Kullanım:
    python3 ambient_sync.py --config config.json

Kurulum detayları için hdmi-capture/README.md dosyasına bakın.
"""
from __future__ import annotations

import argparse
import json
import sys
import time

import cv2
import numpy as np

from color_extractor import average_colors, build_zones_for_layout, detect_content_rect
from ddp_sender import DdpSender
from led_layout import LedLayoutConfig

CAPTURE_WIDTH = 160
CAPTURE_HEIGHT = 90


def load_config(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def apply_smoothing(state: np.ndarray | None, raw: np.ndarray, smoothing_percent: int) -> np.ndarray:
    alpha = 1.0 - min(max(smoothing_percent, 0), 95) / 100.0
    raw_f = raw.astype(np.float32)
    if state is None or state.shape != raw_f.shape:
        return raw_f.copy()
    state += (raw_f - state) * alpha
    return state


def apply_brightness_and_order(colors: np.ndarray, brightness_percent: int, reverse: bool) -> np.ndarray:
    scale = min(max(brightness_percent, 0), 100) / 100.0
    scaled = np.clip(colors * scale, 0, 255).astype(np.uint8)
    return scaled[::-1] if reverse else scaled


def main() -> int:
    parser = argparse.ArgumentParser(description="HDMI capture -> WLED ambiyans senkronizasyonu")
    parser.add_argument("--config", default="config.json", help="Yapılandırma dosyası yolu")
    args = parser.parse_args()

    config = load_config(args.config)
    device = config.get("capture_device", 0)
    wled_ip = config["wled_ip"]
    wled_port = config.get("wled_port", 4048)
    margin_percent = config.get("margin_percent", 12)
    interval_ms = config.get("interval_ms", 120)
    brightness_percent = config.get("brightness_percent", 100)
    smoothing_percent = config.get("smoothing_percent", 35)
    reverse_direction = config.get("reverse_direction", False)
    layout = LedLayoutConfig.from_dict(config.get("led_layout"))

    if layout.total_led_count() <= 0:
        print("led_layout içinde açık ve LED sayısı > 0 olan en az bir kenar olmalı.", file=sys.stderr)
        return 1

    cap = cv2.VideoCapture(device)
    if not cap.isOpened():
        print(f"Yakalama cihazı açılamadı: {device}", file=sys.stderr)
        return 1

    sender = DdpSender()
    smoothed_state: np.ndarray | None = None
    last_send = 0.0

    print(f"AmbiScreen HDMI senkronizasyonu başladı -> {wled_ip}:{wled_port} "
          f"({layout.total_led_count()} LED)")

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                time.sleep(0.05)
                continue

            now = time.monotonic()
            if (now - last_send) * 1000 < interval_ms:
                continue
            last_send = now

            small = cv2.resize(frame, (CAPTURE_WIDTH, CAPTURE_HEIGHT), interpolation=cv2.INTER_AREA)
            rect = detect_content_rect(small)
            zones = build_zones_for_layout(rect, layout, margin_percent)
            if not zones:
                continue

            raw_colors = average_colors(small, zones)
            smoothed_state = apply_smoothing(smoothed_state, raw_colors, smoothing_percent)
            final_colors = apply_brightness_and_order(smoothed_state, brightness_percent, reverse_direction)

            sender.send(wled_ip, wled_port, final_colors)
    except KeyboardInterrupt:
        pass
    finally:
        cap.release()
        sender.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
