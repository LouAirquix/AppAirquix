package com.example.airquix01

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import android.media.Image
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ImageUtils {

    /**
     * Konvertiert einen ImageProxy (im YUV_420_888-Format) in ein Bitmap.
     *
     * Das YUV-Bild wird zuerst in das NV21-Format konvertiert, dann als JPEG komprimiert
     * und anschließend in ein Bitmap decodiert.
     */
    fun imageToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Konvertiert ein YUV_420_888 Image in ein NV21-ByteArray.
     *
     * - Die Y-Daten werden zeilenweise kopiert (unter Berücksichtigung des rowStride).
     * - Für die UV-Daten wird jede UV-Zeile in ein temporäres Array eingelesen.
     *   Dann wird die tatsächlich verfügbare Anzahl an UV-Samples pro Zeile (effectiveUVWidth)
     *   berechnet, und für diese Spalten werden die Werte ausgelesen. Für fehlende Werte
     *   wird 0 geschrieben.
     *
     * NV21 erwartet das Format: YYYYYY... VU VU VU ...
     */
    fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height

        // Hole die drei Planes: Y, U und V
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // Dupliziere die Buffer, damit deren Position nicht verändert wird
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()

        // Rewind, um sicherzustellen, dass von Anfang gelesen wird
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        // Gesamtgröße der Y-Daten
        val ySize = width * height

        // Für YUV_420_888:
        val uvWidth = if (width % 2 == 0) width / 2 else (width + 1) / 2
        val uvHeight = if (height % 2 == 0) height / 2 else (height + 1) / 2

        // Gesamtgröße des NV21-Arrays: Y-Daten + 2 * (uvWidth * uvHeight)
        val nv21Size = ySize + 2 * uvWidth * uvHeight
        val nv21 = ByteArray(nv21Size)

        // ---- Kopiere die Y-Daten zeilenweise ----
        val yRowStride = yPlane.rowStride
        for (row in 0 until height) {
            val offset = row * width
            if (yRowStride == width) {
                yBuffer.get(nv21, offset, width)
            } else {
                val rowData = ByteArray(yRowStride)
                yBuffer.get(rowData, 0, yRowStride)
                System.arraycopy(rowData, 0, nv21, offset, width)
            }
        }

        // ---- Kopiere die UV-Daten zeilenweise (NV21-Format: VU interleaved) ----
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        // Berechne effectiveUVWidth: Anzahl der tatsächlich verfügbaren UV-Samples pro Zeile
        val effectiveUVWidth = min(uvWidth, uRowStride / uPixelStride)
        // Optional: Debug-Log-Ausgabe (dekommentieren, falls benötigt)
        // Log.d("ImageUtils", "uvWidth=$uvWidth, uvHeight=$uvHeight, effectiveUVWidth=$effectiveUVWidth, uRowStride=$uRowStride, uPixelStride=$uPixelStride, vRowStride=$vRowStride, vPixelStride=$vPixelStride, uBuffer.limit()=${uBuffer.limit()}, vBuffer.limit()=${vBuffer.limit()}")

        // Temporäre Arrays zum Einlesen einer UV-Zeile
        val uRow = ByteArray(uRowStride)
        val vRow = ByteArray(vRowStride)
        var uvOffset = ySize

        for (row in 0 until uvHeight) {
            val bytesToReadU = min(uRow.size, uBuffer.remaining())
            val bytesToReadV = min(vRow.size, vBuffer.remaining())
            uBuffer.get(uRow, 0, bytesToReadU)
            vBuffer.get(vRow, 0, bytesToReadV)
            for (col in 0 until uvWidth) {
                val uIndex = col * uPixelStride
                val vIndex = col * vPixelStride
                val uValue = if (col < effectiveUVWidth && uIndex < bytesToReadU) uRow[uIndex] else 0
                val vValue = if (col < effectiveUVWidth && vIndex < bytesToReadV) vRow[vIndex] else 0
                nv21[uvOffset++] = vValue
                nv21[uvOffset++] = uValue
            }
        }
        return nv21
    }
}
