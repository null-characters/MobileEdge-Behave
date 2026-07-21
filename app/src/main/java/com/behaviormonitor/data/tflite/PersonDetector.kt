package com.behaviormonitor.data.tflite

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * EfficientDet-Lite0 人形检测器
 * 使用 NNAPI 加速推理
 */
class PersonDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "PersonDetector"
        private const val MODEL_FILE = "efficientdet-lite0.tflite"
        private const val INPUT_SIZE = 320
        private const val NUM_DETECTIONS = 25  // EfficientDet-Lite0 输出 25 个检测
    }
    
    private var interpreter: Interpreter? = null
    private var nnapiDelegate: NnApiDelegate? = null
    private var isInitialized = false
    private var isWarmedUp = false
    private val inferenceLock = Object()  // 保护推理/释放的互斥锁
    
    // 复用输出缓冲区，避免重复创建
    private lateinit var outputBoxes: Array<Array<FloatArray>>
    private lateinit var outputClasses: Array<FloatArray>
    private lateinit var outputScores: Array<FloatArray>
    private lateinit var numDetections: FloatArray
    private lateinit var outputs: Map<Int, Any>
    
    // 复用输入缓冲区，避免每次推理创建新数组
    private lateinit var inputBuffer: Array<Array<Array<ByteArray>>>
    
    /**
     * 初始化模型
     * @return 是否初始化成功
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        try {
            // 加载模型文件
            val modelBuffer = loadModelFile()
            
            // 创建解释器（只使用 XNNPACK，避免 delegate 切换开销）
            val options = Interpreter.Options()
                .setUseXNNPACK(true)
                .setNumThreads(2)  // T-15c: 降至 2 线程降低 CPU 占用
            
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            
            // 初始化输出缓冲区（复用）
            outputBoxes = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
            outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
            outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
            numDetections = FloatArray(1)
            outputs = mapOf(
                0 to outputBoxes,
                1 to outputClasses,
                2 to outputScores,
                3 to numDetections
            )
            
            // 初始化输入缓冲区（复用）
            inputBuffer = Array(1) {
                Array(INPUT_SIZE) {
                    Array(INPUT_SIZE) {
                        ByteArray(3)
                    }
                }
            }
            
            // 预热：运行一次推理以编译模型
            warmUp()
            
            Log.d(TAG, "Model initialized successfully with XNNPACK (4 threads)")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model", e)
            return false
        }
    }
    
    /**
     * 检测图片中的人
     * @param bitmap 输入图片（会自动缩放到 320x320）
     * @return 检测结果列表
     */
    fun detect(bitmap: Bitmap): DetectionResult {
        synchronized(inferenceLock) {
            if (!isInitialized || interpreter == null) {
                Log.w(TAG, "Detector not initialized or already released")
                return DetectionResult(emptyList(), 0L)
            }
        }
        
        var resizedBitmap: Bitmap? = null
        try {
            // 1. 预处理：缩放到 320x320
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            
            // 2. 转换为输入张量（UINT8 格式，复用缓冲区）
            fillInputBuffer(resizedBitmap)
            
            // 3. 运行推理（加锁防止释放时竞态）
            val startTime = System.currentTimeMillis()
            synchronized(inferenceLock) {
                interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
                    ?: return DetectionResult(emptyList(), 0L)
            }
            val inferenceTime = System.currentTimeMillis() - startTime
            
            // 4. 解析结果
            val detections = mutableListOf<Detection>()
            val numValidDetections = numDetections[0].toInt()
            
            for (i in 0 until minOf(numValidDetections, NUM_DETECTIONS)) {
                val score = outputScores[0][i]
                val classId = outputClasses[0][i].toInt()
                
                // 只保留人（类别 0）且置信度 > 0.5
                if (classId == 0 && score > 0.5f) {
                    val box = outputBoxes[0][i]  // [ymin, xmin, ymax, xmax]
                    
                    // 计算面积
                    val area = (box[2] - box[0]) * (box[3] - box[1])
                    
                    // 只保留面积 > 5% 的检测框
                    if (area > 0.05f) {
                        detections.add(
                            Detection(
                                boundingBox = BoundingBox(
                                    ymin = box[0],
                                    xmin = box[1],
                                    ymax = box[2],
                                    xmax = box[3]
                                ),
                                confidence = score,
                                area = area
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Detection completed: ${detections.size} persons, inference=${inferenceTime}ms")
            
            return DetectionResult(detections, inferenceTime)
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            return DetectionResult(emptyList(), 0L)
        } finally {
            // 回收临时 Bitmap
            resizedBitmap?.recycle()
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        synchronized(inferenceLock) {
            interpreter?.close()
            nnapiDelegate?.close()
            interpreter = null
            nnapiDelegate = null
            isInitialized = false
            Log.d(TAG, "Detector released")
        }
    }
    
    /**
     * 加载模型文件
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /** T-15d: 复用 pixels 数组避免每帧分配 */
    private val reusablePixels = IntArray(INPUT_SIZE * INPUT_SIZE)

    /**
     * 将 Bitmap 填充到输入缓冲区（UINT8 格式，复用缓冲区）
     * T-15d: 复用 pixels 数组，减少 GC 压力
     */
    private fun fillInputBuffer(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        bitmap.getPixels(reusablePixels, 0, width, 0, 0, width, height)

        for (i in 0 until width * height) {
            val pixel = reusablePixels[i]
            inputBuffer[0][i / width][i % width][0] = ((pixel shr 16) and 0xFF).toByte()  // R
            inputBuffer[0][i / width][i % width][1] = ((pixel shr 8) and 0xFF).toByte()   // G
            inputBuffer[0][i / width][i % width][2] = (pixel and 0xFF).toByte()           // B
        }
    }
    
    /**
     * 预热模型：运行一次推理以编译 NNAPI 模型
     */
    private fun warmUp() {
        try {
            // 直接使用已初始化的 inputBuffer（已经是空白值）
            val startTime = System.currentTimeMillis()
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            val warmUpTime = System.currentTimeMillis() - startTime
            
            isWarmedUp = true
            Log.d(TAG, "Warm-up completed in ${warmUpTime}ms")
            
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up failed (this is usually ok)", e)
        }
    }
}

/**
 * 检测结果
 */
data class DetectionResult(
    val detections: List<Detection>,
    val inferenceTimeMs: Long
)

/**
 * 单个检测
 */
data class Detection(
    val boundingBox: BoundingBox,
    val confidence: Float,
    val area: Float
)

/**
 * 边界框（归一化坐标 0-1）
 */
data class BoundingBox(
    val ymin: Float,
    val xmin: Float,
    val ymax: Float,
    val xmax: Float
)
