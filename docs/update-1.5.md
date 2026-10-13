# GitHub Android updates v1 / 更新合约

Product 1.5, versionCode 7. Entry: **Settings → App updates / 设置 → 软件更新**.

The user manually checks, downloads and verifies an APK, then opens Android's installer. If Android has not permitted this app to install packages, the install button opens the system per-app permission screen; return and press install again. Android owns final confirmation, installation and cancellation. There is no silent installation or install-success claim merely because an intent was launched.

## Release source and ordering

The fixed public source is `https://api.github.com/repos/Qrzzzz/AegisVaultMobile/releases/latest`, using GET, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2026-03-10` and `User-Agent: AegisVaultMobile/{version}`. No authentication token is stored or sent. GitHub's explicitly designated Latest stable release is used; draft/prerelease responses are rejected. Titles/body prose, upload order and floating point versions are not used as version information. See the [official Releases API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

Existing product convention is retained: versionName `MAJOR.MINOR`, tag `vMAJOR.MINOR`, no leading zeros, each numeric component within signed Int range. Compare numeric components (`1.10 > 1.9`). A newer tag must also have strictly greater Android versionCode; same/older tags never trigger downgrade. A local development build newer than the public release is displayed distinctly. Android versionCode is a real positive integer up to 2147483647. Same-tag rebuild updates and versionName prerelease/patch suffixes are outside schema v1.

## Machine-readable artifact

From 1.5 onward, exactly these release assets are produced:

- `AegisVaultMobile-v{version}-debug.apk`
- `update-manifest.json`
- `SHA256SUMS.txt` covering both APK and update-manifest.json

The [JSON Schema](contracts/android-update-v1.schema.json) and [shared artificial fixture](../app/src/test/resources/update-manifest-v1.json) define schemaVersion 1. The publisher generates the manifest only after checking actual APK version, declared minSdk and existing signer. It records applicationId, versionName, versionCode, minSdk, releaseTag and APK name/size/SHA-256/signing-certificate SHA-256. The app applies additional semantic equality checks between tag, version, asset name, GitHub asset metadata and manifest; JSON Schema alone cannot express these cross-field checks. Unknown additional fields are ignored for additive compatibility; an unknown schema version fails closed.

Strict JSON/UTF-8 rejects duplicate keys, coercible strings/floats/booleans instead of integer fields and malformed data. New releases lacking the contract are reported as unsupported. Historical same/older releases can be recognized without a manifest, and the release verifier remains compatible with their two-asset format. No existing published tags/releases are changed.

## Verification and installation

The manifest's exact byte count and SHA-256 must match GitHub's uploaded asset metadata. APK name/size/SHA-256 must agree with both the manifest and GitHub metadata; the initial browser_download_url must equal the fixed repository/tag/asset URL. Signed APKs must keep the current single test signer. A signing-key rotation or production signer/channel transition needs a separately designed contract; this implementation does not weaken certificate matching to accept one.

Downloads stream into a unique private cache file; byte count and SHA-256 are checked before promotion. Android PackageManager then reads the actual archive's packageName, versionName, versionCode, minSdk and signing certificate. The archive must match the manifest, exceed the installed code and have the same installed certificate. The exact APK is verified again before sharing it with the installer. A completed matching cache is reverified and can be reused; failure/cancellation deletes that attempt's partial file. App-owned partial files older than 24 hours and superseded completed APKs are cleaned within the update cache only. Process death does not resume a partial download; a later check/download can reuse a completed verified cache.

FileProvider is non-exported and exposes only `cache/updates/`; only the verified ready APK receives a temporary read grant for Android's installer. No external storage or broad file permission is requested. `INTERNET` and `REQUEST_INSTALL_PACKAGES` are declared; cleartext traffic is disabled. Installation uses the [Android per-app installation permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()) and a system installer intent.

HTTPS uses the default platform trust store and hostname verification. Redirects are manually bounded to six requests and HTTPS hosts github.com, api.github.com, release-assets.githubusercontent.com or objects.githubusercontent.com; credentials/userinfo, fragments and other ports are refused. 15-second connection/read timeouts, 64 KiB metadata, 128 MiB APK, a 60-second metadata body budget and 300-second APK body budget prevent unbounded transfers. HTTP 429 or a 403 rate-limit indicator is reported as rate limiting; other expected network/contract/storage/integrity failures are localized. Cancellation propagates, request/download generations discard stale completions/progress, and only one check/download is active in the updater. No automatic retry flood, startup polling or background scheduler is introduced. Cancelling updates does not cancel crypto operations or erase settings.

## Privacy and evidence boundaries

Cryptographic/Base64 operations and non-sensitive settings still work offline. The update feature never receives text, passwords or results. Only explicit user actions contact GitHub/CDN; GitHub receives ordinary request information such as IP and the app version user agent. No telemetry, account or API key is added.

Local JVM tests exercise the actual parser/repository/ViewModel/streaming HTTP client with artificial response and TLS-connection fixtures. A separate opt-in network smoke exercises the actual unauthenticated production HTTPS client and public GitHub Latest API; it downloads only metadata. Packaging verifies the actual locally built APK and produces its real update manifest. These are distinct from phone installation. Physical-device PackageManager/FileProvider/permission/installer, UI, process death, backgrounding and real upgrade acceptance remain NOT RUN without a device. Ordinary CI skips the opt-in network smoke; deterministic protocol tests remain required.

中文：标准化更新元数据由已验证的实际 APK 生成，客户端和发布验证共用同一版本化约束。下载后校验大小、SHA-256、包名、版本、最低系统版本和签名一致性；只在用户确认时进入 Android 安装界面。加密内容和口令不进入更新网络请求。真实设备安装与权限流程需单独验收，不能用本地 JVM/模拟连接代替。
