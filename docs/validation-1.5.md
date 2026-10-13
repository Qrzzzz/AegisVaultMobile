# 1.5 local validation / 本地验证

**Pre-publication local validation record.** versionName 1.5 / versionCode 7. Baseline `f3a679f7d40d292256500008d4f81ecf75a81ae8` (`v1.4`). These records precede publication; GitHub CI, release state and remote asset digests provide separate publication evidence.

Settings now has a standard settings icon/label and a scrollable **App updates** entry: explicit check, download/progress/cancel, verified ready APK, system installation permission and installer, and the official release page available even after check failure. Chinese and English labels are present. See the [versioned update contract](update-1.5.md) and [JSON Schema](contracts/android-update-v1.schema.json).

## Results

- Final `testDebugUnitTest lintDebug assembleDebug`: **79 test methods, 0 failures/errors/skips**, build passed. Includes the 62 earlier backend/settings tests, 16 deterministic update tests and one explicitly enabled live network smoke. Ordinary CI runs deterministic tests and skips the opt-in live test; its network availability is not a CI release dependency.
- Update tests call the actual strict parser, repository, ViewModel and HTTPS streaming implementation. They cover numeric versions, malformed/duplicate/type-anomalous JSON, draft/prerelease/foreign URLs, manifest/API byte count/hash agreement, unsupported contracts, incompatible minSdk, downgrade and package/certificate policy, bounded headers/redirects/body, real streaming bytes into temporary files, truncated/excess bytes, wrong hashes, storage errors, cancellation, duplicate requests and late completion/progress.
- Tests involving HTTP connections and installer identity policy use artificial connection/archive metadata fixtures. They do not establish real Android TLS, PackageManager parsing or package installation. Separate live smoke below establishes actual host HTTPS to GitHub.
- **Actual production unauthenticated HTTPS smoke: PASS** against GitHub Latest API. It correctly identified public version **1.4** and displayed the 1.5 candidate as ahead, without downloading an APK or requiring an update manifest for the historical older version. No personal input or token was sent. [Receipt](validation/1.5/network-smoke.json) · [actual public API response](validation/1.5/github-latest.json).
- **7 Python release-contract/verifier tests passed**. Shared canonical manifest fixture matches the Android fixture; real verifier code accepts legacy two-asset and standard three-asset fixtures, rejects malformed values, missing new contracts, API digest/size mismatch and duplicate fields. These fake release receipts are not a published 1.5 Release.
- New verifier also independently verified actual public **v1.4 Release ID 411531614** and its two assets using the existing remote checksum manifest and server digests; no APK re-download. [Legacy verification](validation/1.5/legacy-release-verifier.txt).
- Lint **0 errors / 40 existing warnings**, no new warning categories. Updated CI/release YAML passes actionlint; contract tests run in both, and the release workflow generates/uploads/verifies the third manifest asset while retaining historical-tag compatibility.
- **783 actual Android vs frozen desktop CryptoService comparisons passed**, including all Base64 combinations/input semantics and four new Android AGV1 tokens. Previous five-token protocol/historical-migration exchange rechecked. **6 offline migration tests passed**. Core cryptography/data settings recovery source and migration/interoperability scripts are unchanged against v1.4.
- **Physical device: NOT RUN**. `adb devices` shows no connected device.

Runtime: Gradle 8.7, local Android Studio JBR 21.0.10, SDK 34 / build tools 34.0.0, original dependency versions. Byte-verified ASCII build copy and temporary download mirrors; product dependency configuration is unchanged. Desktop baseline remains `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74`, verified source Git blobs, Python 3.12.14 / cryptography 50.0.2.

## Local APK and standard assets

`dist/AegisVaultMobile-v1.5-debug.apk`, **21,105,896 bytes**, SHA-256 `3a02623f1d9b5c44e4b509190e7f95e118f17ef08d43961665934b481dab4412`. Actual APK packageName, versionName 1.5, versionCode 7, minSdk 26 and existing signer were verified by prepare-release.py before manifest generation. Existing test signer SHA-256 `b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9` retained.

`dist/update-manifest.json` is generated from the actual verified APK. `dist/SHA256SUMS.txt` covers both APK and manifest. Copies in [validation/1.5](validation/1.5/) document this local artifact; a future workflow APK will have its own digest.

Evidence: [build/source hashes](validation/1.5/local-build.json), [test XML](validation/1.5/test-results/), [lint XML](validation/1.5/lint-results-debug.xml), [lint HTML](validation/1.5/lint-results-debug.html), [build log](validation/1.5/build.txt), [Python update tests](validation/1.5/update-contract-tests.txt), [desktop receipt](validation/1.5/desktop-verify.json), [protocol regression](validation/1.5/protocol-regression/desktop-verify.json), [migration tests](validation/1.5/migration-tests.txt), [device check](validation/1.5/adb-devices.txt).

## Unverified device gates

Real Android APK archive/certificate extraction, private cache and FileProvider URI grants, unknown-app install permission and return flow, Android installer confirmation/cancellation, actual upgrade from an installed same-signer APK, API 26/28+ branches, device-policy restrictions, settings scrolling/accessibility/themes/minimum screens, screen/clipboard/lifecycle behavior, process death and background/cancellation under actual network conditions remain **NOT RUN**. This local build and host metadata smoke do not replace those gates. #5 remains open for its device acceptance.

中文：1.5 已实现设置内检查、下载校验和系统安装入口。79 项 Android/JVM 测试、7 项更新合约测试、6 项迁移测试、783 条真实桌面比对及真实 GitHub 元数据检查通过；lint 0 错误、40 条原有警告。未连接设备，实际手机更新安装和权限流程未验证；此处为发布前本地记录，发布状态另以 GitHub 为准。
