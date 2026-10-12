# Mobile 1.1 非文件安全合约 / Non-file security contract

状态：1.1，versionCode 3；发布说明见 [v1.1](releases/v1.1.md)。历史 1.0.1/versionCode 2 保持原记录。
Status: 1.1, versionCode 3; see the version-specific release notes. Historical 1.0.1 records are unchanged.

## 冻结基线 / Frozen baselines

- Desktop: `c6664e2b7e6a11b7dfb55f0881136131e1d2bc74`. Exact source, fixed text vector and SHA-256 manifest are in `contracts/desktop-c6664e2/`. These are reference snapshots, not Android runtime code.
- Android: `fd9811b82453d1feebecfe3e167b32a9b30acb1a`. `app/src/test/resources/android-historical.json` preserves the audit's original JSON AGV1 token plus deterministic old AES/AK# migration samples. All plaintext/passwords are artificial and public. AK# embeds a password and remains high risk.

正式桌面封装是 `AGV1.` + 无填充 URL Base64(`AGVTEXT\x01` + BE32 header length + 原始 JSON header + ciphertext/tag)。JSON writer 按键排序、紧凑 UTF-8、拒绝重复键/非有限值。原始 header bytes 全部作为 GCM AAD；AES-256-GCM nonce=12、tag=16。头部 format=aegisvault.text、version=1、algorithm=AES-256-GCM、created_at、nonce 和 kdf 必须校验。桌面 KDF 使用 type=scrypt、头部 salt 为标准 Base64；N=32768,r=8,p=1,key=32 是默认值。

Desktop uses that binary envelope and authenticates the exact header bytes as AAD. Mobile 1.1 STILL writes URL Base64 of a complete JSON object with a second URL Base64 ciphertext inside; AAD is null. Both use `AGV1.` but **cannot decrypt each other's tokens**. Legacy AES and AK# readers remain in 1.1. Algorithm/type checks do not provide header authentication. No cross-platform compatibility claim or new writer is made in this update.

## 资源预算 / Resource budgets

| Category | Desktop baseline | Mobile 1.1 |
|---|---:|---:|
| Plaintext UTF-8 bytes AND UTF-16 units | 1,507,294 | 1,048,576 |
| Encoded text/token UTF-8 bytes AND UTF-16 units | 2,097,152 | 2,097,152 ASCII characters |
| Header / metadata | 65,536 header bytes | 16,384 metadata wire bytes, JSON nesting <=32 |
| AES ciphertext including tag | Binary envelope payload | 1,048,592 bytes |
| Inner ciphertext URL Base64 | None | 1,398,123 characters |
| Entire decoded JSON envelope | Header + binary payload | 1,414,507 bytes |
| scrypt memory `128*N*r` | <=256 MiB | <=64 MiB |
| scrypt work `N*r*p` | <=4,194,304 | <=4,194,304 |

桌面 JSON Lines IPC 特有的 16 MiB line budget 不移植到 Android。固定 32-byte key；N 必须是 2^14..2^20 的2次幂，r=1..16,p=1..8。整数 JSON 字段不接受字符串、浮点、布尔、null 或溢出转换。实际 salt=16..64，且现有 salt_len 要与实际长度匹配。全部组合乘法用 Long，派生前拒绝；不执行超预算 KDF，不 catch OOM 代替预检。

The Android 64 MiB limit is provisional, to leave heap headroom for BC's additional arrays, JSON and UI; it is a policy cap, NOT device-measured peak memory or a guarantee against all allocation failures. Desktop-valid KDFs above 64 MiB are outside Mobile 1.1's accepted boundary. Defaults remain 32 MiB. Device profiling is pending; 1.2 must document any remaining cross-platform resource differences.

1.1 双层 Base64 不能承载桌面全部最大正文边界。无填充 Base64 长度为 ceil(4*n/3)，故 Mobile 1 MiB 明文 +16-byte tag，经内层编码并预留16 KiB metadata，再经外层编码，含前缀的最坏 token 长度 <=1,886,015（保守取整），小于2 MiB。旧32 KiB整个JSON限制被删除，30 KiB普通文本可加密。Base64 编码只接受 <=1 MiB明文；解码同时限制输入2 MiB和预估输出1 MiB，不能通过大编码输入绕过输出限制。

The Mobile 1 MiB UTF-8/UTF-16 budget also applies to supplied or AK#-embedded passwords before hashing; passwords are not trimmed or normalized. Inputs are counted before UTF-8 allocation, encoded tokens before trim/decoding, and decoder output is estimated before padding/decoding. JSON wire metadata and nesting are checked before JSONObject builds its object graph, KDF types/cost and actual salt before derivation, ciphertext before AES output allocation. Metadata counting excludes only the top-level literal ciphertext value (escaped property spelling is conservatively counted as metadata). Unpaired surrogates are rejected in plaintext/password/Base64 encode; valid Unicode remains unchanged. Whitespace rejection, trim, optional Base64 padding and authenticated plaintext UTF-8 behavior otherwise remain for 1.3.

## 任务合约与错误 / Tasks and errors

操作同步占用 busy/generation，重复点击忽略；清空、启用自动清空时切后台、销毁、编辑输入/口令、切模式和清除结果使旧 generation 永久失效并取消协程。原生 crypto 可能继续计算，但旧结果/失败/finally 永不提交。设置更新可保留有效任务；完成只 patch 当前状态的 result/message/busy，不恢复 snapshot 输入、口令、模式或设置。CancellationException 原样重抛，finally 只释放本 generation 的 busy。

Domain mapping: EMPTY_TEXT/EMPTY_PASSWORD = invalid input; BASE64_INVALID/UTF8_INVALID = input encoding; PROTOCOL = malformed/unsupported current format or invalid KDF; AUTH = wrong password/tamper; RESOURCE = budget exceeded; INTERNAL = unavailable crypto provider. Cancellation remains coroutine cancellation and creates no failure message. Resource/internal errors have English and Chinese UI resources. Unsupported-format and protocol-corruption subcategories remain grouped under PROTOCOL until 1.2. Settings recovery is deferred to 1.3.

## 后续范围 / Migration plan

- **1.2**: Desktop binary envelope, exact header AAD, canonical writer/strict reader and real bidirectional vectors. First provide explicit bounded offline import/migration of old Android JSON/AES/AK#; only then remove automatic weak/legacy fallback. Keep original ciphertext and migrate copies. 1.1 fixtures define the minimum migration corpus. Recalculate the single-layer envelope capacity and revisit Android KDF budget using device evidence.
- **1.3**: strict/ignore-ASCII-whitespace Base64 modes, padding and complete empty/whitespace/Unicode semantics; resilient settings enum/storage recovery.
- **1.4**: non-file acceptance and release hardening, API26/newer-device lifecycle and safe-boundary profiling, full protocol/error corpus and independent acceptance. No automatic publication.

文件加解密、文件Base64、批处理、路径、最近文件、UI重设计全部排除。Files, file Base64, batch/path/recent-file work and UI redesign remain excluded.
