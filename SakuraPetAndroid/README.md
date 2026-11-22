# 小樱桌宠 Android v0.5（手机用户云端构建版）

本项目是 Android Studio 源码工程，不是现成 APK。v0.5 的重点是加入 GitHub Actions 云端构建配置，让没有电脑的用户也有机会通过浏览器在云端生成 APK。

## v0.5 新增
- GitHub Actions 工作流：`.github/workflows/android-apk.yml`
- 支持在 GitHub Actions 页面手动触发构建，也可在 main/master 分支推送后自动构建。
- 构建成功后上传 `SakuraPet-debug-apk` 工件，文件名为 `app-debug.apk`。
- 应用版本号更新为 0.5.0。

## 没有电脑，如何尝试生成 APK
1. 在手机浏览器打开 GitHub 并注册/登录账号。
2. 创建一个新的空仓库，例如 `SakuraPetAndroid`。
3. 把本 ZIP 解压后的**项目内所有文件和文件夹**上传到仓库根目录。必须包含 `.github/workflows/android-apk.yml`（上传时确保显示隐藏目录）。如果手机端网页不方便上传整个文件夹，可使用 GitHub Codespaces 或其他云端开发环境解压 ZIP 后，将文件提交到仓库。
4. 打开仓库的 **Actions** 标签页，选择 **Build SakuraPet APK**，点击 **Run workflow**。
5. 等待工作流完成。成功后打开该次运行页面底部的 **Artifacts**，下载 `SakuraPet-debug-apk`，解压后得到 `app-debug.apk`。
6. 把 APK 下载到 Android 手机上，打开文件安装。若系统提示，允许浏览器/文件管理器“安装未知应用”。只安装你自己仓库构建出来的 APK。

## 当前限制与安全
- 云端构建工作流尚未在本项目实际运行验证；首次构建可能遇到 Android SDK/Gradle 兼容问题，需要根据 Actions 日志修复。
- 生成的是 debug APK，便于个人测试，不是正式发布用的签名版本。
- AI 聊天仍需你配置自己可信的 HTTPS 后端；不要把模型 API Key 写进 APK 或公开仓库。
- 当前 AI 接口与悬浮窗功能仍需真机测试；TTS 音色由手机语音引擎决定，应用只能设置中文、语速和音调，不能保证所有手机都提供相同少女音。
- 悬浮桌宠需用户授予“显示在其他应用上层”权限；Android 厂商后台限制可能影响常驻行为。

## 项目技术
- Kotlin + Jetpack Compose
- minSdk 26（Android 8.0）
- compileSdk 35
- Gradle 8.9 / Android Gradle Plugin 8.7.3 / Kotlin 2.0.21
