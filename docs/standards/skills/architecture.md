# 办公室行为监测系统架构速查

## META

| 属性 | 值 |
|------|-----|
| 项目 | 办公室行为监测 Android 应用 |
| 平台 | Android 15 (API 35)，红米 K60 |
| 架构 | MVVM + Clean Architecture |
| 开发语言 | Kotlin |
| 更新日期 | 2026-07-21 |

---

## ARCHITECTURE

### 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Compose UI  │  │  ViewModel  │  │  UiState    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                     Domain Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ UseCase     │  │ StateMachine│  │ Repository  │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                      Data Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │    Room     │  │  TFLite     │  │  CameraX    │         │
│  │  (SQLite)   │  │  (模型推理)  │  │ (视频采集)  │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 模块职责

| 层级 | 模块 | 职责 | 技术方案 |
|------|------|------|----------|
| Presentation | Compose UI | 界面渲染、用户交互 | Jetpack Compose |
| Presentation | ViewModel | 状态管理、UI 逻辑 | Jetpack ViewModel |
| Presentation | UiState | UI 状态数据类 | Kotlin data class |
| Domain | UseCase | 业务逻辑封装 | Kotlin |
| Domain | StateMachine | 在岗/离岗状态判断 | 自定义状态机 |
| Domain | Repository | 数据访问抽象 | Interface |
| Data | Room | 本地数据库存储 | Room (SQLite) |
| Data | TFLite | AI 模型推理 | TensorFlow Lite + NNAPI |
| Data | CameraX | 摄像头视频采集 | CameraX |

---

## MODULE_INDEX

### 数据层 (Data)

| 模块 | 职责 | 关键接口 |
|------|------|----------|
| CameraX | 视频帧采集 | `ImageAnalysis.Analyzer` |
| TFLite | 人形检测推理 | `Interpreter.run()` |
| Room | 状态事件持久化 | `StateEventDao` |

### 领域层 (Domain)

| 模块 | 职责 | 关键接口 |
|------|------|----------|
| StateMachine | 状态判断 | `updateState(detection: Detection): State` |
| DetectionRepository | 数据访问 | `saveEvent(event: StateEvent)` |

### 表现层 (Presentation)

| 模块 | 职责 | 关键接口 |
|------|------|----------|
| MonitorViewModel | 状态管理 | `uiState: StateFlow<MonitorUiState>` |
| MonitorScreen | UI 渲染 | `@Composable fun MonitorScreen()` |

---

## DATA_FLOW

### 核心数据流

```
CameraX (5fps)
    ↓
ImageAnalyzer.analyze(imageProxy)
    ↓
TFLite 推理 (EfficientDet-Lite0)
    ↓
DetectionResult (bbox, confidence)
    ↓
StateMachine.updateState()
    ↓
StateEvent (状态变化)
    ↓
Room 数据库存储
    ↓
ViewModel 聚合统计
    ↓
Compose UI 展示
```

### 状态机设计

```
状态: UNKNOWN | PRESENT | ABSENT

参数：
- 置信度阈值: 0.5
- 连续帧确认: 5帧
- 检测框面积阈值: > 5% 画面

转换规则：
  UNKNOWN ──[检测到人×5帧]──→ PRESENT
  UNKNOWN ──[未检测到人×5帧]──→ ABSENT
  PRESENT ──[未检测到人×5帧]──→ ABSENT
  ABSENT ──[检测到人×5帧]──→ PRESENT
```

---

## DATA_MODEL

### 状态事件表

```kotlin
@Entity(tableName = "state_events")
data class StateEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,        // 时间戳（毫秒）
    val fromState: String,      // PRESENT/ABSENT/UNKNOWN
    val toState: String,        // ABSENT/PRESENT
    val confidence: Float       // 检测置信度
)
```

### 每日统计（运行时计算）

```kotlin
data class DailySummary(
    val date: String,
    val totalPresentMs: Long,
    val totalAbsentMs: Long,
    val absenceEvents: List<AbsenceRecord>
)
```

---

## CONSTRAINTS

### 硬约束清单

