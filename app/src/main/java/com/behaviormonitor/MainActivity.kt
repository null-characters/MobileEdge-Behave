package com.behaviormonitor

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.presentation.viewmodel.MonitorViewModel
import com.behaviormonitor.ui.theme.BehaviorMonitorTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notifyGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        Log.d(TAG, "Camera: $cameraGranted, Notifications: $notifyGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BehaviorMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MonitorViewModel = viewModel()
                    MonitorScreen(
                        viewModel = viewModel,
                        onRequestPermissions = { requestPermissions() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 检查 Service 状态，恢复 UI
        val viewModel = ViewModelProvider(this)[MonitorViewModel::class.java]
        viewModel.checkServiceState()
        viewModel.setForegroundState(true)
    }

    override fun onPause() {
        super.onPause()
        val viewModel = ViewModelProvider(this)[MonitorViewModel::class.java]
        viewModel.setForegroundState(false)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "行为监测",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    uiState.isMonitoring -> Color(0x4C, 0xAF, 0x50)
                    else -> Color(0x9E, 0x9E, 0x9E)
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (uiState.isMonitoring) "监测中" else "未启动",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 当日统计
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "当日统计",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(title = "在岗时长", value = formatDuration(uiState.dailySummary?.presentDuration))
                    StatItem(title = "离岗时长", value = formatDuration(uiState.dailySummary?.absentDuration))
                    StatItem(title = "离岗次数", value = uiState.dailySummary?.absentCount?.toString() ?: "-")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 运行信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(title = "帧数", value = uiState.frameCount.toString())
            StatCard(title = "推理(ms)", value = uiState.avgInferenceTimeMs.toString())
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
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Camera Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "无图像", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = androidx.compose.ui.graphics.Color.Red,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (!uiState.isMonitoring) {
                    // 检查权限
                    val hasCamera = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasCamera) {
                        onRequestPermissions()
                        errorMessage = "请授予相机权限"
                        return@Button
                    }

                    // Android 13+ 需要通知权限
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        val hasNotify = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasNotify) {
                            onRequestPermissions()
                            // 不阻止启动，通知权限非必需
                        }
                    }

                    viewModel.startMonitoring(context)
                    errorMessage = null
                } else {
                    viewModel.stopMonitoring(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (uiState.isMonitoring) "停止监测" else "开始监测")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 导出 CSV 按钮
        OutlinedButton(
            onClick = { viewModel.exportCsv(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "导出当日数据 (CSV)")
        }
    }
}

private fun formatDuration(ms: Long?): String {
    if (ms == null) return "-"
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
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

@Composable
fun StatItem(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
