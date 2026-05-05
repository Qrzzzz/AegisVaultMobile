param(
    [ValidateSet("debug", "release")]
    [string] $Variant = "debug"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$distDir = Join-Path $projectRoot "dist"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

$gradleCommand = if ($env:AEGIS_GRADLE_CMD) {
    $env:AEGIS_GRADLE_CMD
} elseif (Test-Path (Join-Path $projectRoot "gradlew.bat")) {
    ".\gradlew.bat"
} else {
    "gradle.bat"
}

Push-Location $projectRoot
try {
    if ($Variant -eq "release") {
        & $gradleCommand :app:assembleRelease
        $source = Join-Path $projectRoot "app\build\outputs\apk\release\app-release-unsigned.apk"
        $target = Join-Path $distDir "AegisVaultMobile-v1.0.1-release-unsigned.apk"
    } else {
        & $gradleCommand :app:assembleDebug
        $source = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
        $target = Join-Path $distDir "AegisVaultMobile-v1.0.1-debug.apk"
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }

    Copy-Item -LiteralPath $source -Destination $target -Force
    Write-Host "Packaged $target"
} finally {
    Pop-Location
}
