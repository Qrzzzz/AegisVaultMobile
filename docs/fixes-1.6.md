# 1.6 fixes / 修复说明

Product versionName **1.6**, versionCode **8**. Baseline: public v1.5, `ae4d6e41498c214e2bcea5940850d7b1ce3f33e3`. Scope: issues #16–#20. See the [versioned release](https://github.com/Qrzzzz/AegisVaultMobile/releases/tag/v1.6) for published artifacts; the local validation record separately identifies its QA signing and evidence boundaries.

## Text task budget (#16)

`TextOperationGate` owns one process-wide coroutine Mutex. The slot encloses the actual worker call and remains held until that call returns or throws, including a non-cooperative cancelled KDF. UI invalidation still immediately clears/edits state and cancels its job. A newer valid request waits asynchronously; an edited/cleared waiting request is cancelled before entering the operation. Each ViewModel retains only its current valid request. The shared slot also covers a replacement ViewModel while its destroyed predecessor is still computing.

Generation checks continue to protect result/error/finally, and cancellation still propagates. No main-thread join, unbounded retry queue or extra parallel KDF is introduced. The existing 64 MiB per-KDF parameter policy, AGV1 bytes, cryptographic parameters and Base64 semantics are unchanged.

## Language (#17)

`MainActivity` is now an AppCompatActivity. Locale application waits for loaded settings, preventing an initial SYSTEM default from undoing the stored selection during recreation. Before API 33, existing DataStore language values drive AppCompat; a failed settings write does not apply the uncommitted language.

On API 33+, a one-time handoff imports the existing explicit preference only if the framework has no per-app selection. Afterwards the framework selection is authoritative, including a system-level reset to Follow system. Explicit in-app language selections update the framework and DataStore; resume synchronizes external changes. A separate SharedPreferences boolean `app_locale_bridge/framework_migrated` records only this handoff. It contains no user text, password or clipboard content; the existing five settings keys are preserved.

## Lifecycle (#19)

The Route checks the hosting Activity's `isChangingConfigurations` before forwarding ON_STOP as OnLeaveApp. Rotation, locale and other configuration recreations retain the current ViewModel/session and active task. Actual backgrounding retains the auto-clear policy, including fail-closed behavior before settings are loaded. Final ViewModel destruction still clears sensitive state and invalidates pending results.

## Clipboard (#18, #20)

Every copied result receives `ClipDescription.EXTRA_IS_SENSITIVE=true` and a random per-copy token. Before clearing, the Android backend checks that token, SHA-256 of the current text and the captured system timestamp when available. A replacement from another app, even with identical text, is preserved. The flag hides previews on supporting system UI; it does not prohibit all clipboard reads.

One process-owned controller replaces the Composition timers. It retains only ownership metadata and monotonic copy time, never a plaintext copy or persisted clipboard record. A new copy replaces the old deadline. Timeout changes recompute from the latest copy time; Off cancels automatic clearing while retaining the manual clear action. A stale snackbar action cannot clear a newer copy. Window-focus callbacks retry an overdue attempt that the platform previously denied; no background polling/service is added.

Process death, forced stop, lost permissions and OEM clipboard restrictions remain best-effort boundaries. No deadline or sensitive text is written to disk to work around them. Devices below API 28 use the existing empty-clip fallback; newer devices use clearPrimaryClip.

## Validation and release

See [actual validation results](validation-1.6.md). CI now runs the lifecycle/locale/clipboard instrumentation suite on API 30 and 34; adding the job is not evidence that remote CI ran. The update schema, dependency versions and expected published signer remain unchanged. A locally generated development signing key cannot replace the existing release identity; the release workflow remains responsible for restoring and verifying that identity before publication.
