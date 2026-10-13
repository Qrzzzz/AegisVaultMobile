# AegisVault Mobile 1.2 本地实现与验证

本文件保存初始本地验证阶段的历史记录。后续公开产物与发布状态以 [v1.2 发布说明](releases/v1.2.md) 和 GitHub Release 为准。

日期：2026-10-13，Asia/Shanghai。以下记录来自发布授权前的本地候选阶段。versionName=1.2，versionCode=4。当前 main/远端 main 基线 `b274ffa991190ca30d1361c1b2afb0a74616c945`；公开 v1.1 的 annotated tag 源提交 `34c582a2f6b46309f64a632471749a3f71d695f2`。保留 1.1 修复、历史合约/样本与签名身份，没有 commit/push/PR/merge/tag/Release 或 issue 状态变更。

## 实现与边界

- #3：正式桌面 binary AGV1、canonical JSON writer、原始 header AAD、严格字段类型/KDF/UTF-8/长度验证。独立 `ProtocolJson` 拒绝重复键、非有限值、异常 JSON 和32层以上嵌套，不使用 org.json 的生产解析行为。
- #6：App 正式 AGV1-only；移除旧 SHA256 派生、AK parser、usedLegacyAk 和中英兼容承诺。历史数据有独立显式离线迁移工具与[中文](migration-1.2.zh-CN.md)/[English](migration-1.2.en.md)说明。AK 既有口令泄露风险不可撤销，迁移前必须备份原件。
- 单层 Base64 封装，头部65,536 bytes、正文1 MiB、token2 MiB、KDF64 MiB/4,194,304 work；格式互通不承诺支持桌面全部最大资源参数。没有危险 KDF/OOM 压力探针，不降低传入参数。
- 保留1.1 generation/取消/过期提交丢弃和正常文本/Base64行为。没有 App 文件功能、批处理或 UI 重设计。完整 Base64/空白语义属1.3，设置恢复和整体验收属1.4。

## 执行结果

| Check | Result |
|---|---|
| `testDebugUnitTest` | PASS：42 tests，0 failures/errors/skips |
| `lintDebug` | PASS：0 errors，40 warnings；未变更既有依赖/UI消除历史警告 |
| `assembleDebug` | PASS：实际 debug APK，版本1.2/code4 |
| 冻结桌面固定向量 | PASS：精确Unicode解密；固定salt/nonce/time writer token字节完全相同 |
| 非规范合法 JSON 头 | PASS：按原始 bytes 认证；重新序列化替代AAD失败 |
| 错口令/头部和正文篡改/字段错误类型/版本/算法/KDF | PASS：受控错误；高成本仅validator，未执行危险派生 |
| 截断/头长/正文预算/重复键/NaN/Infinity/数字溢出/异常语法/UTF-8/surrogate/深度 | PASS：解析/认证前预检；认证后非法UTF-8拒绝 |
| 真实 desktop → Android Engine | PASS：实际CryptoService生成2个新随机token，Android Gradle单元测试精确读回 |
| 原始3个历史样本 | PASS：生产App入口拒绝；独立迁移后均在Android及实际桌面精确读回 |
| Android Engine → 真实 desktop | PASS：Android实际新生成5个token，实际CryptoService全部精确读回 |
| 迁移脚本 unittest | PASS：5 tests，包括原始冻结样本、错误口令/损坏无成功输出、资源validator、CLI无泄漏/不覆盖，以及Unicode AK嵌入口令恢复 |
| APK签名/打包 gate | PASS：`prepare-release.py --tag v1.2` 验证实际APK、metadata、旧签名；本地包已生成 |
| Release check-only | PASS：`prepare-release.py --check-only --tag v1.2`；仅检查候选，不创建tag |
| 源码ASCII副本 | PASS：45个app/src及原构建/打包配置文件逐字节一致；有SHA256清单 |
| Git diff whitespace | PASS |
| 真机协议/迁移/生命周期/平台provider/内存 | **NOT RUN / BLOCKED**：`adb devices`无设备 |

Android 结果来自真实原配置 Gradle Android JVM 单元测试与APK编译，**不是实体手机执行证据**。没有将独立占位JVM编译或各自本端roundtrip冒充跨端互通/设备通过。平台 JSON 差异通过移除生产 org.json 解析依赖消除；Android具体provider和系统生命周期仍需设备验证。

## 源与环境证据

