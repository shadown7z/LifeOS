# LifeOS 阶段 1：基础工程与静态 UI

更新时间：2026-10-14

## 已完成

- 将应用启动入口切换为单 Activity + Jetpack Compose。
- 配置 Compose BOM、Material 3、Navigation Compose 和 Kotlin Compose 编译器。
- 建立 LifeOS 浅色/深色主题，使用暖白背景、深色正文、青绿色强调色。
- 建立底部导航：首页、目标、笔记、习惯、更多。
- 建立静态页面：Daily Dashboard、Goals、Notes、Habits、Journal、Settings。
- 首页实现每日习惯即时打卡、完成数量和进度条动画。
- 加入目标、笔记、日记、设置的静态入口和空状态文案。
- 加入页面内容切换、习惯状态变化和内容尺寸变化动画。
- Manifest 增加可启动的 `MainActivity`，替换旧的 Material Components 启动主题。

## 验证结果

通过：

```text
./gradlew.bat :app:assembleDebug --no-configuration-cache
BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --no-configuration-cache
BUILD SUCCESSFUL

./gradlew.bat :app:lintDebug --no-configuration-cache
BUILD SUCCESSFUL
```

Lint 报告仍包含 10 条警告，但没有阻断错误；主要是 Compose/Android 资源层面的提示，不影响本阶段构建。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

未完成：

- 当前执行环境没有 `adb` 命令和可用 Android 设备，因此未完成真机/模拟器启动、导航点击和截图验证。
- 当前页面使用内存静态数据，尚未接入 ViewModel、Room、DataStore 和真实文件导入导出；这些属于阶段 2 至阶段 4。

## 手动验证步骤

1. 在 Android Studio 打开项目并启动一个 API 36 或更高版本模拟器。
2. 安装 `app/build/outputs/apk/debug/app-debug.apk`。
3. 检查底部五个入口，打开“更多”中的日记和设置。
4. 在首页点击四个习惯，确认勾选、完成数量和进度条即时变化。
5. 切换系统深色模式，确认背景、文字和卡片仍可读。
6. 在窄屏设备上滚动首页、笔记和目标页面，确认没有横向溢出。
