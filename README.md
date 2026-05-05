<div align="center">

# 🛡️ AegisVault Mobile

### 更优雅的离线 Android 文本加密与 Base64 工具

**AES-256-GCM 文本加密 / Base64 编解码 / 中英双语 / 离线优先**

[English](./README_EN.md) · [下载 APK](https://github.com/Qrzzzz/AegisVaultMobile/releases/latest) · [更新日志](./CHANGELOG.md) · [安全说明](./SECURITY.md)

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/github/license/Qrzzzz/AegisVaultMobile)
![Release](https://img.shields.io/github/v/release/Qrzzzz/AegisVaultMobile?include_prereleases)

</div>

---

## ✨ 简介

**AegisVault Mobile** 是一个专注、轻量、离线优先的 Android 文本工具。

它主要解决两件事：

1. 用 **AES-256-GCM** 对文本进行加密和解密；
2. 对文本进行 **Base64 编码和解码**。

它不追求复杂账号体系、云同步或在线服务，而是把核心功能控制在本地：输入文本、输入口令、生成结果、复制结果。适合临时文本加密、密文传输、Base64 文本处理，以及学习 Android 本地加密工具的实现方式。

> ⚠️ Base64 只是编码，不是加密。它只改变文本表示形式，不提供保密性。

---

## 🤖 AI 辅助说明

本项目的开发、代码整理、文档编写与发布流程优化由 Codex（ChatGPT 5.5）和 Gemini 3.1 Pro 辅助完成。

---

## 🚀 功能特性

### 🔐 AES 文本加密

- 使用 **AES-256-GCM** 进行文本加密与认证
- 使用 **scrypt** 从用户口令派生密钥
- 每次加密生成随机 salt 与随机 nonce
- 输出现代 `AGV1.` 格式密文
- 支持解密旧版文本格式
- 支持旧版高风险 `AK#` 兼容模式提示

### 🔤 Base64 文本工具

- 文本 Base64 编码
- 文本 Base64 解码
- 明确提示 Base64 不提供保密性
- 不需要密码

### 📱 Android 体验

- 使用 Jetpack Compose 构建界面
- 支持简体中文和英文
- 应用内语言切换
- 跟随系统亮色 / 暗色模式
- 输入粘贴、结果复制
- 清空全部输入
- 操作状态提示

### 📴 离线优先

- 不依赖服务器
- 不需要账号
- 不上传用户输入内容
- Android 备份已关闭

---

## 📦 下载

你可以从 GitHub Releases 下载 APK：

👉 [下载最新版本](https://github.com/Qrzzzz/AegisVaultMobile/releases/latest)

当前版本：

```text
AegisVault Mobile v1.0.1
```

> 当前 APK 为 debug 签名版本，主要用于测试和演示。正式分发前建议使用私有签名密钥构建 release APK 或 AAB。

---

## 🧩 使用方式

### AES 加密

1. 选择 `AES 文本`
2. 输入要加密的文本
3. 输入 AES 密钥 / 口令短语
4. 点击 `加密`
5. 复制生成的 `AGV1.` 密文

### AES 解密

1. 选择 `AES 文本`
2. 粘贴 `AGV1.` 密文
3. 输入加密时使用的同一口令
4. 点击 `解密`
5. 查看并复制明文结果

### Base64 编码 / 解码

1. 选择 `Base64 文本`
2. 输入文本或 Base64 内容
3. 点击 `编码` 或 `解码`
4. 复制结果

---

## 🔐 安全模型

AegisVault Mobile 的现代文本加密格式以 `AGV1.` 开头。其基本流程是：

```text
用户口令
  ↓
scrypt + random salt
  ↓
AES-256 key
  ↓
AES-256-GCM + random nonce
  ↓
AGV1 token
```

生成的密文载荷包含：

- 版本号
- 类型标识
- 算法信息
- KDF 参数
- salt
- nonce
- ciphertext

### 需要注意

- 口令不会被应用主动保存
- 口令丢失后，密文通常无法恢复
- 剪贴板内容可能被系统或其他高权限软件读取
- debug APK 不适合高风险安全场景
- 本项目不能替代专业密码管理器、硬件安全模块或企业级密钥管理系统

---

## 🏗️ 技术栈

| 模块 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose / Material 3 |
| 加密 | AES-256-GCM |
| KDF | scrypt |
| Base64 | Java Base64 |
| Android 最低版本 | Android 8.0 / API 26 |
| 构建系统 | Gradle Kotlin DSL |
| License | MIT |

---

## 🛠️ 开发与构建

### 环境要求

- Android Studio 或 Android SDK 命令行工具
- JDK 17
- Gradle Wrapper
- Android Gradle Plugin 8.5.2
- Kotlin Android Plugin 1.9.24

### 克隆项目

```bash
git clone https://github.com/Qrzzzz/AegisVaultMobile.git
cd AegisVaultMobile
```

### 运行测试

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest
```

macOS / Linux：

```bash
./gradlew testDebugUnitTest
```

### 构建 Debug APK

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 生成命名 APK 包

Windows PowerShell：

```powershell
.\scripts\package-apk.ps1
```

输出位置：

```text
dist/AegisVaultMobile-v1.0.1-debug.apk
```

---

## 📁 项目结构

```text
AegisVaultMobile/
├── .github/
│   └── workflows/
│       └── android.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/aegisvault/mobile/
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/
│       │   │   │   └── AegisVaultEngine.kt
│       │   │   ├── data/
│       │   │   │   └── AppPreferences.kt
│       │   │   └── ui/theme/
│       │   └── res/
│       │       ├── values/
│       │       ├── values-zh-rCN/
│       │       └── xml/
│       └── test/
│           └── java/com/aegisvault/mobile/core/
├── scripts/
│   └── package-apk.ps1
├── CHANGELOG.md
├── LICENSE
├── README.md
├── README_EN.md
└── SECURITY.md
```

---

## 🧪 当前版本

### v1.0.1

初始 GitHub 发布版本。

主要内容：

- AES-256-GCM 文本加密与解密
- Base64 编码与解码
- 简体中文与英文界面
- 离线优先 Android 应用
- debug APK 测试包

---

## ⚠️ 免责声明

AegisVault Mobile 是一个轻量级本地文本工具。虽然它使用现代加密算法，但实际安全性仍取决于用户口令强度、设备环境、剪贴板暴露风险和 APK 签名方式。

请不要把 debug 构建用于生产、商业或高风险安全场景。

---

## 📄 License

本项目基于 [MIT License](./LICENSE) 开源。
