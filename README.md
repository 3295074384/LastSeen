# LastSeen

> 耳机丢了？LastSeen 帮你记录它最后一次出现的位置。

LastSeen 是一款 Android 工具应用，专为解决蓝牙耳机"丢失后找不到"的痛点而设计。当你的受追踪设备断开蓝牙连接时，LastSeen 会自动记录手机当前的精确位置，并在地图上展示所有历史断连点。

## 核心功能

- **断连自动打点** — 耳机断开蓝牙连接后 30 秒未重连，自动获取 GPS/WiFi/基站融合定位并记录
- **高精度定位** — 接入高德地图 SDK，支持 GPS + WiFi 热点三角定位，室内精度可达 5 米级
- **场景智能识别** — 自动识别常用 WiFi 网络（如家里、公司），断连记录直接标注"在[家里]附近断连"
- **地图可视化** — 全屏高德地图展示所有断连位置，点击标记查看详情
- **历史记录列表** — 底部抽屉式列表，支持滑动删除，点击跳转地图聚焦
- **即时强提醒** — 断连确认后震动通知，点击通知直达地图定位
- **多设备白名单** — 支持同时追踪多个蓝牙设备（如左耳 + 右耳独立地址）
- **90 天自动清理** — 过期记录自动清除，支持手动单条删除

## 技术架构

```
[耳机断连] → ACL_DISCONNECTED 广播
    ↓
TrackerForegroundService
    ↓
┌─ AMapTracker.resolve()         高德高精度定位 + 系统缓存兜底
├─ WifiSniffer.sniff()           WiFi SSID/BSSID 场景识别
├─ Room 写入 DisconnectRecord    持久化存储
└─ 强提醒通知 + Deep Link        震动提醒，点击直达地图
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 地图 | 高德 3D Map SDK 10.0.600（内置定位） |
| 数据库 | Room (SQLite ORM) + KSP |
| 导航 | Compose Navigation + Deep Link |
| 后台 | 前台服务 (foregroundServiceType=connectedDevice) |
| 目标平台 | Android 16 / HyperOS 3.0 / API 36 |

## 项目结构

```
com.tom_crayon.lastseen/
├── LastSeenApplication.kt          # Application: Room 单例、通知渠道、高德合规
├── MainActivity.kt                  # 单 Activity: Deep Link、白名单初始化
├── data/
│   ├── DisconnectRecord.kt          # Room Entity (12 字段)
│   ├── DisconnectDao.kt             # DAO: 增删查、90天清理
│   ├── LastSeenDatabase.kt          # Room Database
│   └── WhitelistManager.kt          # MAC 白名单 (SharedPreferences)
├── receiver/
│   └── BluetoothReceiver.kt         # 开机广播 + ACL 兜底接收器
├── service/
│   └── TrackerForegroundService.kt  # 前台服务: 广播监听、定位、存库、通知
├── tracker/
│   ├── AMapTracker.kt               # 双重定位: 缓存 + 高德高精度
│   ├── LocationResult.kt            # 统一定位结果数据类
│   ├── WifiSniffer.kt               # WiFi 场景嗅探
│   └── SceneLabelManager.kt         # BSSID→场所标签管理
└── ui/
    └── screen/
        ├── MainViewModel.kt         # ViewModel: StateFlow 驱动 UI
        └── MainScreen.kt            # 地图 + BottomSheet + 滑动删除
```

## 环境要求

- Android Studio (AGP 9.2.1+)
- JDK 17
- Android SDK Platform 36
- 高德地图 API Key（[申请地址](https://console.amap.com/dev/key/app)）

## 快速开始

### 1. 克隆项目

```bash
git clone <repo-url>
cd LastSeen
```

### 2. 配置高德 API Key

在项目根目录的 `local.properties` 中添加你的高德 Key：

```properties
AMAP_KEY=你的高德API_Key
```

> **重要**：高德 Key 必须绑定你本地 debug 签名的 SHA1 指纹。
> 获取方式：`./gradlew signingReport` → 找 `debug` 构建类型下的 SHA1。
> 在[高德控制台](https://console.amap.com/dev/key/app)创建应用时，包名填写 `com.tom_crayon.lastseen`，调试版安全码填入你的 SHA1。

### 3. 编译运行

```bash
./gradlew assembleDebug
```

或直接在 Android Studio 中点击 Run。

### 4. 权限授权

首次启动后，需要手动授予以下权限（HyperOS 需特别注意）：

- **定位** → 选择"始终允许"
- **蓝牙** → 允许
- **通知** → 允许
- **电池优化** → 设置为"无限制"（防止后台被杀）

### 5. 配置追踪设备

当前版本在 `MainActivity.onCreate()` 中硬编码了两组 OPPO Enco X3 的 MAC 地址作为白名单。如需追踪其他设备，修改以下代码：

```kotlin
WhitelistManager.add(this, "你的设备MAC地址")
```

> MAC 地址获取方式：手机蓝牙设置 → 已配对设备详情，或使用第三方蓝牙扫描工具。

## 工作原理

1. **开机自启** — `BluetoothReceiver` 监听 `BOOT_COMPLETED`，检测到白名单设备已连接时拉起前台服务
2. **后台监听** — 前台服务动态注册蓝牙 ACL 广播，持续监听设备连接/断开事件
3. **断连检测** — 收到 `ACL_DISCONNECTED` 后启动 30 秒缓冲计时器（防止短暂信号闪断误报）
4. **确认断连** — 30 秒后设备仍未重连，触发完整处理流程：
   - 高德高精度定位（GPS + WiFi + 基站融合，15 秒超时）
   - 系统缓存定位兜底（零延迟）
   - WiFi 场景识别（匹配已知场所标签）
   - 写入 Room 数据库
   - 发送震动强提醒通知
5. **空闲关机** — 所有白名单设备断开 5 分钟后，服务自动停止以节省电量

## 权限说明

| 权限 | 用途 |
|------|------|
| `BLUETOOTH_CONNECT` | 读取已配对设备、监听连接状态 |
| `ACCESS_FINE_LOCATION` | GPS 精确定位 |
| `ACCESS_BACKGROUND_LOCATION` | 后台定位（服务被唤醒时） |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Android 14+ 蓝牙前台服务类型 |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 |
| `NEARBY_WIFI_DEVICES` | Android 13+ WiFi 扫描 |
| `ACCESS_NETWORK_STATE` | 高德 SDK 网络状态检测 |

## 已知限制

- **仅限 Android** — 无 iOS 版本
- **单向追踪** — 仅记录断连位置，不支持实时追踪（需耳机端支持类似 Apple Find My 的众包网络）
- **HyperOS 适配** — 小米系统后台管控激进，需手动关闭电池优化并开启自启动
- **Hilt 未集成** — AGP 9.x 与 Hilt 2.56.2 存在 `BaseExtension` 兼容性问题，当前使用手动 DI

## 鸣谢
感谢狗老师提供的logo设计

## License

MIT
