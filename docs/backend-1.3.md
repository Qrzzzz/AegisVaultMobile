# 1.3 text semantics / 文本语义

Baseline: actual desktop source `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74`. The added `text-semantics-manifest.json` records the frozen source and test hashes; the original 1.2 manifest and vectors remain unchanged. `scripts/interop-1.3.py` verifies the exported source against Git blobs and invokes the actual desktop CryptoService, while Android JVM tests invoke the actual AegisVaultEngine and export results for independent desktop verification.

| strict | ignore ASCII whitespace | Behavior |
| --- | --- | --- |
| true | false | Reject whitespace; validate standard alphabet/padding |
| true | true | Reject whitespace; strict takes precedence |
| false | false | Validate standard alphabet/padding; whitespace remains invalid |
| false | true | Remove TAB, LF, CR, FF, VT, SPACE only, then validate |

No implicit Base64 trim, MIME decoder, URL alphabet, missing padding or excess padding. Actual reference runtime is Python 3.12.14; nonzero discarded padding bits (`Zh==`, `Zm9=`) remain accepted, as on that desktop runtime. Non-ASCII whitespace is not filtered. Options are transient UI state, not a settings recovery change.

Empty plaintext and empty Base64 input/output are valid. Whitespace-only and edge whitespace data/passwords are preserved exactly; only the empty password is rejected. No Unicode normalization. Unpaired input surrogates are rejected before encoding (`INPUT_INVALID`); decoded malformed UTF-8 is `UTF8_INVALID`; Base64 syntax is `BASE64_INVALID`. The desktop API groups the latter two as `base64.invalid_text`, so the cross-platform comparison records their common category plus native errors. Formal AGV1 boundary whitespace follows the desktop strip set; Base64 data never uses that strip operation.

Android keeps 1 MiB plaintext/password, 2 MiB encoded input, 64 KiB header and 64 MiB KDF memory limits. Encoded UTF-8 budget runs first, then the desktop-compatible decoded-length preflight when the significant character count is divisible by four, then syntax/UTF-8 validation. The preflight excludes the six ASCII whitespace characters when ignore is selected, even if strict subsequently rejects them. Desktop has a larger plaintext limit; format interoperability does not imply equal maximum capacities. Invalid Base64 shape is left to the decoder rather than reported as estimated decoded-size overflow.

Paste accepts legitimate empty/whitespace text; whitespace results remain visible and copyable. Copying an empty result retains the existing no-result message. Changing either option invalidates pending work. Existing generation cancellation, raw-header AAD, strict JSON validation, AGV1-only production reader and isolated historical migration are retained. The migration tool's new-password check now also rejects only the empty string.

中文：空文本与纯空白文本有效，口令仅拒绝空串。四种 Base64 组合遵循上表；六种 ASCII 空白外的 Unicode 空白不会被忽略。资源检查顺序和现有 Android 预算保持不变。未增加文件功能、设置恢复或 UI 重设计。

Frozen `test_base64_input_consistency.py` covers desktop file behavior and is recorded as source context; it is not claimed as an Android file acceptance test. #10 settings recovery and real-device acceptance remain deferred.
