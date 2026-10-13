# 1.3 validation / 验证记录

Local source validation completed on 2026-10-13. These records describe the local candidate build; GitHub workflow artifacts and release asset digests are separate publication evidence.

- Android JVM: **47 test methods, 0 failures, 0 errors, 0 skipped**. Includes all earlier protocol/resource/lifecycle regressions, 783-case actual desktop oracle comparison, full malformed-Unicode call chains and actual ViewModel mode dispatch.
- Desktop: **783 actual Android vs actual desktop CryptoService comparisons passed**, across all four decode-mode combinations; four newly produced Android AGV1 tokens read by actual desktop. Frozen desktop SHA `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74`, verified exported Git blobs; Python 3.12.14 / cryptography 50.0.2.
- Protocol regression: five Android tokens read by actual desktop, and all five earlier desktop/migration vectors independently rechecked, including Android JSON/AES/AK migration.
- Offline migration: **6 tests passed**, including a whitespace-only new password, empty-password rejection and previous historical safety cases. Newly migrated whitespace-password token read by Android and actual desktop.
- `testDebugUnitTest lintDebug assembleDebug`: passed. Lint **0 errors / 40 warnings**, retained existing warning baseline. Gradle 8.7, original declared dependencies/SDK 34/build tools 34.0.0, local JBR 21.0.10. Local build used a byte-verified ASCII source copy and temporary dependency mirrors; repository dependency configuration remains unchanged. Workflow uses Java 17.
- Local APK: `AegisVaultMobile-v1.3-debug.apk`, versionName 1.3 / versionCode 5, 21,036,219 bytes, SHA-256 `9c2c512c11307e11b1cb21e8020dcebbde4df2fa709fd355f88615643ce32765`. Actual APK version and signer were checked by `prepare-release.py`; existing signer SHA-256 `b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9` retained. A workflow-produced APK may have a different binary digest.
- Physical device: **NOT RUN**. `adb devices` showed no connected device. Device keyboard/clipboard/rotation/accessibility/rendering/upgrade behavior is unverified; JVM tests and compilation do not establish physical-device acceptance.

Evidence: [local build and source hashes](validation/1.3/local-build.json), [test XML](validation/1.3/test-results/), [lint XML](validation/1.3/lint-results-debug.xml), [desktop source/prepare receipt](validation/1.3/desktop-prepare.json), [independent desktop verification](validation/1.3/desktop-verify.json), [protocol regression](validation/1.3/protocol-regression/desktop-verify.json), [migration tests](validation/1.3/migration-tests.txt). All exchange inputs/passwords are artificial public fixtures.

The frozen desktop file-consistency tests are retained as reference context only; no Android file feature or file acceptance is claimed. UI options are transient state; #10 settings recovery and the remaining device gates are deferred. Earlier 1.1/1.2 acceptance records remain historical and unchanged.

中文：47 项 Android 测试、783 条真实桌面比对、6 项迁移测试及 lint/build 通过；未连接真机，真机验收为 NOT RUN。发布仍为沿用既有签名的 debug 测试版。未增加文件功能或设置恢复，未以本地或模拟检查替代真机证据。
