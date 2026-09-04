# APPCanvasFaker

Canvas 指纹随机化伪装模块。在目标应用进程内 Hook Canvas/Bitmap 的读取与编码路径，用坐标绑定的确定性噪声替换真实渲染结果——指纹**稳定地不同于真实值**（同应用多路径一致、不同应用各不相同、噪声带非零偏置抗统计检测），而非随机漂移。基于 libxposed，界面复刻 KernelSU Manager 原版设计。

| 项目 | 值 |
|---|---|
| 包名 | `dev.neekolor.appcanvasfaker` |
| 版本 | 0.8.3-dev (versionCode 40) |
| 运行环境 | Android · root · LSPosed |
| 实现 | Kotlin · Jetpack Compose · Navigation3 · Miuix + Material3 双皮肤 |
| 许可证 | GPL-3.0（[LICENSE](LICENSE) / [NOTICE](NOTICE)） |

## Hook 覆盖

整图像素读取、单点读取、缓冲区读取、PNG/JPEG 编码出口默认开启；文本度量微扰默认开启；
GPU 直读默认关闭（避免影响游戏录像、推流等，按需开启）。完整覆盖表见
[Wiki · 安装与使用](https://github.com/Neekolor/APPCanvasFaker/wiki/安装与使用)。

## SSAID 管理

提供系统级 SSAID 的随机化与删除：真实的 root 写操作（非 Hook），带二次确认，操作前强制停止目标应用；写入走"临时文件 → 校验 → 原子替换"流程，任一步失败不触碰系统原文件。列表按系统文件原始顺序展示，等宽字体显示，点击整行复制。

## 使用

1. 安装 APK，在 LSPosed 中启用模块
2. 作用域勾选目标应用（或全选）
3. 打开模块，在应用列表中对目标应用开启随机化
4. 重启目标应用生效（模块自身不可被 Hook，主页基准卡恒为本机未污染基准值）

## 验证

模块内所有对外展示的哈希统一为 16 位折叠口径（SHA-256 四块 XOR），主页基准卡、App Profile 卡与配套扫描器应用三方数值可直接互比：主页卡恒为本机未污染基准（参照）；对目标应用开启伪装前后，用扫描器/App Profile 各采集一次，哈希改变即确认 Hook 生效且无串扰。

## 从源码构建

```bash
git clone https://github.com/Neekolor/APPCanvasFaker.git
cd APPCanvasFaker
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/APPCanvasFaker_<version>_debug.apk
```

环境要求：JDK 21+、Android SDK（compileSdk 见 gradle/libs.versions.toml）。

## 许可与声明

本项目为 GPL-3.0 许可。界面基于 [KernelSU Manager](https://github.com/tiann/KernelSU) 的 UI/UX 设计移植，组件库使用 [miuix](https://github.com/compose-miuix-ui/miuix)，Hook 能力基于 [libxposed](https://github.com/libxposed/api)，归属详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

本工具仅用于隐私保护研究与自有设备的指纹防护测试。请遵守当地法律法规，勿用于任何违法违规用途。使用本项目产生的任何后果由使用者自行承担。
