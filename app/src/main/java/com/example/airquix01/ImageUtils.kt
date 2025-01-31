// Datei: app/src/main/java/com/example/airquix01/ImageUtils.kt
package com.example.airquix01

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun imageToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun yuv420ToNv21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        yBuffer.get(nv21, 0, ySize)
        val uBytes = ByteArray(uvSize)
        val vBytes = ByteArray(uvSize)
        uBuffer.get(uBytes)
        vBuffer.get(vBytes)
        for (i in 0 until uvSize) {
            nv21[ySize + i * 2] = vBytes[i]
            nv21[ySize + i * 2 + 1] = uBytes[i]
        }
        return nv21
    }
}
