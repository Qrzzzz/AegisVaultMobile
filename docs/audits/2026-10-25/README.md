# 2026-10-25 问题审计

基线：`ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`，本地 HEAD、GitHub main 与当前正式版 v1.5 的代码一致。审计前已有 #5 的未提交仪器测试、验收材料和 CI 工作流改动，均保留。本轮新增审计材料及 issues，没有修改产品代码、版本或发布产物，也没有提交、推送或发布。

## 已建立的问题

| 优先级 | Issue | 结论与证据 |
| --- | --- | --- |
| P1 | [#16 KDF 任务缺少应用级并发预算](https://github.com/Qrzzzz/AegisVaultMobile/issues/16) | 未修改 ViewModel 的 JVM 探针复现 8 个不可协作 worker 在途；清空 UI 后仍有 8 个。结合生产同步 scrypt 判断聚合资源风险，未实际触发手机 OOM。 |
| P2 | [#17 语言切换宿主不兼容](https://github.com/Qrzzzz/AegisVaultMobile/issues/17) | ComponentActivity 与 AppCompatDelegate 的集成不符合官方语言 API 合约，重点影响 API 26–32；源码证据，设备步骤待执行。 |
| P2 | [#18 复制明文未加敏感标记](https://github.com/Qrzzzz/AegisVaultMobile/issues/18) | 统一结果复制出口没有 ClipDescription.EXTRA_IS_SENSITIVE；结合 Android 13+ 官方预览行为确认缺口，没有伪造设备截图。 |
| P2 | [#19 配置重建误触发离开清空](https://github.com/Qrzzzz/AegisVaultMobile/issues/19) | ON_STOP 无条件转为离开事件，启用清空时配置重建也会删除会话；源码及官方生命周期合约，设备步骤待执行。 |
| P2 | [#20 剪贴板截止时间与所有权管理缺失](https://github.com/Qrzzzz/AegisVaultMobile/issues/20) | coroutines 窄模型复现 Composition 释放后计时丢失，以及第二次复制同一内容 10 秒后被旧计时清除；不是系统剪贴板实测。 |

每个 issue 包含不可变代码链接、影响、证据边界、复现或待执行的设备步骤，以及修复验收条件。[创建回执](issues-created.json)。已有 #3–#10 均已关闭，本轮没有重复创建其原有兼容性问题；#16 与 #5 的状态回写保护明确区分。

## 覆盖与验证

源码检查覆盖 AGV1 二进制封装/原始头部 AAD、严格 JSON/UTF-8、scrypt 成本与文本预算、Base64 语义、设置恢复、更新合约/HTTPS/缓存/安装入口、Activity 生命周期、语言、屏幕保护、剪贴板及 CI/发布流程。没有把未观察到故障的猜测单独立案。

- **39 项原有 JVM 测试通过**：核心协议/加密/文本语义/资源边界 28 项，更新解析/身份策略/HTTPS 流式传输 11 项。[核心结果](core-tests.txt) · [更新结果](update-tests.txt) · [合并重放结果](replay.txt)。HTTP 测试使用连接替身；不代表 Android TLS 或安装验收。
- **13 项 Python 测试通过**：6 项迁移、7 项更新合约/发布验证器。[迁移结果](migration-tests.txt) · [合约结果](update-contract-tests.txt)。验证器输出的 artificial release 是测试夹具，不是真实发布。
- **任务准入探针完成**：[源码](probes/TaskOverlapProbe.kt) · [输出](task-overlap.txt)。旧结果仍被丢弃，#5 的保护没有被复现为回归。8 个暂停 worker 不是 8 次真实并发 scrypt 的实测，也不是实际 OOM 证据。
- **剪贴板算法模型完成**：[源码](probes/ClipboardTimerModelProbe.kt) · [输出](clipboard-timer-model.txt)。用 scope.cancel 建模文档规定的 Composition 释放，没有执行 Compose 渲染器或 ClipboardManager。
- **本轮 Android APK 构建、lint、模拟器及实体手机测试未运行**。历史 SDK/adb 目录已不可用，本轮没有重新安装 SDK 或配置设备。没有将 10 月 15 日的 #5 手机验收扩展为此次其他问题的设备证据。

上述 JVM 检查通过独立 Kotlin 编译/运行完成，使用 JBR 21.0.10、Gradle 自带 Kotlin 编译器 1.9.22、实际 lifecycle 2.8.2 / coroutines 1.8.1 / BC 1.78.1；它们不是仓库 Gradle Android 任务（配置为 Kotlin 1.9.24）。ViewModel 与 core 源码未修改；编译只补充资源 ID 和 SettingsNotice 枚举符号，没有替换任务逻辑。生成符号见 [probes](probes/)。[环境记录](environment.json) · [源码 SHA-256](source-sha256.json) · [依赖 SHA-256](probe-dependency-sha256.json)。

## 重放

准备依赖目录，文件名与 SHA-256 需符合上述清单：

| 文件 | Maven 坐标 |
| --- | --- |
| coroutines.jar | org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1 |
| coroutines-test.jar | org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.8.1 |
| viewmodel/classes.jar | androidx.lifecycle:lifecycle-viewmodel-android:2.8.2 AAR 内的 classes.jar |
| annotation.jar | androidx.annotation:annotation-jvm:1.8.0 |
| bcprov.jar | org.bouncycastle:bcprov-jdk18on:1.78.1 |
| junit.jar | junit:junit:4.13.2 |
| hamcrest.jar | org.hamcrest:hamcrest-core:1.3 |
| json.jar | org.json:json:20240303 |

运行 [run-host-checks.ps1](run-host-checks.ps1)，传入 DependencyDir；必要时覆盖 Java、KotlinLib 和 OutputDir。脚本检查源码/依赖哈希，独立编译原始源码，运行两个探针与 39 项原有测试。依赖来自固定版本的 Maven 下载镜像，二进制没有加入仓库。

Python 使用 3.12.14 / cryptography 50.0.2，在仓库根目录分别执行 `python scripts/test-migration.py`、`python scripts/test-update-contract.py`。

平台依据均为一手资料：[应用语言](https://developer.android.com/guide/topics/resources/app-languages)、[敏感剪贴板](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste#sensitive-content)、[配置变化与生命周期](https://developer.android.com/guide/components/activities/state-changes#configuration-change)、[Composition scope](https://developer.android.com/develop/ui/compose/side-effects#remembercoroutinescope)。
