# 1.6 local validation / 本地验证

2026-10-25. **Pre-publication local validation record**, versionName **1.6**, versionCode **8**; baseline public v1.5 at `ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`. Implements issues #16–#20; [behavior and boundaries](fixes-1.6.md). The original local QA results below remain distinct from the [published release](https://github.com/Qrzzzz/AegisVaultMobile/releases/tag/v1.6), remote CI and release-signer verification.

## Results

| Check | Actual result |
| --- | --- |
| Declared Gradle JVM suite | **91 methods: 90 passed, 1 skipped, 0 failures/errors**. The optional live GitHub smoke stays skipped. |
| Android API 34 / AOSP x86_64 | **14 instrumentation tests passed, 0 skipped, 0 failures/errors**. |
| Android API 30 / AOSP x86_64 | **13 instrumentation tests passed, 0 skipped, 0 failures/errors**. The API 33+ framework-locale test is excluded by SdkSuppress. |
| API 30 process cold start | **Passed**: real UI selected Chinese, force-stopped the app, relaunched and verified the Chinese input resource. |
| Lint / debug app / test APK build | **Passed**, lint **0 errors / 38 warnings**. |
| Python migration / update contract | **6 + 7 tests passed**. The verifier's artificial releases are test fixtures. |
| Product version and notes | `prepare-release.py --check-only --tag v1.6` passed, versionCode 8. This mode does not verify APK signing. |
| CI/release YAML | actionlint 1.7.12 passed. This local record predates the release PR and remote CI. |

The JVM suite includes 100 edit/retry requests with one executing worker and only the latest waiting request, cross-ViewModel execution gating after destruction, cancellation before entry, stale error/finally protection, clipboard identity/deadline/timeout changes and foreground retry, and old/framework locale migration policy. Previous cryptographic/protocol, input semantics, DataStore recovery and update regressions remain in the full suite.

The Android suite uses the real AppCompat MainActivity, production Route/ViewModel, Android Main/Default dispatchers, ActivityScenario recreation/stop/destruction and the actual system ClipboardManager. It verifies real Chinese/English resource switching, API 33+ framework reset to Follow system, session and active-task retention through configuration recreation, actual sensitive clip extras and same-text replacement preservation, and a real 30-second clipboard deadline surviving recreation. The non-cooperative task cases inject a deterministically gated worker; they do not measure concurrent real KDF memory or establish physical-phone OOM acceptance.

Evidence: [JVM XML](validation/1.6/unit-results/), [build log](validation/1.6/build.txt), [lint XML](validation/1.6/lint-results-debug.xml), [API 34 native XML and runner/device logs](validation/1.6/api34/), [API 30 native XML and runner/device logs](validation/1.6/api30/), [actual cold-start receipt and UI hierarchies](validation/1.6/api30/cold-start/), [migration](validation/1.6/migration-tests.txt), [update contracts](validation/1.6/update-contract-tests.txt), [version check](validation/1.6/version-check.txt), [workflow check](validation/1.6/workflow-check.txt).

## Build and identity

Gradle 8.7, Kotlin 1.9.24, JBR 21.0.10, SDK 34 / build tools 34.0.0, original dependency versions. Fresh temporary SDK tools/platforms were installed because the previous SDK location was unavailable. Source-matching ASCII build copy, external temporary repository mirrors; no product repository/dependency upgrades. [65 build-input hashes](validation/1.6/build-input-sha256.json) · [resolved instrumentation dependencies](validation/1.6/dependencies.txt).

The actual local APK reports package `com.aegisvault.mobile`, versionName 1.6, versionCode 8, minSdk 26, targetSdk 34. [APK identity](validation/1.6/local-apk-identity.txt).

**Local QA signing differs from the public channel.** The former local debug keystore was unavailable; Gradle generated a new development keystore. Its public certificate SHA-256 is `efeffa848cf56b90dc2e3c3ce7e2bf9fa0e1564364ed5c9240be2db16ebefeaf`. The unchanged expected release signer remains `b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9`. The local QA APK can be used on the isolated emulators, but cannot cover-install the published app or serve as a standard update artifact. No production update manifest was generated and no signer checks were weakened. Formal publication must use the existing release workflow's retained signing identity. [Certificate verification](validation/1.6/local-apk-signer.txt) · [local artifact path/size/hash and build receipt](validation/1.6/local-build.json).

## Evidence boundaries

The local emulator instances are dedicated, headless, hardware-accelerated AVDs with read-only/no-snapshot startup. They contain synthetic test inputs. Their results are emulator evidence, not physical-phone confirmation. API 30 cold-start language persistence was additionally exercised through actual UI and force-stop/relaunch, using the [emulator-only probe](validation/1.6/cold-start-language-probe.py). Physical-device low-memory/OEM behavior, API 26 clipboard fallback, production-signed upgrade and installer flows are not established by these tests. The earlier user-reported #5 phone pass remains historical evidence for #5.

This record was collected before commit, push, tag, GitHub Release and issue closure. Those later actions are evidenced separately by the release PR, Actions and published assets. Existing #5 acceptance/audit files were preserved; its instrumentation tests were extended for the bounded execution behavior and configuration recreation. Historical validation records remain unchanged.

Reproduce on a selected QA emulator with `ANDROID_SERIAL` set explicitly: `gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest`. CI now uses API 30/34 matrix jobs. No install test should use the local QA APK to replace a public-signed installation.
