# APPCanvasFaker

> Canvas 指纹随机化伪装模块 —— 基于 libxposed 的 Android Canvas API Hook 方案，界面复刻 KernelSU Manager 原版设计。

| | |
|---|---|
| **应用名** | APPCanvasFaker |
| **包名** | `dev.nikko.appcanvasfaker` |
| **当前版本** | v0.6.0-dev (versionCode 33) |
| **运行形态** | LSPosed / libxposed 模块（后续计划适配 Root、Zygisk 版） |
| **语言/框架** | Kotlin · Jetpack Compose · Navigation3 · Miuix + Material3 双皮肤 |
| **Hook 框架** | libxposed API 102（[api](https://github.com/libxposed/api) / [service](https://github.com/libxposed/service)） |
| **许可证** | GPL-3.0（详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)） |

---

## 这是什么

Canvas 指纹是网页/应用通过 `<canvas>` 绘制后读取像素计算哈希来唯一标识设备的主流手段之一。**APPCanvasFaker** 在目标应用进程内 Hook `android.graphics.Bitmap` 的像素读取与编码路径，用与坐标绑定的确定性偏置噪声替换真实渲染结果，使应用每次采集到的 Canvas 指纹都偏离真实值且保持稳定一致 —— 既防追踪，又不会因"每次都变"而触发风控怀疑。

配套的 21 项指纹采集矩阵（见「哈希测试」页）可让用户直观验证 Hook 前后的指纹差异。

## 核心特性

- 🎯 **精准 Hook**：拦截 `Bitmap.getPixels` / `copyPixelsToBuffer` / `compress(PNG/JPEG)` 三大读取路径（A1/A3/A4/A4b），覆盖绝大多数 Canvas 指纹算法的取值方式
- 🕳️ **逃逸口封堵（v0.6.0）**：`getPixel` 单点读取（A2）、Paint 文本度量族（E1）、GPU `glReadPixels` 直读（D1，默认关）三条新链，均有独立开关
- 🔒 **确定性伪装**：噪声由 `SplitMix64(seed, x, y)` 按位图绝对坐标生成，同 seed 下任意路径、任意区域读取结果一致；通道抖动带非零偏置，抵抗零均值噪声统计检测
- #️⃣ **16 位折叠哈希**：SHA-256 四块 XOR 折叠为 16 位短哈希，与配套测试应用 canvas-fingerprint-scanner 同方法，可直接比对
- 🧪 **内置哈希测试页**：A/B/C/D/E/F 六组 21 个采集面（像素读取、离屏渲染、截图抓取、GPU 直读、元信息、参考基准），一键验证 Hook 效果
- 🎨 **双皮肤**：Miuix 风格与 Material You 风格完整适配，跟随系统深色模式、动态取色
- 📋 **日志审计**：记录 Hook 事件与随机化操作，支持搜索/筛选/清空
- 🛡️ **安全收敛**：Provider UID 校验、配置最小下发、`allowBackup=false`、release 构建日志静默

## 工作原理（一图流）

```
┌─ LSPosed 加载模块 ────────────────────────────────┐
│ LibXposedInit.onPackageLoaded(pkg)                │
│   ├─ 读取配置（JSON 规则表: 包名→seed/开关）        │
│   ├─ 未启用 → 直接返回                             │
│   └─ 已启用 → BitmapHooks.install()                │
│        ├─ hook Bitmap.getPixels          (A1)      │
│        ├─ hook Bitmap.copyPixelsToBuffer (A3)     │
│        ├─ hook Bitmap.compress           (A4/A4b) │
│        ├─ hook Bitmap.getPixel   │
│        ├─ hook Paint 文本度量族 ×12   │
│        └─ hook GLES20.glReadPixels       (D1,   │
│                 默认关)                            │
│              ↓ proceed 后                          │
│        FingerprintEngine.applyPixels()             │
│        = 原始像素 ⊕ SplitMix64(seed,x,y) 偏置噪声   │
│              ↓                                     │
│        StatsProvider 回写统计（UID 校验）           │
└───────────────────────────────────────────────────┘
```

## 构建

