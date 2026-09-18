#!/usr/bin/env python3
"""
Windows'ta (ve genel olarak) hangi `capture_device` indeksinin HDMI capture
kartına ait olduğunu bulmaya yarayan yardımcı araç. Linux'taki
`v4l2-ctl --list-devices` karşılığıdır; OpenCV/DirectShow üzerinden 0-9
arası indeksleri deneyip hangilerinin açıldığını ve çözünürlüğünü basar.

Kullanım:
    python list_devices.py
"""
from __future__ import annotations

import cv2


def main() -> None:
    print("Kamera/capture cihazları taranıyor (indeks 0-9)...")
    found_any = False
    for index in range(10):
        cap = cv2.VideoCapture(index)
        if not cap.isOpened():
            cap.release()
            continue
        found_any = True
        ok, frame = cap.read()
        if ok and frame is not None:
            height, width = frame.shape[:2]
            print(f"  [{index}] AÇILDI - çözünürlük {width}x{height}")
        else:
            print(f"  [{index}] açıldı ama görüntü okunamadı")
        cap.release()

    if not found_any:
        print("Hiçbir cihaz bulunamadı. Capture kartının USB'ye takılı ve "
              "sürücülerinin (varsa) kurulu olduğundan emin olun.")
        return

    print(
        "\nHDMI capture kartınızı config.json içindeki 'capture_device' "
        "alanına yukarıdaki listeden doğru indeksi yazarak seçin. Dahili "
        "webcam'iniz varsa genelde 0'dır; harici capture kartı çoğunlukla "
        "1 veya üzeri bir indekste görünür — hangisi olduğunu projektörden "
        "görüntü gelirken (çözünürlük projektörünüzünkiyle eşleşirken) "
        "anlayabilirsiniz."
    )


if __name__ == "__main__":
    main()