| 约束类型 | 规则 | 说明 |
|----------|------|------|
| 后台保活 | Foreground Service + 常驻通知 | MIUI 后台管理激进 |
| 隐私保护 | 本地处理，不存储原始图像 | 只存储行为日志 |
| 功耗控制 | 智能采样 + 模型量化 | 长期插电运行 |
| 状态判断 | 连续 5 帧确认 | 防止瞬时误检 |
| 推理加速 | NNAPI 硬件加速 | 利用 GPU/NPU |

### MIUI 保活要求

| 措施 | 说明 |
|------|------|
| Foreground Service | 常驻通知栏，显示"监测中"状态 |
| WakeLock | 屏幕关闭时保持 CPU 运行 |
| 电池优化豁免 | 引导用户关闭电池优化 |
| 自启动权限 | 引导用户开启自启动 |
| 后台无限制 | 设置"无限制"后台运行 |

---

## PERFORMANCE

### 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 模型推理延迟 | < 30ms | NNAPI 加速 |
| 状态变化延迟 | < 3s | 连续 5 帧确认 |
| 内存占用 | < 300MB | 长期运行 |
| 功耗 | < 5W | 插电运行 |
| 温度 | < 45°C | 智能采样 |

### 帧率策略

| 场景 | 帧率 | 说明 |
|------|------|------|
| 正常监测 | 5 fps | 默认帧率 |
| 状态变化 | 15 fps | 检测到变化时提高 |
| 长时间静止 | 1 fps | 休眠模式 |

---

## TECH_STACK

### 核心技术栈

| 模块 | 技术方案 | 版本要求 |
|------|----------|----------|
| 开发语言 | Kotlin | 1.9+ |
| UI 框架 | Jetpack Compose | 1.5+ |
| 摄像头 | CameraX | 1.3+ |
| 模型推理 | TensorFlow Lite | 2.14+ |
| 数据存储 | Room | 2.6+ |
| 后台服务 | Foreground Service | - |

### AI 模型

| 功能 | 模型 | 大小 | 延迟 |
|------|------|------|------|
| 人形检测 | EfficientDet-Lite0 | 4.4MB | ~30ms |
| 人形检测 (备选) | MobileNet SSD v2 | 6.7MB | ~25ms |
| 姿态估计 (V2.0) | MoveNet Lightning | 8.5MB | ~15ms |

---

## MODIFY_GUIDE

### 场景A：新增功能模块

| 步骤 | 操作 | 目标路径 |
|------|------|----------|
| A1 | 创建数据模型 | `data/local/entity/` |
| A2 | 创建 Repository | `data/repository/` |
| A3 | 创建 UseCase | `domain/usecase/` |
| A4 | 创建 ViewModel | `presentation/viewmodel/` |
| A5 | 创建 UI Screen | `presentation/ui/` |

### 场景B：修改状态机逻辑

| 模块 | 文件位置 | 修改内容 |
|------|----------|----------|
| 状态机 | `domain/statemachine/` | 状态转换规则 |
| 参数配置 | `domain/config/` | 阈值参数 |

### 场景C：更换检测模型

| 步骤 | 操作 | 说明 |
|------|------|------|
| C1 | 替换 .tflite 文件 | `assets/` 目录 |
| C2 | 修改模型配置 | 输入尺寸、标签映射 |
| C3 | 调整后处理逻辑 | 解析检测结果 |

---

## QUICK_REF

### 常用命令

```bash
# ADB 调试
adb logcat | grep "BehaviorMonitor"

# 安装 APK
adb install app-debug.apk

# 查看设备温度
adb shell dumpsys battery | grep temperature

# 导出数据库
adb pull /data/data/<package>/databases/ ./
```

### 文件定位速查

| 需求 | 位置 |
|------|------|
| 修改状态机参数 | `domain/statemachine/` |
| 修改 UI 界面 | `presentation/ui/` |
| 修改数据模型 | `data/local/entity/` |
| 更换检测模型 | `assets/*.tflite` |
| 配置后台服务 | `service/MonitorService.kt` |
