# 办公室行为监测系统设计文档

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| V1.0 | 2026-07-17 | 初稿 |
| V1.1 | 2026-07-17 | 补充 V1.0 MVP 详细设计、硬件配置、俯视场景优化 |

---

## 1. 项目概述

### 1.1 背景

利用旧安卓手机作为边缘计算节点，搭建本地图像识别系统，用于办公室环境下的员工行为监测与分析。

### 1.2 目标

- **实用价值**：提供客观的工作时长统计，帮助团队了解时间分配
- **技术深度**：探索边缘 AI、模型优化、实时视频处理等技术
- **开发乐趣**：从简单功能开始，逐步迭代升级

### 1.3 约束条件

- **硬件**：红米 K60（骁龙 8+ Gen 1，12GB RAM，256GB 存储），需长期插电运行
- **隐私**：本地处理，不上传图像数据；只存储行为日志
- **功耗**：需优化采样策略，避免过热和过度耗电
- **摄像头**：俯视放置，覆盖整个工位范围
- **系统**：MIUI 后台保活策略激进，需专项处理

### 1.4 目标硬件评估

| 项目 | 规格 | 对项目的影响 |
|------|------|-------------|
| 处理器 | 骁龙 8+ Gen 1 | NPU 性能强，Hexagon DSP 支持良好 |
| RAM | 12GB | 充足，模型推理无压力 |
| 存储 | 256GB | 日志存储空间绰绰有余 |
| 系统 | MIUI | 后台杀进程激进，需重点处理保活 |

**结论**：硬件完全满足需求，骁龙 8+ 的 NNAPI 加速效果优秀，EfficientDet-Lite0 推理延迟预计 < 20ms。

---

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    旧安卓手机（边缘计算节点）                    │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │ 摄像头采集 │ → │ 图像预处理 │ → │ 模型推理  │              │
│  │ (CameraX) │    │ (缩放/裁剪)│    │ (TFLite) │              │
│  └──────────┘    └──────────┘    └──────────┘              │
│                                        ↓                     │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │ 本地存储  │ ← │ 行为日志  │ ← │ 结果后处理 │              │
│  │ (SQLite) │    │ (时间序列)│    │ (状态机)  │              │
│  └──────────┘    └──────────┘    └──────────┘              │
│         ↓                                    ↓              │
│  ┌──────────┐                         ┌──────────┐         │
│  │ Web仪表盘 │ ← ← ← ← ← ← ← ← ← ← ← │ HTTP API │         │
│  │ (可选)    │                         │ (端口服务)│         │
│  └──────────┘                         └──────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心模块

| 模块 | 职责 | 技术方案 |
|------|------|----------|
| 摄像头采集 | 持续获取视频帧 | CameraX + Foreground Service |
| 图像预处理 | 缩放、裁剪、格式转换 | Bitmap 操作 |
| 模型推理 | 人形检测、姿态估计、行为分类 | TensorFlow Lite + NNAPI |
| 结果后处理 | 状态机判断、时间窗口过滤 | 自定义状态机 |
| 行为日志 | 记录行为变化事件 | Room (SQLite) |
| HTTP API | 提供 REST 接口供外部访问 | Ktor / NanoHTTPD |
| Web 仪表盘 | 可视化展示统计数据 | Jetpack Compose + WebView |

---

## 3. 功能演进路线

### 3.1 版本规划

| 版本 | 功能 | 技术要点 | 预计工作量 | 优先级 |
|------|------|----------|------------|--------|
| **V1.0** | 在岗/离岗检测 | MobileNet SSD 人形检测 | 1-2 周 | P0 |
| **V1.5** | 在岗时长统计 + 离岗告警 | 状态机 + 时间窗口统计 | +3 天 | P0 |
| **V2.0** | 工作/摸鱼分类 | 姿态估计 + 行为分类 | 2-3 周 | P1 |
| **V2.5** | 多行为识别 | 多标签分类 + 时序分析 | +1 周 | P2 |
| **V3.0** | Web 仪表盘 + 数据导出 | HTTP Server + 图表组件 | 1 周 | P1 |

### 3.2 V1.0 MVP 详细设计

#### 3.2.1 功能范围

