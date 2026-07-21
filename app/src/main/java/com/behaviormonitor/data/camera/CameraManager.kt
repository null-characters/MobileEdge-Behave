package com.behaviormonitor.data.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相机管理器 - 使用 CameraX 进行视频帧采集
 */
class CameraManager(private val context: Context) {

    private val TAG = "CameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null

    private val _frameFlow = MutableStateFlow<Bitmap?>(null)
    val frameFlow: StateFlow<Bitmap?> = _frameFlow.asStateFlow()

    private var isRunning = false
    private var lastFrameTime = 0L
    private var frameIntervalMs: Long = 200L  // 默认 5fps = 200ms

    /**
     * 启动相机
     * @param lifecycleOwner 生命周期所有者
     * @param fps 帧率（默认 5fps）
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, fps: Int = 5) {
        if (isRunning) return

        frameIntervalMs = 1000L / fps
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 配置图像分析
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            processFrame(imageProxy)
                        }
                    }

                // 选择前置摄像头（俯视场景）
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // 解绑所有用例
                cameraProvider?.unbindAll()

                // 绑定到生命周期
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )

                isRunning = true
                Log.d(TAG, "Camera started with LifecycleOwner")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                _frameFlow.value = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    /**
     * 启动相机（无 LifecycleOwner 版本，用于 Service）
     * 创建自定义 LifecycleOwner，手动管理生命周期
     * @param fps 帧率（默认 5fps）
     */
    fun startCameraWithoutLifecycle(fps: Int = 5) {
        if (isRunning) return

        frameIntervalMs = 1000L / fps
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 创建自定义 LifecycleOwner 并设为 RESUMED
        serviceLifecycleOwner = ServiceLifecycleOwner()
        serviceLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        serviceLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
        serviceLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 配置图像分析
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            processFrame(imageProxy)
                        }
                    }

                // 选择前置摄像头
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // 解绑所有用例
                cameraProvider?.unbindAll()

                // 绑定到自定义 LifecycleOwner
                cameraProvider?.bindToLifecycle(
                    serviceLifecycleOwner!!,
                    cameraSelector,
                    imageAnalysis
                )

                isRunning = true
                Log.d(TAG, "Camera started without LifecycleOwner (service mode)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera in service mode", e)
                _frameFlow.value = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 自定义 LifecycleOwner，用于 Service 场景
     * 手动控制生命周期状态，始终保持 RESUMED
     */
    private class ServiceLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = lifecycleRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }

    /**
     * 停止相机
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        // 销毁 Service LifecycleOwner
        serviceLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceLifecycleOwner = null
        cameraProvider = null
        imageAnalysis = null
        cameraExecutor = null
        isRunning = false
        _frameFlow.value = null
    }

    /**
     * 处理帧（带帧率控制）
     */
    private fun processFrame(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // 帧率控制：跳过过早的帧
        if (currentTime - lastFrameTime < frameIntervalMs) {
            imageProxy.close()
            return
        }
        
        lastFrameTime = currentTime
        
        try {
            // 不 recycle 旧 Bitmap，由消费者负责
            val bitmap = FrameAnalyzer.imageProxyToBitmap(imageProxy)
            _frameFlow.value = bitmap
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopCamera()
    }
}
