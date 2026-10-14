# 象棋悬浮窗助手（Android）

**不参与下棋的实战辅助 App**：悬浮在你的象棋 App 上层，自动识别对手落子、
实时算出你的应手、在悬浮窗里告诉你该走哪一步。**落子始终由你手动完成。**

```
对手落子 → 自动截屏识别 → 判断轮到你了吗 → 本地引擎算应手 → 悬浮窗提示（含落点箭头）→ 你手动落子 → 循环
```

- 全程在手机本地运行，**不需要电脑、不需要联网**
- 识别算法从原项目的 `recognition_companion.py` 忠实移植（四角标定 + 透视矫正 + 模板匹配）
- 图像处理**纯 Java 实现，不依赖 OpenCV**，APK 更小、与 CPU 架构无关
- 引擎为 Pikafish（Stockfish 象棋分支），棋力约 3000+ Elo

---

## 一、系统要求

| 项目 | 要求 |
|---|---|
| 系统 | **Android 10 及以上**（受预编译引擎二进制限制，见下方说明） |
| CPU | 64 位 ARM（arm64-v8a） |
| 存储 | 约 120MB（APK 55MB + 解压后的神经网络 50MB） |
| 权限 | 悬浮窗、录屏、通知、电池优化白名单 |

> **为什么不是 Android 5.0？** App 代码本身按 `minSdk 21` 编写，支持 Android 5.0+。
> 但项目自带的引擎二进制 `Pikafish-Android-arm64-universal` 在 ELF 头里声明了
> 最低 API 29（Android 10）。想支持更老的手机，需用 Android NDK 重编引擎
> （方法见第五节），App 代码不用改。

---

## 二、项目结构

```
android_app/
├── settings.gradle / build.gradle / gradle.properties / gradle/wrapper/
├── tools/make_icons.py                    ← 图标生成脚本（已运行过）
└── app/
    ├── build.gradle                       ← minSdk 21 / arm64 插槽 / useLegacyPackaging
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/pikafish.nnue           ← 50MB 神经网络
        ├── jniLibs/arm64-v8a/libpikafish.so  ← 2.4MB 引擎（arm64）
        ├── res/…                          ← 布局、图标、文案
        └── java/com/xiangqi/overlay/
            ├── App.java                   ← 应用入口
            ├── MainActivity.java          ← 权限引导 + 启动/停止
            ├── CalibrationActivity.java   ← 四角标定
            ├── CalibrationView.java       ← 可拖角点/双指缩放的标定视图
            ├── OverlayService.java        ← 核心前台服务（截屏→识别→引擎→悬浮窗）
            ├── OverlayController.java     ← 双窗口悬浮窗（面板可拖动 + 箭头层穿透）
            ├── ArrowView.java             ← 落点箭头绘制
            ├── ScreenCapture.java         ← MediaProjection 截屏（含黑屏检测）
            ├── XiangqiRecognizer.java     ← 识别核心（移植自 Python 版）
            ├── BoardProfile.java          ← 标定档案持久化
            ├── Position.java              ← 局面表示与 FEN 读写
            ├── MoveTranslator.java        ← UCI → 中文着法（重写，修掉原版 bug）
            ├── PikafishEngine.java        ← 引擎进程 + UCI 协议
            ├── DeviceCompat.java          ← 厂商/版本差异适配
            └── img/                       ← 纯 Java 图像内核
                ├── GrayImage.java         ← 灰度图容器
                ├── ImageOps.java          ← 模糊/缩放/NCC/边缘
                └── Perspective.java       ← 单应矩阵求解 + 透视矫正
```

---

## 三、如何编译出 APK

本机未安装 JDK / Android SDK / Gradle，所以需要用 Android Studio 编译（约 10 分钟）：

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 JDK 和 SDK）
2. 打开 Android Studio → **File → Open** → 选择 `Chinese-chess/android_app` 文件夹
3. 首次打开会提示缺少 Gradle wrapper，选择 **Use Gradle from: gradle-wrapper.properties**
   并让它自动生成（或菜单 **File → Sync Project with Gradle Files**）
4. 等待右下角进度条跑完（首次会下载 Gradle 8.7 与构建工具，需要联网）
5. 手机开启**开发者选项 → USB 调试**，用数据线连电脑
6. 顶部设备下拉框选中你的手机 → 点绿色 **Run ▶** 按钮
7. 完成后手机上会出现「象棋助手」图标