```
核心功能：
├── 人形检测（俯视视角，固定全画面）
├── 在岗/离岗状态判断（状态机）
├── 状态变化日志记录
├── 简单统计界面（当日数据）
│   ├── 在岗总时长
│   ├── 离岗总时长
│   ├── 离岗次数及时间点
├── CSV 数据导出
└── 后台持续运行（MIUI 保活）

不包含：
├── ROI 配置
├── 实时告警
├── Web 仪表盘
├── 多人支持
└── 每日报告推送（V1.5）
```

#### 3.2.2 架构概览

```
┌─────────────────────────────────────────┐
│              Android App                │
├─────────────────────────────────────────┤
│  CameraX ──→ ImageAnalyzer ──→ TFLite   │
│                              ↓           │
│  StateMachine ──→ StateEvent ──→ Room   │
│       ↓                                ↓  │
│  ViewModel ──→ Compose UI          CSV   │
└─────────────────────────────────────────┘
```

#### 3.2.3 数据流

```
视频帧(5fps)
    ↓
EfficientDet-Lite0 推理（NNAPI 加速）
    ↓
人形检测框 + 置信度
    ↓
状态机判断（连续5帧确认）
    ↓
状态变化事件
    ↓
StateEvent 写入 Room
    ↓
ViewModel 聚合统计
    ↓
UI 展示 / CSV 导出
```

#### 3.2.4 状态机设计

```
状态: UNKNOWN | PRESENT | ABSENT

参数（俯视场景优化）：
- 置信度阈值: 0.5（俯视场景检测框较小，置信度偏低）
- 连续帧确认: 5帧（约1秒，防止瞬时误检）
- 检测框面积阈值: > 5% 画面（排除远处路人干扰）

转换规则：
  UNKNOWN ──[检测到人×5帧]──→ PRESENT
  UNKNOWN ──[未检测到人×5帧]──→ ABSENT
  
  PRESENT ──[未检测到人×5帧]──→ ABSENT（记录离岗时间点）
  ABSENT ──[检测到人×5帧]──→ PRESENT（记录返回时间点）
```

#### 3.2.5 数据模型

```kotlin
// 状态变化事件
@Entity(tableName = "state_events")
data class StateEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,        // 时间戳（毫秒）
    val fromState: String,      // PRESENT/ABSENT/UNKNOWN
    val toState: String,        // ABSENT/PRESENT
    val confidence: Float       // 检测置信度
)

// 每日统计（运行时计算，不持久化）
data class DailySummary(
    val date: String,                   // YYYY-MM-DD
    val totalPresentMs: Long,           // 在岗总时长（毫秒）
    val totalAbsentMs: Long,            // 离岗总时长（毫秒）
    val absenceEvents: List<AbsenceRecord>  // 离岗记录列表
)

data class AbsenceRecord(
    val startTime: Long,    // 离岗开始时间
    val endTime: Long,      // 返回时间
    val durationMs: Long    // 离岗时长
)
```

#### 3.2.6 MIUI 后台保活策略

```
必须实现：
├── Foreground Service + 常驻通知（显示"监测中"状态）
├── WakeLock（屏幕关闭时保持 CPU 运行）
└── 服务守护（异常退出后自动重启）

引导用户操作：
├── 关闭电池优化
├── 开启自启动权限
├── 设置"无限制"后台运行
└── 锁定最近任务（防止滑动清理）
```

#### 3.2.7 CSV 导出格式

```csv
timestamp,from_state,to_state,confidence
2026-07-17T09:00:15,UNKNOWN,PRESENT,0.82
2026-07-17T10:30:22,PRESENT,ABSENT,0.15
2026-07-17T10:45:38,ABSENT,PRESENT,0.76
2026-07-17T12:00:00,PRESENT,ABSENT,0.12
```

#### 3.2.8 俯视场景特点

**优势**：
- 观察范围更广，覆盖整个工位
- 减少遮挡问题
- 人形检测更稳定（头肩特征明显）

**挑战**：
- 姿态估计精度下降（俯视角度看不清四肢细节）
- V2.0 的"工作/摸鱼"分类需要重新设计特征

**应对策略**：
- V1.0 在岗/离岗检测：俯视完全满足需求
- V2.0 分类：基于头部位置 + 肩膀朝向 + 相对工位的静止/移动状态

