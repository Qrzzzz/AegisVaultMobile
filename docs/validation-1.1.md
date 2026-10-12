# AegisVault Mobile 1.1 本地交付与验证

本文件保存初始本地验证阶段的历史记录；后续发布工作流与公开产物以 [v1.1 发布说明](releases/v1.1.md) 和 GitHub Release 为准。

日期：2026-10-12（Asia/Shanghai）。工作区 main，基线 `fd9811b82453d1feebecfe3e167b32a9b30acb1a`。版本 `1.1` / versionCode `3`。实现和产物均留在本地；没有提交、推送、PR、合并、tag、Release 或 issue 状态变更。

## 实现

- #5：同步占用有效任务，重复操作不重入；generation + Job 取消 + 过期提交丢弃。清空、自动清空离开、销毁、输入/口令编辑、切模式、清除结果均失效旧任务。完成只改当前结果/消息/忙碌；设置不会被旧快照覆盖。取消保持 CancellationException。
- #4：JSON 字段真实类型、固定32字节key、实际16..64字节salt及声明一致、Long内存/工作成本预检。Android暂行64 MiB内存和4,194,304工作单位；默认参数仍32 MiB。所有危险组合只验证validator，不执行派生。
- #7：1 MiB明文、2 MiB编码输入、16 KiB元数据与32层JSON嵌套上限，以及独立解码/密文预算。双层Base64最坏输出在KDF前估计；不再将整个正文算作32 KiB头部。预检非法surrogate，中英资源/加密不可用错误。
- 产品版本两段式；APK脚本根据实际构建的output-metadata.json命名。历史1.0.1记录和所有声明的依赖版本未改。

完整桌面合约、Android双层Base64容量公式、错误分类与1.2/1.3/1.4计划：[backend-1.1.md](backend-1.1.md)。冻结参考源与向量在 `contracts/desktop-c6664e2/`，历史Android样本在 `app/src/test/resources/android-historical.json`。三个样本也由重新编译的精确Android原始基线成功读通。

## 已执行结果

| 检查 | 结果 |
|---|---|
| testDebugUnitTest | PASS：34 tests，0失败/错误/跳过 |
| lintDebug | PASS：0 errors，40 warnings；未擅自升级依赖/重做UI来消除警告 |
| assembleDebug | PASS：真实debug APK |
| 原声明依赖独立JVM检查 | PASS：30 tests；原Kotlin1.9.24/lifecycle2.8.2/coroutines1.8.1/BC1.78.1。此检查使用R/注解占位，不替代上面的Android构建检查 |
| Android基线迁移样本 | PASS：JSON AGV1、旧AES、AK#三类人工非敏感样本 |
| 当前源与ASCII构建副本 | PASS：所有app/src及构建配置/打包脚本逐字节一致，SHA-256清单已保存 |
| 产品/产物版本 | PASS：APK实际versionName=1.1、versionCode=3、compile/targetSDK=34、minSDK=26；脚本产物AegisVaultMobile-v1.1-debug.apk |
| APK签名 | PASS：apksigner verify，v2 debug签名，非正式发布签名 |
| git diff --check、PowerShell脚本语法 | PASS |

确定性任务测试通过暂停NonCancellable worker后清空/离开/销毁、编辑输入/口令/切模式/更新设置、重复10次点击、逆序完成、旧失败/旧finally、新失败及取消。KDF边界只运行validator；JSON类型/盐/元数据拒绝发生在派生前。文本检查包括30 KiB ASCII、中文、emoji、1 MiB安全输入边界、双层输出容量、Base64解码预算、超限和非法surrogate。正式桌面向量在1.1仍被拒绝，这属于预期协议差异。

## 构建环境与证据

使用仓库原配置 Gradle8.7、AGP8.5.2、Kotlin1.9.24、SDK34 revision3/build-tools34.0.0、Android Studio JBR，依赖版本没有替换。官方Gradle域名TLS失败，官方GitHub分发速度受限；从镜像取得8.7后按wrapper指定SHA-256校验：`544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`。SDK34从Google官方archive取得，校验官方repository清单SHA1：`1f2e9478d6a7601425ceaa553311dc43191f103d`。已有SDK35/36未冒充SDK34。

Windows中文项目路径被AGP拒绝，因此在 `C:\Users\qrzzz\AppData\Local\Temp\aegis11-build-root` 构建当前源码副本。网络仓库临时init script改为镜像Google/Maven源以克服TLS阻塞，仅改变下载源，未写入产品Gradle配置。已保存解析依赖树，确认lifecycle2.8.2、coroutines1.8.1、BC1.78.1与测试org.json20240303；独立下载的Kotlin编译器和lifecycle AAR另与官方Maven/Google SHA1匹配。

执行：`gradlew.bat --no-daemon --init-script <temporary repositories script> testDebugUnitTest lintDebug assembleDebug`。完整成功日志、测试XML、lint XML/HTML、依赖树、临时下载源配置、原基线样本结果、APK元数据与签名结果都在 [validation/1.1/](validation/1.1/)。可直接查看 [lint report](validation/1.1/lint-results-debug.html)。原目录直接执行仍受中文路径和网络环境限制；成功结果来自逐字节核验后的ASCII副本。

`dist/AegisVaultMobile-v1.1-debug.apk`：20,948,096 bytes。
SHA-256：`15d9774237ad4a1359c2f0a103d005b0754f2a158cbddcde79fe159b0ca4eeec`。`dist/SHA256SUMS.txt` 为本地校验文件；未上传任何产物。

## 尚未验证与剩余边界

- `adb devices` 没有设备；API26/新系统实体设备、真实前后台/系统杀进程/配置变化、屏幕与内存压力验收均未完成。64 MiB是暂行策略限制，不是设备峰值测量，也不保证所有设备都不会分配失败。
- 1.1继续使用Android JSON型AGV1、AAD=null与旧AES/AK#支持。正式桌面binary envelope与原始header AAD尚未实现，两端仍无法互解。Android1 MiB正文/64 MiB KDF不承诺承载桌面全部最大边界。
- 未执行超预算KDF；没有以OOM捕获充当预检，也没有运行危险压力探针。真实Android平台JSON/provider行为尚需设备验证；本轮Android单元测试按声明org.json测试依赖执行。
- 1.2完成明确离线旧密文迁移与正式协议/真实跨端向量；1.3统一完整空白/Base64/Unicode语义与设置恢复；1.4完成非文件设备与独立验收。文件功能与UI重设计均不在此次范围。
