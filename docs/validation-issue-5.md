# Issue #5 Android lifecycle acceptance

2026-10-15, baseline `ae4d6e41498c214e2bcea5940850d7b1ce3f33e3` (released 1.5, versionCode 7).

The generation/job invalidation fix already exists in the released production ViewModel. This change adds the missing Android instrumentation coverage and a CI emulator job; it does not change product behavior or dependency versions.

## Physical-device acceptance

The user subsequently confirmed that physical-phone verification passed and explicitly requested closure of issue #5. This is user-reported manual acceptance, separate from the emulator logs below. No phone model, Android version, tested APK digest or raw device log was supplied; none is inferred. This confirmation applies to issue #5 and does not establish unrelated updater/installation acceptance.

## Android results

**7 tests passed, 0 failures/errors/skips** on Android 11 / API 30 / x86_64, using the real Android Main/Default dispatchers, production `AegisVaultViewModel`, production `AegisVaultRoute`, and `MainActivity`. The clear action is clicked through Compose semantics. ActivityScenario drives actual ON_STOP and destruction, including the real ViewModelStore `onCleared` callback.

The operation dependency is a deterministic gated worker that deliberately continues after cancellation. Settings use an in-memory repository. These tests exercise lifecycle/task races, not encryption performance, DataStore disk behavior, process death or APK installation.

| Test | Verified behavior |
| --- | --- |
| Clear button | Late work cannot restore cleared input, password, result or message. |
| Activity stopped | Default auto-clear runs through the production lifecycle observer; late work stays discarded after resume. |
| Activity destroyed | Destruction clears even with auto-clear disabled; subsequent events cannot restart work. |
| Input/password/mode/options/settings edited | Stale completion cannot overwrite the edited state. Individual invalidation events also retain their existing JVM regression coverage. |
| Rapid repeat / settings | Ten immediate operations start one worker; valid completion preserves newer settings. |
| Out-of-order success/error/finally | An obsolete finally cannot reset the newer busy flag, and obsolete failure cannot replace newer success. |
| Current error / auto-clear disabled | Stop preserves the task and input when configured; current failure releases busy. |

Evidence: [native test XML](validation/issue-5/android-30-tests.xml), [Android runner log](validation/issue-5/android-30-test-runner.txt), [connected-test build log](validation/issue-5/connected-tests.txt), [device properties](validation/issue-5/device.json), [source hash](validation/issue-5/source.json).

## Regression results

- Existing JVM suite: **79 methods, 78 passed, 1 skipped, 0 failures/errors**. The opt-in live GitHub smoke remains skipped; this task does not need external update traffic. [Native XML](validation/issue-5/unit-results/).
- `testDebugUnitTest lintDebug assembleDebug`: **passed**. Lint has **0 errors / 40 existing warnings**. [Build log](validation/issue-5/regression.txt), [lint XML](validation/issue-5/lint-results-debug.xml).
- Actual `debugAndroidTestRuntimeClasspath` resolves lifecycle **2.8.2** and coroutines **1.8.1**, matching the production declarations. [Dependency report](validation/issue-5/dependencies.txt).
- Updated CI workflow passes actionlint. [Local check](validation/issue-5/workflow-check.txt). Build input hashes match the ASCII copy: [SHA-256 manifest](validation/issue-5/build-input-sha256.json).

The local AVD was started headlessly with hardware acceleration, `-read-only -no-snapshot -no-window -no-audio -gpu swiftshader_indirect -port 5580`. It is an **Android Emulator**, not a physical handset. No physical-phone result is claimed. Existing historical validation records remain unchanged.

## Reproduce

Use the repository's declared SDK 34/build tools 34.0.0 and a booted API 30 emulator. Select an explicit test-device serial so another connected device is not modified. The test task installs the debug app and test APK on that selected device.

```powershell
$env:ANDROID_SERIAL = 'emulator-5580'
.\gradlew.bat --no-daemon connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.aegisvault.mobile.ui.TaskLifecycleDeviceTest'
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Local Windows build used Android Studio JBR 21.0.10 / Gradle 8.7 and a source-matching ASCII temporary copy because of the non-ASCII workspace path. Temporary repository mirrors were applied by an external Gradle init script only. CI uses JDK 17, the declared dependencies, and a pinned emulator-runner action. The new CI job uploads native Android test reports; its remote execution is not claimed by this local record.

ActivityScenario's lifecycle operations are documented by [Android's activity testing guide](https://developer.android.com/guide/components/activities/testing). Emulator CI configuration follows the [runner's own documentation](https://github.com/ReactiveCircus/android-emulator-runner).
