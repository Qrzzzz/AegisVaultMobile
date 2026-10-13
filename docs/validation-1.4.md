# 1.4 local acceptance / 本地验收

Product **1.4 / versionCode 6**, validated 2026-10-13 against Android baseline `a1bc86092ae5d4727d75545b6082606e40fdc337` (`v1.3`). These local records were produced before publication; GitHub workflow and release state provide separate publication evidence.

Local non-file backend checks passed. Physical-device acceptance is **NOT RUN**; this local debug test build is not completed device acceptance.

| Area | Evidence and result |
| --- | --- |
| Settings #10 | 12 settings tests using actual Preferences DataStore 1.1.1, plus 3 ViewModel notice/startup tests; PASS |
| Actual settings disk reading | Owner closed and new DataStore reads unknown language/wrong-type theme from actual protobuf; valid screen/leave/clipboard settings survive; edit succeeds; PASS |
| Recovery | Missing values, 36 legal enum combinations, unknown/old values, all five wrong-type values, actual corrupt protobuf, real file read/write I/O, later successful edits and retained subscription; PASS |
| Failure boundaries | Read/write cancellation and programming errors propagate; separate injected stream I/O around actual DataStore retains last known safety values; PASS |
| Notice/lifecycle | Bilingual resources, dedicated recovery state survives actions/clearing until acknowledgement; pending startup leave clears sensitive inputs, loaded false preference respected; PASS locally |
| AGV1 text | Exact frozen writer vector, raw-header AAD, strict JSON/types/UTF-8, authentication/tamper/malformed envelope regression; PASS |
| Actual desktop exchange | 783 actual Android vs actual frozen desktop CryptoService comparisons; all four Base64 combinations and exact empty/whitespace/Unicode semantics; four new Android AGV1 tokens read by desktop; PASS |
| Historical migration | 6 offline migration tests; prior desktop/migration corpus rechecked, five Android tokens read by actual desktop; whitespace-password migrated token read by both; PASS |
| Resources | Existing encoded/plaintext/header/ciphertext limits, Long KDF validation, 1 MiB and Unicode boundaries; unsafe costs are validated without expensive derivation; PASS |
| Task lifecycle | All 1.1–1.3 duplicate/stale/reverse completion, cancellation, edit/mode/options/clear/leave/destroy and settings-preserving completion tests retained; PASS |
| Physical device | `adb devices`: no connected device; NOT RUN |

Final `testDebugUnitTest lintDebug assembleDebug`: **62 test methods, 0 failures/errors/skips**, lint **0 errors / 40 warnings**, build successful. The original 47 methods remain, plus 15 new settings/startup methods. The existing auto-clear-off test now waits for settings loading before asserting its explicit false choice, matching the new startup protection gate.

Runtime: Gradle 8.7, local Android Studio JBR 21.0.10, SDK 34 / build tools 34.0.0, DataStore 1.1.1; declared dependency versions unchanged. Local build uses a byte-verified ASCII source copy and temporary download mirrors; product repository dependency configuration is untouched. Actual desktop source `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74`, every exported Python Git blob verified; Python 3.12.14 / cryptography 50.0.2.

APK: `dist/AegisVaultMobile-v1.4-debug.apk`, **21,005,902 bytes**, SHA-256 `622f9b6ae92cbc581dad6b63dfb3889d836410c40c378ea01a66a50bc08cdbd5`. Actual APK versionName **1.4**, versionCode **6**, signature verified with `prepare-release.py`. Existing signer SHA-256 `b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9` retained. `dist/SHA256SUMS.txt` contains this local APK digest. Publication workflow was not run.

Evidence: [local build/source hashes](validation/1.4/local-build.json), [test XML](validation/1.4/test-results/), [lint XML](validation/1.4/lint-results-debug.xml), [build output](validation/1.4/build.txt), [actual desktop receipt](validation/1.4/desktop-verify.json), [protocol/migration receipt](validation/1.4/protocol-regression/desktop-verify.json), [migration tests](validation/1.4/migration-tests.txt), [device check](validation/1.4/adb-devices.txt). Exchange inputs/passwords are artificial public test fixtures.

Compared against v1.3, production core, all protocol/semantic/historical fixtures, migration/interoperability scripts, frozen contracts and workflows are unchanged. Only settings recovery, the minimal notice/startup integration, version metadata, related tests and version documents change. Existing 1.1–1.3 historical evidence is untouched. See [recovery contract and exact defaults](settings-1.4.md).

Unverified device gates: real Android sandbox read/write/corruption handling, process death/restart, screen/recents protection, lifecycle, clipboard/keyboard/whitespace interaction, bilingual notice display/accessibility, rotation and minimum screen layout, installed v1.3-to-1.4 upgrade/preferences behavior, and on-device memory/performance. Local JVM DataStore/protobuf and injected filesystem faults establish local behavior only. Recovery notices last within the current process; protective replacement preferences persist, while notice status itself is not persisted across process death.

中文：设置 #10 和非文件后端本地回归通过，62 项 Android 测试、6 项迁移测试、783 条真实桌面比对及 APK 构建通过。无连接设备，真机验收为 NOT RUN。未新增文件功能、未重做 UI，此处是发布前的本地证据；发布与 issue 状态另以 GitHub 记录为准。
