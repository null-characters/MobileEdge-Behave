package com.behaviormonitor.data.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * 帧分析器 - 将 ImageProxy 转换为 Bitmap
 */
object FrameAnalyzer {

    /**
     * 将 ImageProxy 转换为 Bitmap
     * @param imageProxy 相机帧数据
     * @return Bitmap 或 null（转换失败时）
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        
        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                // YUV 转 JPEG 再转 Bitmap
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                // U 和 V 交错排列
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val outputStream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, outputStream)
                val jpegBytes = outputStream.toByteArray()
                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            }
            else -> null
        }
    }

    /**
     * 将 Bitmap 缩放到指定尺寸
     * @param bitmap 原始 Bitmap
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @return 缩放后的 Bitmap
     */
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
