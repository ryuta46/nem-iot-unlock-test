package com.ryuta46.nemiotunlocklib

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.charset.Charset
import java.util.*


class ZXingWrapper {
    companion object {

        fun createBitmap(data: ByteArray, width: Int, height: Int): Bitmap {

            val writer = QRCodeWriter()
            val encodeHint = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)

            val dataStr = String(data, Charset.forName("ISO_8859_1"))
            val qrCodeData = writer.encode(dataStr, BarcodeFormat.QR_CODE, 0, 0, encodeHint);

            val bitmap = Bitmap.createBitmap(qrCodeData.width, qrCodeData.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until qrCodeData.width) {
                for (y in 0 until qrCodeData.height) {
                    bitmap.setPixel(x, y, if (qrCodeData.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

            return Bitmap.createScaledBitmap(bitmap, width, height, false)
        }
    }
}