命令行方式（可选）：

```bash
cd android_app
gradle wrapper          # 首次需要，生成 gradlew
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 四、如何使用

### 首次配置（每个象棋 App 做一次）

1. **开权限**：打开「象棋助手」，把三个权限都点亮
   - 悬浮窗权限（小米手机还要开「后台弹出界面」）
   - 通知权限
   - 电池优化白名单（设为「无限制」）
2. **启动**：点「启动悬浮窗辅助」，在系统弹窗里点「立即开始」
3. **标定**：
   - 切到你的象棋 App，进入一局**刚开局的**对局，让棋盘完整显示
   - 切回「象棋助手」，点「标定棋盘四角」
   - 拖动 4 个红点，框住棋盘**最外圈的 4 个交叉点**（双指可缩放查看细节）
   - 点「确认标定」——系统会自动校验并生成识别模板

### 日常使用

1. 切到象棋 App 正常下棋
2. 悬浮窗会显示：
   - 当前状态（等待稳定 / 对手走棋中 / 轮到你走）
   - **对手刚走的着法**（如「马8进7」）—— 你可以顺便核对识别对不对
   - **建议你走的着法**（如「炮二平五」）+ 评估分 + 深度
   - 棋盘上的**红箭头**标出起止点
3. 照着走即可，它会继续自动跟

### 悬浮窗操作

- 按住顶部标题栏可拖动
- 点「收起」折叠成小条，点「展开」还原
- 点「标定」重新标定，「关闭」退出

---

## 五、如何支持更多手机（32 位 / 更老系统）

Gradle 已把架构插槽预留好。需要两步：

1. 用 Android NDK（r27c+）编译引擎，项目根目录的 `src/` 就是 Pikafish 源码：

   ```bash
   # 在 src/ 目录下
   make -j build ARCH=armv7      COMP=ndk      # 32 位 ARM
   make -j build ARCH=armv8      COMP=ndk      # 64 位 ARM（已有现成的）
   make -j build ARCH=x86_64     COMP=ndk      # x86 模拟器
   ```

   > 要支持 Android 10 以下，把编译器换成低 API 版本：
   > `make ... COMP=ndk COMPCXX=aarch64-linux-android21-clang++`

2. 把产物改名为 `libpikafish.so`，放到 `app/src/main/jniLibs/armeabi-v7a/`，
   并把 `app/build.gradle` 里的 `abiFilters` 改成：

   ```groovy
   abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64'
   ```

   **代码一行都不用改。**

---

## 六、常见问题

| 现象 | 原因与解决 |
|---|---|
| 悬浮窗不显示 | 悬浮窗权限没开全。小米需同时开「显示在其他应用上层」和「后台弹出界面」 |
| 提示「截到黑屏」 | 对方 App 开了防截屏，或录屏授权不完整。重新启动辅助再试 |
| 切到象棋 App 就没反应 | 被系统杀后台。把本应用的省电策略设为「无限制」并在最近任务里锁定 |
| 标定失败「不像初始局面」 | 必须在**开局状态**标定。回到开局再点标定 |
| 识别置信度低 | 棋盘被弹窗/表情遮挡、用了非默认皮肤、或四角拖偏。重新标定 |
| 建议迟迟不出 | 深度可在代码里调（`OverlayService` 的 `quickDepth`/`deepDepth`），或等一局结束后再试 |
| 引擎启动失败 | 手机不是 64 位 ARM，或系统低于 Android 10 |

---

## 七、必须知道的风险

- 本工具**只提供建议，不会代替你落子**
- 在排位赛、真人对局中使用可能**违反游戏平台的服务条款**，可能导致账号受限
- 建议仅在**人机对战、练习、复盘**场景使用
- 截屏内容只在手机本地处理，**不会上传任何数据**

---

## 八、与原电脑方案的关系

原项目（本仓库其余部分）是「手机 ADB 截屏 → 电脑 Flask 识别+引擎 → 手机看建议」，
必须一直开着电脑。本 App 把整条链路搬进了手机，是原方案文档里
「长期目标：完全独立的 Android App（内置引擎）」的实现。