---

## 4. 技术选型

### 4.1 核心技术栈

| 模块 | 技术方案 | 版本要求 | 理由 |
|------|----------|----------|------|
| 开发语言 | Kotlin | 1.9+ | Android 官方推荐 |
| UI 框架 | Jetpack Compose | 1.5+ | 现代化声明式 UI |
| 摄像头 | CameraX | 1.3+ | 生命周期感知，后台服务友好 |
| 模型推理 | TensorFlow Lite | 2.14+ | 支持 GPU/NPU 加速 |
| 数据存储 | Room | 2.6+ | Jetpack 组件，类型安全 |
| 后台服务 | Foreground Service | - | 保活必备 |
| HTTP 服务 | Ktor / NanoHTTPD | - | 轻量级嵌入式服务 |

### 4.2 模型选型

| 功能 | 模型 | 大小 | 延迟 | 精度 |
|------|------|------|------|------|
| 人形检测 | EfficientDet-Lite0 | 4.4MB | ~30ms | mAP 0.34 |
| 人形检测 (备选) | MobileNet SSD v2 | 6.7MB | ~25ms | mAP 0.22 |
| 姿态估计 | MoveNet Lightning | 8.5MB | ~15ms | 高 |
| 姿态估计 (备选) | MediaPipe BlazePose | 10MB | ~20ms | 高 |

### 4.3 性能优化策略

| 策略 | 说明 |
|------|------|
| 智能采样 | 静止时降低帧率（5fps），检测到变化时提高帧率（15fps） |
| 模型量化 | 使用 INT8 量化模型，减少计算量和内存占用 |
| 硬件加速 | 启用 NNAPI，利用 GPU/NPU 加速推理 |
| 区域裁剪 | 只处理 ROI 区域，减少计算量 |
| 批处理 | 低优先级任务合并处理 |

---

## 5. 数据模型

### 5.1 状态变化事件表（V1.0）

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

### 5.2 每日统计（运行时计算）

```kotlin
// 不持久化，从 StateEvent 实时聚合计算
data class DailySummary(
    val date: String,                   // YYYY-MM-DD
    val totalPresentMs: Long,           // 在岗总时长（毫秒）
    val totalAbsentMs: Long,            // 离岗总时长（毫秒）
    val absenceEvents: List<AbsenceRecord>  // 离岗记录列表
)

data class AbsenceRecord(
    val startTime: Long,    // 离岗开始时间
    val endTime: Long,      // 返回时间
    val durationMs: Long    // 离岗时长
)
```

### 5.3 行为日志表（V2.0 预留）

```kotlin
@Entity(tableName = "behavior_logs")
data class BehaviorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,           // 时间戳
    val behaviorType: String,      // 行为类型: PRESENT, ABSENT, WORKING, SLACKING
    val confidence: Float,         // 置信度
    val duration: Long = 0,        // 持续时间（毫秒）
    val metadata: String? = null   // 扩展字段（JSON）
)
```

### 5.4 统计汇总表（V3.0 预留）

```kotlin
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey val date: String,  // 日期: YYYY-MM-DD
    val totalPresentTime: Long,    // 在岗总时长
    val totalAbsentTime: Long,     // 离岗总时长
    val totalWorkingTime: Long,    // 工作总时长
    val totalSlackingTime: Long,   // 摸鱼总时长
    val presenceCount: Int,        // 在岗次数
    val absenceCount: Int          // 离岗次数
)
```

---

## 6. 核心挑战与解决方案

| 挑战 | 解决方案 | 优先级 |
|------|----------|--------|
| 功耗控制 | 智能采样 + 模型量化 + 硬件加速 | P0 |
| 误检率 | 置信度阈值 + 时间窗口过滤 + 连续帧确认 | P0 |
| 光照变化 | 自动曝光 + 夜间模式（红外补光或降低阈值） | P1 |
| 多角度适应 | 摄像头角度校准 + ROI 定义 | P1 |
| 后台保活 | Foreground Service + 电池优化白名单 | P0 |
| 存储管理 | 日志自动清理（保留最近 30 天） | P2 |

---

## 7. 隐私与伦理

### 7.1 隐私保护措施

