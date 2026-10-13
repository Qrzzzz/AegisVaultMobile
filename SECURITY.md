# Security Policy

## Supported Versions

Only the latest public version is supported while the project is in early release.

## Reporting a Vulnerability

Please open a private security advisory on GitHub if the repository is hosted there. If private advisories are unavailable, contact the maintainer directly before publishing technical details.

Include:

- A clear description of the issue.
- Steps to reproduce.
- Affected version or commit.
- Any proof-of-concept payloads needed to validate the report.

## Scope

In scope:

- Incorrect encryption or decryption behavior.
- Authentication bypass or tamper-detection failure.
- Accidental persistence of sensitive app data.

Out of scope:

- Attacks requiring a compromised device.
- Clipboard monitoring by the operating system or privileged apps.
- Recovery of data when the user has lost the password.


## Manual app updates (1.5)

Encryption/Base64 inputs and passwords stay offline. Only explicit update actions connect to the fixed public GitHub repository/CDN, without credentials or sensitive editor data. APK byte count/hash, package/version/minSdk and installed certificate matching precede the system installer; no silent installation is performed. REQUEST_INSTALL_PACKAGES is used only for this user-initiated self-update flow. The existing debug test signer is retained; this is not a production-signing transition. See [the update contract](docs/update-1.5.md) for limits and device acceptance boundaries.
