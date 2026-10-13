# Changelog

## 1.3 - 2026-10-13

- Desktop-aligned four-mode Base64 decoding with explicit bilingual controls and standard alphabet/padding validation.
- Empty and whitespace text supported; passwords reject only the empty string, including in offline migration. Unicode is preserved without normalization; malformed input and decoded UTF-8 remain distinct errors.
- Whitespace-preserving paste/copy/use-result, reachable scrolling and option-change cancellation. Existing AGV1-only reader, budgets, dependencies and signing retained.
- 47 Android test methods, 783 actual desktop comparisons and 6 migration tests; physical-device acceptance NOT RUN. Settings recovery (#10) remains deferred.
- versionName 1.3 / versionCode 5; debug-signed test release.

## 1.2 - 2026-10-13

- Formal desktop AGV1 binary text envelope, canonical UTF-8 writer, exact raw-header AAD and strict independent JSON/UTF-8 reader.
- Explicit bounded offline Android JSON/AES/AK migration; production legacy fallback, SHA256 key derivation and AK branches removed.
- Separate 64 KiB header / 1 MiB plaintext budgets and single-layer token capacity; Android 64 MiB KDF policy retained.
- Frozen fixed-vector equality, actual desktop CryptoService bidirectional exchange and historical migration corpus validation.
- versionName 1.2 / versionCode 4; existing signing and workflow contracts preserved. Debug-signed test release; physical-device acceptance remains NOT RUN.

## 1.1 - 2026-10-12

- Reserve one effective task synchronously; cancel and discard obsolete results on clear, edit, mode change, auto-clear leave and destruction. Completion patches only current result/message/busy.
- Validate real JSON types, 32-byte keys, actual salt and Long scrypt cost before derivation; provisional Android memory cap 64 MiB.
- Separate plaintext, encoded, metadata and decoded-output budgets; support 30 KiB text and reject oversized inputs before large allocations/KDF. Add Chinese/English resource errors.
- Freeze desktop contract and historical Android migration samples; keep current writer/readers. Desktop interoperability remains planned for 1.2, input/settings alignment for 1.3, non-file acceptance for 1.4.
- Added CI and tag-triggered release gates, persistent test signing, exact APK version/signer checks and remote asset digest verification.
- Product version is now major.minor (1.1), versionCode 3. Package filenames read actual APK output metadata. Historical releases and dependency versions stay unchanged.

## 1.0.1 - 2026-05-05

- Prepared a clean Android repository layout for GitHub.
- Added Gradle wrapper, Android CI, package script, and focused unit tests.
- Fixed Simplified Chinese resources and app metadata for a clean public release.
- Disabled Android backup for safer default behavior.

## 1.0.0

- Initial Android implementation with AES text tools, Base64 tools, and bilingual UI.
