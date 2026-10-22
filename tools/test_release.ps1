# Preserve the preceding test world because GameTest does not remove all out-of-template entities.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$java17 = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot'
if (Test-Path -LiteralPath $java17) { $env:JAVA_HOME = $java17 }
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
$worldPath = Join-Path $buildRoot 'gametest-redesign\world'
if (Test-Path -LiteralPath $worldPath) {
    $worldPath = (Resolve-Path -LiteralPath $worldPath).Path
    $backupRoot = Join-Path $buildRoot 'test-world-backups'
    $destination = [System.IO.Path]::GetFullPath((Join-Path $backupRoot ('release-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
    if (-not $worldPath.StartsWith($buildRoot + '\') -or -not $destination.StartsWith($buildRoot + '\')) { throw 'Test world must remain within project/build' }
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    Move-Item -LiteralPath $worldPath -Destination $destination
}
& .\gradlew.bat --offline --console=plain runGameTestServer build
exit $LASTEXITCODE
