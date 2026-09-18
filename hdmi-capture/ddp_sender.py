"""Android tarafındaki `DdpSender.kt` ile aynı DDP (Distributed Display
Protocol) paketleyicisinin Python portu. WLED bu paketleri UDP üzerinden
algılayıp otomatik canlı moda geçer."""
from __future__ import annotations

import socket

import numpy as np


class DdpSender:
    def __init__(self) -> None:
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def send(self, ip: str, port: int, colors_rgb: np.ndarray) -> None:
        data_length = len(colors_rgb) * 3
        packet = bytearray(10 + data_length)
        packet[0] = 0x41  # versiyon=1, push biti set
        packet[1] = 0x00  # sequence (kullanılmıyor)
        packet[2] = 0x00  # data type: RGB, 8 bit
        packet[3] = 0x01  # output id: varsayılan çıkış
        packet[8] = (data_length >> 8) & 0xFF
        packet[9] = data_length & 0xFF

        offset = 10
        for r, g, b in colors_rgb:
            packet[offset] = int(r)
            packet[offset + 1] = int(g)
            packet[offset + 2] = int(b)
            offset += 3

        try:
            self._socket.sendto(bytes(packet), (ip, port))
        except OSError:
            # WLED geçici olarak erişilemez olabilir (kapalı/uykuda); bir
            # sonraki karede tekrar denenecek, tek bir kayıp paket önemsiz.
            pass

    def close(self) -> None:
        self._socket.close()