```bash
# 环境要求：JDK 17+、Android SDK（compileSdk 见 gradle/libs.versions.toml）、Android Studio 或命令行
git clone https://github.com/Neekolor/APPCanvasFaker.git
cd APPCanvasFaker
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/APPCanvasFaker_<version>_debug.apk
```

安装后在 LSPosed 中启用模块，作用域勾选目标应用（或全选），重启目标应用生效。

## 目录结构

```
app/src/main/java/dev/nikko/appcanvasfaker/
├── AppCanvasFakerApplication.kt   # 应用入口，xposedService 绑定监听
├── core/
│   ├── ConfigRepository.kt        # 配置读写中心（JSON 规则表、统计、日志）
│   ├── FingerprintEngine.kt       # 伪装算法核心（SplitMix64 确定性偏置噪声）
│   ├── Models.kt                  # 领域模型（AppRule/InstalledApp/ModuleSnapshot…）
│   └── StandardCanvas.kt*         # 标准画布定义
├── data/repository/               # SettingsRepository 及实现（DataStore/JSON）
├── hook/
│   ├── LibXposedInit.kt           # xposed 入口：包加载/就绪/attach 三级兜底
│   ├── BitmapHooks.kt             # A1/A3/A4+A4b 三路 Hook 实现（递归保护、节流）
│   ├── StatsProvider.kt           # 跨进程统计回写 Provider（UID 鉴权）
│   └── HashUtils.kt*              # SHA-256 / foldHash16
├── scanner/                       # 内置指纹采集引擎（源自配套测试应用）
│   ├── core/StandardCanvas.kt
│   ├── fingerprint/               # 21 项采集面定义与实现（A/B/C/D/E/F 组）
│   │   ├── PixelReaders.kt  HardwareReaders.kt  OffscreenRenderers.kt
│   │   ├── ViewCapturers.kt  NonPixelSignals.kt  Models.kt
│   └── ui/ProbeView.kt
└── ui/
    ├── MainActivity.kt            # 单 Activity + Navigation3
    ├── navigation3/               # Route 定义与 Navigator（含预留结果回传）
    ├── screen/
    │   ├── home/       # 主页：状态卡、五项信息卡、快捷卡
    │   ├── applist/    # 应用列表：搜索/排序/批量，二级页 App Profile
    │   ├── cfc/        # 哈希测试页（21 项采集面）
    │   ├── log/        # 日志：搜索/筛选/清空，禁用提示横幅
    │   ├── settings/   # 设置：七项卡片
    │   ├── appprofile/ # 应用二级详情：功能启用、随机化指纹值、执行按钮
    │   ├── colorpalette/ about/ tools/
    ├── component/      # bottombar/dialog/filter/liquid/material/miuix/statustag
    ├── theme/          # Material/Miuix 双主题体系
    ├── util/           # AppIconCache/PinyinUtil 等
    └── viewmodel/      # 各页面 ViewModel
```

## 文档索引

| 文档 | 说明 |
|---|---|
| [doc/DEVELOPMENT.md](doc/DEVELOPMENT.md) | 从立项到现在的完整开发史、关键决策 |
| [doc/PRD.md](doc/PRD.md) | 产品需求文档：定位、用户场景、功能需求与版本规划 |
| [doc/SRS.md](doc/SRS.md) | 软件需求规格说明：功能规格、接口、性能与安全需求 |
| [doc/TODO.md](doc/TODO.md) | 路线图与待办事项 |
| [CHANGELOG.md](CHANGELOG.md) | 版本更新日志 |
| [doc/MEMO.md](doc/MEMO.md) | 开发备忘（Navigator 结果回传等保留机制说明） |

## 合规与致谢

本项目界面基于 [KernelSU Manager](https://github.com/tiann/KernelSU)（GPL-3.0）的 UI/UX 设计移植，组件库使用 [miuix](https://github.com/yuukifox/miuix)，Hook 能力基于 [libxposed](https://github.com/libxposed/api)。相应归属已在 [LICENSE](LICENSE) 与 [NOTICE](NOTICE) 中注明。

## 免责声明

本工具仅用于隐私保护研究与自有设备的指纹防护测试。请遵守当地法律法规，勿用于任何违法违规用途。使用本项目产生的任何后果由使用者自行承担。
