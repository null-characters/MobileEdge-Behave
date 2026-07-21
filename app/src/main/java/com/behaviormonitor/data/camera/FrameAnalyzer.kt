package com.behaviormonitor.data.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap.Config
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * 帧分析器 - 将 ImageProxy 转换为 Bitmap
 */
object FrameAnalyzer {

    /** 复用 NV21 缓冲区，避免每帧分配 */
    private var reusableNv21: ByteArray? = null

    /**
     * 将 ImageProxy 转换为 Bitmap
     * 优化：复用 NV21 缓冲区，降低 JPEG 质量减少内存
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()
                val nv21Size = ySize + uSize + vSize

                // 复用 NV21 缓冲区
                val nv21 = reusableNv21?.let {
                    if (it.size >= nv21Size) it else ByteArray(nv21Size)
                } ?: ByteArray(nv21Size)
                reusableNv21 = nv21

                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val outputStream = ByteArrayOutputStream()
                // 质量 70 足够检测，大幅减少 JPEG 字节数和解码内存
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 70, outputStream)
                val jpegBytes = outputStream.toByteArray()
                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            }
            else -> null
        }
    }

    /**
     * 将 Bitmap 缩放到指定尺寸
     */
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