| 措施 | 说明 |
|------|------|
| 本地处理 | 所有图像处理在本地完成，不上传云端 |
| 数据最小化 | 只存储行为日志，不存储原始图像 |
| 匿名化 | 不记录具体人员身份，只记录行为统计 |
| 数据加密 | 敏感数据加密存储 |
| 访问控制 | App 启动需密码验证 |

### 7.2 使用建议

- 明确告知被监控者，获得同意
- 数据仅用于生产力分析，不用于惩罚
- 定期清理历史数据
- 根据当地劳动法规调整实施方案

---

## 8. 验收标准

### 8.1 V1.0 MVP 验收标准

**功能验收**：
- [ ] App 能够持续运行，后台不退出（MIUI 保活）
- [ ] 人形检测准确率 > 90%（俯视场景）
- [ ] 状态变化延迟 < 3 秒（连续5帧确认）
- [ ] 日志正确记录状态变化时间点
- [ ] 统计界面显示当日数据（在岗时长、离岗时长、离岗次数）
- [ ] CSV 导出功能正常

**性能验收**：
- [ ] 模型推理延迟 < 30ms（NNAPI 加速）
- [ ] 功耗 < 5W（插电运行温度正常）
- [ ] 内存占用 < 300MB

**稳定性验收**：
- [ ] 连续运行 8 小时无崩溃
- [ ] 屏幕关闭后服务继续运行
- [ ] 异常退出后自动重启

### 8.2 V1.5 验收标准

- [ ] 在岗时长统计误差 < 5%
- [ ] 每日报告推送功能
- [ ] 统计数据可视化展示优化

### 8.3 V2.0 验收标准

- [ ] 工作/摸鱼分类准确率 > 80%
- [ ] 支持 5 种以上行为类型识别
- [ ] Web 仪表盘正常访问

---

## 9. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 模型精度不足 | 中 | 高 | 选择成熟模型，后期可训练定制模型 |
| 后台被系统杀死 | 高 | 高 | Foreground Service + 引导用户加白名单 |
| 过热降频 | 中 | 中 | 智能采样 + 散热措施 |
| 隐私合规问题 | 低 | 高 | 遵循最小化原则，明确告知 |

---

## 10. 下一步行动

### 10.1 V1.0 MVP 开发计划

1. **环境搭建**：创建 Android 项目，配置 CameraX 和 TFLite 依赖
2. **模型集成**：下载 EfficientDet-Lite0 模型，测试 NNAPI 推理性能
3. **核心功能开发**：
   - CameraX 视频帧采集
   - TFLite 人形检测
   - 状态机实现
   - Room 数据存储
4. **UI 开发**：统计界面 + CSV 导出
5. **保活优化**：Foreground Service + MIUI 引导
6. **测试验证**：功能测试 + 稳定性测试

### 10.2 技术验证优先级

| 优先级 | 验证项 | 目标 |
|--------|--------|------|
| P0 | TFLite + NNAPI 推理延迟 | < 30ms |
| P0 | MIUI 后台保活效果 | 8小时不退出 |
| P1 | 俯视场景检测准确率 | > 90% |
| P1 | 温度与功耗表现 | < 5W，< 45°C |

---

## 附录

### A. 参考资料

- [TensorFlow Lite 官方文档](https://www.tensorflow.org/lite)
- [CameraX 官方指南](https://developer.android.com/training/camerax)
- [EfficientDet-Lite 模型下载](https://www.tensorflow.org/lite/models/object_detection/overview)
- [MoveNet 姿态估计](https://www.tensorflow.org/hub/tutorials/movenet)

### B. 硬件要求

**目标设备：红米 K60**
- 处理器：骁龙 8+ Gen 1（支持 NNAPI 硬件加速）
- RAM：12GB
- 存储：256GB
- 系统：MIUI 14+（需配置后台保活）
- 摄像头：后置摄像头可用，俯视放置

**最低要求（其他设备）**
- Android 6.0+ (API 23+)
- 至少 2GB RAM
- 后置摄像头可用
- 支持长期插电运行

### C. 俯视场景配置建议

- 摄像头高度：建议 1.5-2 米，覆盖整个工位
- 检测区域：固定全画面（V1.0），后续支持 ROI 配置
- 光照条件：自然光或办公室照明，避免逆光