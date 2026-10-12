# Android CI and release workflow

Product version names/tags are major.minor (`1.1` / `v1.1`); versionCode must increase for each release. Dependency versions and historical1.0.1 records retain their existing format.

`Android CI` runs unit tests, lint and debug build on main pushes and pull requests, installing the declared SDK34/build-tools34.0.0. `Android Release` runs on pushed version tags, verifies tag/source parity, repeats validation and builds the APK. Only its separate publish job has contents-write permission. Reports are retained as Actions artifacts even on failure.

Release artifacts remain **debug-signed test builds**. The existing1.0.1 test identity is stored in the repository Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64`; never commit the keystore, print its value or silently replace this identity. `prepare-release.py` rejects a different signer and mismatched APK/source/tag versions. CI pull-request APK artifacts use an ephemeral test signer and are not the release assets.

After successful validation, the workflow uploads the APK and `SHA256SUMS.txt` to a draft Release, verifies the exact two asset names and GitHub server-side SHA-256 digests, then publishes and verifies the public state. A failed check leaves publication incomplete; inspect the exact run/release before retrying and do not bypass the signer/digest gate.

For an authorized release: update app version/code, CHANGELOG and `docs/releases/vX.Y.md`; obtain green PR CI, merge, create an annotated `vX.Y` tag on the merged commit and push it. Confirm the release workflow success, public tag/commit, asset digests and latest-release state. Device acceptance and protocol compatibility must be reported separately; workflow success does not prove them.
