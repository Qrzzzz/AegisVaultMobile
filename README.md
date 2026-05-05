# AegisVault Mobile

AegisVault Mobile is a focused Android text utility for AES-256-GCM encryption/decryption and Base64 encoding/decoding. It keeps the interface small, bilingual, and offline-first.

Base64 is encoding, not encryption. It changes representation only and does not protect data.

## Features

- AES-256-GCM text encryption with `AGV1.` tokens.
- Password-based key derivation with scrypt, random salt, and random nonce.
- Modern-token decryption plus legacy text compatibility.
- Base64 text encode/decode.
- English and Simplified Chinese UI.
- Light/dark mode via system appearance.
- Clipboard paste/copy helpers.

## Security Notes

- The app does not send data over the network.
- Android backup is disabled for the app.
- Passwords are never intentionally stored by the app.
- Clipboard contents may still be visible to the operating system or other privileged software.
- Losing the password means encrypted data cannot be recovered.

## Requirements

- Android Studio or Android SDK command-line tools.
- JDK 17.
- Android Gradle Plugin 8.5.2.
- Gradle wrapper included in this repository.

## Build

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a named local package:

```powershell
.\scripts\package-apk.ps1
```

The copied package is generated at:

```text
dist/AegisVaultMobile-v1.0.1-debug.apk
```

## GitHub Release Checklist

- Run unit tests: `.\gradlew.bat testDebugUnitTest`
- Build the APK: `.\scripts\package-apk.ps1`
- Attach `dist/AegisVaultMobile-v1.0.1-debug.apk` to a GitHub Release for testing builds.
- For production distribution, create a private signing key and build a signed release APK or AAB.

## Project Structure

```text
app/
  src/main/java/com/aegisvault/mobile/
    core/        AES and Base64 engine
    data/        language preferences
    ui/theme/    Compose theme and background
  src/main/res/  icons, strings, locale config
  src/test/      JVM unit tests
.github/workflows/android.yml
scripts/package-apk.ps1
```

## Development Credits

This repository was prepared with assistance from Codex (ChatGPT 5.5) and Gemini 3.1 Pro.

## License

MIT License.
