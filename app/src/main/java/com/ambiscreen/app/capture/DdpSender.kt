package com.ambiscreen.app.capture

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * WLED'in yerleşik desteklediği DDP (Distributed Display Protocol) üzerinden
 * gerçek zamanlı RGB verisi gönderir. WLED, "Sync interfaces" ayarında DDP
 * girişi açıkken bu paketleri algılayıp otomatik olarak canlı moda geçer;
 * ekstra bir eşleştirme/handshake gerekmez.
 *
 * Paket biçimi (10 bayt başlık + 3*ledCount veri baytı):
 * [0] flags   = 0x41 (versiyon=1, push biti set)
 * [1] sequence (kullanılmıyor, 0)
 * [2] data type (0x00 = RGB, 8 bit)
 * [3] output id (0x01 = varsayılan çıkış)
 * [4-7] offset (big-endian, her zaman 0 - tek parça gönderiyoruz)
 * [8-9] length (big-endian, veri baytı sayısı)
 */
class DdpSender {

    private var socket: DatagramSocket? = null

    fun send(ip: String, port: Int, colorsRgb: IntArray) {
        val address = try {
            InetAddress.getByName(ip)
        } catch (e: Exception) {
            return
        }

        val dataLength = colorsRgb.size * 3
        val packet = ByteArray(10 + dataLength)
        packet[0] = 0x41
        packet[1] = 0x00
        packet[2] = 0x00
        packet[3] = 0x01
        packet[4] = 0
        packet[5] = 0
        packet[6] = 0
        packet[7] = 0
        packet[8] = ((dataLength shr 8) and 0xFF).toByte()
        packet[9] = (dataLength and 0xFF).toByte()

        var offset = 10
        for (color in colorsRgb) {
            packet[offset] = ((color shr 16) and 0xFF).toByte()
            packet[offset + 1] = ((color shr 8) and 0xFF).toByte()
            packet[offset + 2] = (color and 0xFF).toByte()
            offset += 3
        }

        try {
            val s = socket ?: DatagramSocket().also { socket = it }
            s.send(DatagramPacket(packet, packet.size, address, port))
        } catch (e: IOException) {
            // Ağ geçici olarak erişilemez olabilir (WLED kapalı/uykuda); bir sonraki
            // karede tekrar denenecek, tek bir kayıp paket görsel olarak önemsiz.
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }
}