实际桌面源为冻结 Git SHA `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74` 的完整 `src/` archive。`interop-1.2.py` 对每个 Python 源逐字节比较该 SHA 的 Git blob，并验证冻结合约摘要；加载未经替代的 `aegisvault.services.crypto_service.CryptoService(AppSettings())`，不是重写的模拟服务。Python3.12.14、cryptography50.0.2、Windows11。桌面旧 .venv 指向已不存在的Python，未使用；本次使用Codex bundled Python及cryptography。

Android使用原 Gradle8.7、AGP8.5.2、Kotlin插件1.9.24、BC1.78.1、lifecycle2.8.2、coroutines1.8.1、测试org.json20240303；没有升级或替换依赖。JBR21.0.10，SDK34 revision3/build-tools34.0.0。Gradle runtime本身显示的embedded Kotlin1.9.22不等于应用Kotlin插件版本。

复用1.1下载且本次重新计算摘要的Gradle8.7 archive：`544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`。SDK34实际source.properties保存在环境证据。中文路径仍不被AGP支持，在 `C:\Users\qrzzz\AppData\Local\Temp\aegis12-build-root` 的一致ASCII源码副本执行。设置JAVA_HOME/ANDROID_HOME，使用1.1临时mirror init script克服本机下载限制；镜像仅改变下载源，未进入产品配置。

执行过的关键命令（路径根据本机设置）：

```powershell
$env:JAVA_HOME = 'D:\Programme Files\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\qrzzz\AppData\Local\Android\Sdk'
# 原源导出及 prepare 从仓库根目录执行，使用 bundled Python。
git -C '..\桌面版' archive --format=zip --output "$env:TEMP\aegis12-desktop.zip" c6664e2b7e6a11b7dfb55f0881136131e1d2bc74 src
# 展开archive后，真实桌面生成公开人工fixture及历史迁移fixture。
python scripts/interop-1.2.py --desktop-root <frozen-export> --exchange-dir docs/validation/1.2 --phase prepare
# 同步app/src及配置到ASCII副本，核对source-sha256，再从副本执行。
$env:AEGIS_INTEROP_DIR = '<original-repo>\docs\validation\1.2'
.\gradlew.bat --no-daemon --init-script <temporary-mirrors.gradle> testDebugUnitTest lintDebug assembleDebug
# 原目录验证Android导出的公开人工token。
python scripts/interop-1.2.py --desktop-root <frozen-export> --exchange-dir docs/validation/1.2 --phase verify
python scripts/test-migration.py
python scripts/prepare-release.py --check-only --tag v1.2
```

`prepare`会重新生成随机测试token，不应在已验证交付后随意运行；重生成后需要重新执行Android测试与双向verify。全部fixture/交换记录均为**人工公开测试数据**，不包含真实敏感数据。`AEGIS_INTEROP_DIR`只用于测试导出，不是生产App日志入口。

报告：[validation/1.2/](validation/1.2/)，包括Android测试XML、lint XML/HTML、原配置依赖树、Gradle日志、环境/源码摘要、desktop prepare/verify源SHA收据、Android新token、迁移日志、APK metadata及签名记录。两个互通方向和迁移均有独立执行记录。

## 可安装候选产物

`dist/AegisVaultMobile-v1.2-debug.apk`，20,975,261 bytes。

SHA256：`c11e6784a73b25b37960f80999acaaee5efece88ea4392ab88656c7bd370a4dc`。

`dist/SHA256SUMS.txt`已更新为候选摘要。apksigner verify v2通过；cert SHA256 `b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9`，与既有1.0.1/1.1测试签名一致。该文件仅为本地 debug 测试候选，不是公开 Release。

## 尚未解决的限制

- 未连接实体设备，API26/新系统上的provider、协议/迁移实际使用、前后台/销毁/系统杀进程和内存验收没有运行。64 MiB仅为保守策略，不是测得的峰值保证。
- Desktop较大有效正文或KDF组合会被Android明确RESOURCE拒绝；未扩大设备预算来获得表面互通。
- migration reader仅存在于独立离线工具中。它不能撤销旧AK口令泄露，也不能保证Python/JVM即时擦除全部敏感内存。不要在互通脚本或仓库保存真实输入。
- 新App不直接支持旧Android JSON/AES/AK，升级前需要迁移并保留备份。Base64完整空白/padding模式与设置恢复仍按后续1.3/1.4处理。
- 当前变更留在本地，不关闭#3/#6或其他issue。最新版公开下载仍是1.1。
