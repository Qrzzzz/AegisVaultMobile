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
    } else {
        & $gradleCommand :app:assembleDebug
        $source = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }

    $metadataPath = Join-Path (Split-Path -Parent $source) "output-metadata.json"
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $productVersion = [string]$metadata.elements[0].versionName
    if ($productVersion -notmatch '^\d+\.\d+$') { throw "Expected product version major.minor in APK metadata." }
    $suffix = if ($Variant -eq "release") { "release-unsigned" } else { "debug" }
    $target = Join-Path $distDir "AegisVaultMobile-v$productVersion-$suffix.apk"
    Copy-Item -LiteralPath $source -Destination $target -Force
    Write-Host "Packaged $target"
} finally {
    Pop-Location
}
