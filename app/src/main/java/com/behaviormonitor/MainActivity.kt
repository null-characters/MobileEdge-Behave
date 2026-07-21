package com.behaviormonitor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.behaviormonitor.data.camera.CameraManager
import com.behaviormonitor.data.tflite.DetectionResult
import com.behaviormonitor.data.tflite.PersonDetector
import com.behaviormonitor.ui.theme.BehaviorMonitorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var cameraManager: CameraManager
    private lateinit var personDetector: PersonDetector
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cameraManager = CameraManager(this)
        personDetector = PersonDetector(this)
        
        enableEdgeToEdge()
        setContent {
            BehaviorMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraTestScreen(
                        cameraManager = cameraManager,
                        personDetector = personDetector,
                        onRequestPermission = { requestCameraPermission() }
                    )
                }
            }
        }
    }
    
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted")
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraManager.release()
        personDetector.release()
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun CameraTestScreen(
    cameraManager: CameraManager,
    personDetector: PersonDetector,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isMonitoring by remember { mutableStateOf(false) }
    var frameCount by remember { mutableStateOf(0L) }
    var currentFps by remember { mutableStateOf(0f) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // TFLite 相关状态
    var isModelLoaded by remember { mutableStateOf(false) }
    var lastDetectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    var avgInferenceTime by remember { mutableStateOf(0f) }
    var inferenceCount by remember { mutableStateOf(0L) }
    var totalInferenceTime by remember { mutableStateOf(0L) }
    
    // FPS 计算
    var frameCountInWindow by remember { mutableStateOf(0L) }
    var windowStartTime by remember { mutableStateOf(0L) }
    
    // 初始化 TFLite 模型
    DisposableEffect(personDetector) {
        isModelLoaded = personDetector.initialize()
        if (!isModelLoaded) {
            errorMessage = "模型加载失败"
        }
        onDispose { }
    }
    
    // 监听帧流
    DisposableEffect(cameraManager, isModelLoaded) {
        val job = CoroutineScope(Dispatchers.Main).launch {
            cameraManager.frameFlow.collect { bitmap ->
                if (bitmap != null && isModelLoaded) {
                    frameCount++
                    frameCountInWindow++
                    
                    // 运行人形检测
                    val result = personDetector.detect(bitmap)
                    lastDetectionResult = result
                    
                    // 统计推理时间
                    if (result.inferenceTimeMs > 0) {
                        inferenceCount++
                        totalInferenceTime += result.inferenceTimeMs
                        avgInferenceTime = totalInferenceTime.toFloat() / inferenceCount
                    }
                    
                    // 绘制检测框（回收旧的 Bitmap）
                    val oldBitmap = lastBitmap
                    lastBitmap = drawDetections(bitmap, result)
                    oldBitmap?.recycle()
                    
                    // 计算FPS
                    val currentTime = System.currentTimeMillis()
                    if (windowStartTime == 0L) {
                        windowStartTime = currentTime
                    } else if (currentTime - windowStartTime >= 1000) {
                        currentFps = frameCountInWindow * 1000f / (currentTime - windowStartTime)
                        frameCountInWindow = 0
                        windowStartTime = currentTime
                    }
                    
                    errorMessage = null
                }
            }
        }
        onDispose { job.cancel() }
    }
    
    // 生命周期监听
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                cameraManager.stopCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "TFLite 人形检测测试",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    !isModelLoaded -> Color(0xFF, 0x98, 0x00)  // 橙色：模型未加载
                    isMonitoring -> Color(0x4C, 0xAF, 0x50)   // 绿色：监测中
                    else -> Color(0x9E, 0x9E, 0x9E)           // 灰色：未启动
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        !isModelLoaded -> "模型未加载"
                        isMonitoring -> "监测中"
                        else -> "未启动"
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 统计信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(title = "帧数", value = frameCount.toString())
            StatCard(title = "FPS", value = String.format("%.1f", currentFps))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // TFLite 统计
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(title = "检测人数", value = lastDetectionResult?.detections?.size?.toString() ?: "-")
            StatCard(title = "推理(ms)", value = String.format("%.1f", avgInferenceTime))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 预览图像
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (lastBitmap != null) {
                    Image(
                        bitmap = lastBitmap!!.asImageBitmap(),
                        contentDescription = "Camera Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "无图像",
                        color = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 错误信息
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = androidx.compose.ui.graphics.Color.Red,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 控制按钮
        Button(
            onClick = {
                if (!isModelLoaded) {
                    errorMessage = "模型未加载，无法启动"
                    return@Button
                }
                
                if (!isMonitoring) {
                    // 检查权限
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                        onRequestPermission()
                        errorMessage = "请授予相机权限"
                        return@Button
                    }
                    
                    // 启动相机
                    try {
                        cameraManager.startCamera(lifecycleOwner, fps = 5)
                        isMonitoring = true
                        frameCount = 0
                        frameCountInWindow = 0
                        windowStartTime = 0
                        currentFps = 0f
                        inferenceCount = 0
                        totalInferenceTime = 0
                        avgInferenceTime = 0f
                    } catch (e: Exception) {
                        errorMessage = "启动失败: ${e.message}"
                    }
                } else {
                    cameraManager.stopCamera()
                    isMonitoring = false
                    lastBitmap = null
                    lastDetectionResult = null
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isMonitoring) "停止监测" else "开始监测")
        }
    }
}

/**
 * 在 Bitmap 上绘制检测框
 */
fun drawDetections(bitmap: Bitmap, result: DetectionResult): Bitmap {
    val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(outputBitmap)
    val paint = Paint().apply {
        color = AndroidColor.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    val textPaint = Paint().apply {
        color = AndroidColor.GREEN
        textSize = 24f
        isAntiAlias = true
    }
    
    val width = bitmap.width.toFloat()
    val height = bitmap.height.toFloat()
    
    result.detections.forEach { detection ->
        val box = detection.boundingBox
        val left = box.xmin * width
        val top = box.ymin * height
        val right = box.xmax * width
        val bottom = box.ymax * height
        
        // 绘制框
        canvas.drawRect(left, top, right, bottom, paint)
        
        // 绘制置信度
        val text = String.format("%.0f%%", detection.confidence * 100)
        canvas.drawText(text, left, top - 8f, textPaint)
    }
    
    return outputBitmap
}

@Composable
fun StatCard(title: String, value: String) {
    Card(
        modifier = Modifier.size(100.dp, 70.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
