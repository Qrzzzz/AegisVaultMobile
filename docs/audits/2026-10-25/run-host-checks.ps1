param(
    [Parameter(Mandatory = $true)][string]$DependencyDir,
    [string]$Java = 'D:\Programme Files\Android Studio\jbr\bin\java.exe',
    [string]$KotlinLib = 'C:\Users\qrzzz\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\lib',
    [string]$OutputDir = (Join-Path $env:TEMP 'aegis-audit-20261025-replay')
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path

# Host checks only. No Android SDK, device, installer, public GitHub API or source edits.
foreach ($entry in (Get-Content (Join-Path $PSScriptRoot 'source-sha256.json') -Raw | ConvertFrom-Json)) {
    $actual = (Get-FileHash -LiteralPath (Join-Path $root $entry.path)).Hash.ToLower()
    if ($actual -ne $entry.sha256) { throw "Audit source changed: $($entry.path)" }
}
foreach ($entry in (Get-Content (Join-Path $PSScriptRoot 'probe-dependency-sha256.json') -Raw | ConvertFrom-Json)) {
    $actual = (Get-FileHash -LiteralPath (Join-Path $DependencyDir $entry.file)).Hash.ToLower()
    if ($actual -ne $entry.sha256) { throw "Dependency differs: $($entry.file)" }
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$jars = @('coroutines.jar', 'coroutines-test.jar', 'annotation.jar', 'bcprov.jar',
    'junit.jar', 'hamcrest.jar', 'json.jar', 'viewmodel/classes.jar')
$classpath = (@((Join-Path $KotlinLib 'kotlin-stdlib-1.9.22.jar')) +
    @($jars | ForEach-Object { Join-Path $DependencyDir $_ })) -join ';'
$sources = @(Get-ChildItem (Join-Path $root 'app/src/main/java/com/aegisvault/mobile/core') -Filter '*.kt' |
    ForEach-Object { $_.FullName })
$sources += @('ui/AegisVaultViewModel.kt', 'ui/AegisVaultUiState.kt', 'ui/AegisVaultUiEvent.kt',
    'data/AppSettings.kt', 'data/AppSettingsRepository.kt', 'update/UpdateContract.kt',
    'update/GitHubUpdateRepository.kt') | ForEach-Object { Join-Path $root "app/src/main/java/com/aegisvault/mobile/$_" }
$sources += @(Get-ChildItem (Join-Path $root 'app/src/test/java/com/aegisvault/mobile/core') -Filter '*.kt' |
    ForEach-Object { $_.FullName })
$sources += @('UpdateContractTest.kt', 'UpdateTransportTest.kt') |
    ForEach-Object { Join-Path $root "app/src/test/java/com/aegisvault/mobile/update/$_" }
$sources += @(Get-ChildItem (Join-Path $PSScriptRoot 'probes') -Filter '*.kt' | ForEach-Object { $_.FullName })
$compiled = Join-Path $OutputDir 'audit.jar'
& $Java -cp "$KotlinLib\*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect `
    -jvm-target 17 -classpath $classpath -d $compiled @sources *> (Join-Path $OutputDir 'compile.txt')
if ($LASTEXITCODE -ne 0) {
    Get-Content (Join-Path $OutputDir 'compile.txt') -Tail 60
    throw 'Standalone audit compilation failed'
}
$runtimeClasspath = "$compiled;$classpath;$(Join-Path $root 'app/src/test/resources')"
function Run-Check([string]$Name, [string[]]$Arguments) {
    $log = Join-Path $OutputDir "$Name.txt"
    & $Java -Xmx768m -cp $runtimeClasspath @Arguments *> $log
    $status = $LASTEXITCODE
    Get-Content $log -Tail 25
    if ($status -ne 0) { throw "$Name failed (exit $status)" }
}
Run-Check 'task-overlap' @('com.aegisvault.mobile.ui.TaskOverlapProbeKt')
Run-Check 'clipboard-timer-model' @('audit.ClipboardTimerModelProbeKt')
Run-Check 'jvm-regression' @('org.junit.runner.JUnitCore',
    'com.aegisvault.mobile.core.AegisVaultEngineTest', 'com.aegisvault.mobile.core.ResourceSafetyTest',
    'com.aegisvault.mobile.core.TextProtocolTest', 'com.aegisvault.mobile.core.TextSemanticsTest',
    'com.aegisvault.mobile.update.UpdateContractTest', 'com.aegisvault.mobile.update.UpdateTransportTest')
