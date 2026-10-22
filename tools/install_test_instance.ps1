param([string]$ReleaseDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'build\libs'))
$ErrorActionPreference = 'Stop'
$instanceRoot = [System.IO.Path]::GetFullPath('C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester')
$running = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -and ($_.CommandLine.Replace('/','\') -match [regex]::Escape($instanceRoot)) }
if ($running) { throw 'Save and close Rackworks Tester before installing. No game process or files were changed.' }
$sourceJar = Join-Path $ReleaseDirectory 'automatons-0.2.2.jar'
$expected = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
$modsRoot = (Resolve-Path -LiteralPath (Join-Path $instanceRoot 'mods')).Path
$targetJar = Join-Path $modsRoot 'automatons-0.2.2.jar'
$stagedJar = Join-Path $modsRoot 'automatons-0.2.2.jar.pending'
$backupRoot = [System.IO.Path]::GetFullPath((Join-Path $instanceRoot ('.automatons-release-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
if (-not $backupRoot.StartsWith($instanceRoot + '\')) { throw 'Backup path outside the designated instance' }
$oldJars = @(Get-ChildItem -LiteralPath $modsRoot -Filter 'automatons-*.jar' -File)
foreach ($oldJar in $oldJars) { if ($oldJar.DirectoryName -ne $modsRoot) { throw 'Unexpected mod path' } }
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
foreach ($oldJar in $oldJars) { Copy-Item -LiteralPath $oldJar.FullName -Destination (Join-Path $backupRoot $oldJar.Name) }
Copy-Item -LiteralPath $sourceJar -Destination $stagedJar
if ((Get-FileHash -LiteralPath $stagedJar -Algorithm SHA256).Hash -ne $expected) { throw 'Staged JAR hash mismatch; old mods remain installed' }
try {
    foreach ($oldJar in $oldJars) { Remove-Item -LiteralPath $oldJar.FullName }
    Move-Item -LiteralPath $stagedJar -Destination $targetJar
    if ((Get-FileHash -LiteralPath $targetJar -Algorithm SHA256).Hash -ne $expected) { throw 'Installed JAR hash mismatch' }
} catch {
    if (Test-Path -LiteralPath $targetJar) { Remove-Item -LiteralPath $targetJar }
    foreach ($oldJar in $oldJars) { Copy-Item -LiteralPath (Join-Path $backupRoot $oldJar.Name) -Destination $oldJar.FullName -Force }
    throw
}
Write-Output "Installed $targetJar"
Write-Output "SHA256 $expected"
Write-Output "Previous releases preserved in $backupRoot"

# Migrate the previous stock display height only; retain custom heights and back up each config.
$configPaths = @((Join-Path $instanceRoot 'defaultconfigs\automatons-server.toml'))
$configPaths += @(Get-ChildItem -LiteralPath (Join-Path $instanceRoot 'saves') -Directory | ForEach-Object { Join-Path $_.FullName 'serverconfig\automatons-server.toml' })
foreach ($configPath in $configPaths) {
    if (-not (Test-Path -LiteralPath $configPath)) { continue }
    $full = (Resolve-Path -LiteralPath $configPath).Path
    if (-not $full.StartsWith($instanceRoot + '\')) { throw 'Config outside designated instance' }
    $contents = [IO.File]::ReadAllText($full)
    $updated = [regex]::Replace($contents, '(?m)^(\s*displayHeight\s*=\s*)1\.75([ \t]*)(\r?)$', '${1}1.4${2}${3}')
    if ($updated -ne $contents) {
        $relative = $full.Substring($instanceRoot.Length + 1)
        $backupConfig = Join-Path $backupRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $backupConfig) -Force | Out-Null
        Copy-Item -LiteralPath $full -Destination $backupConfig
        [IO.File]::WriteAllText($full, $updated, [Text.UTF8Encoding]::new($false))
        Write-Output "Updated stock pedestal height: $relative"
    }
}
