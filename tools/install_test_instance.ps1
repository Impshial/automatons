param(
    [string]$ReleaseDirectory = '',
    [string]$HandbookDirectory = ''
)
$ErrorActionPreference = 'Stop'
$instanceRoot = [System.IO.Path]::GetFullPath('C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester')
$running = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -and ($_.CommandLine.Replace('/','\') -match [regex]::Escape($instanceRoot)) }
if ($running) { throw 'Save and close Rackworks Tester before installing. No game process or files were changed.' }
$releaseVersion = '0.10.0'
if (-not $ReleaseDirectory) {
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "automatons-$releaseVersion.jar")) { $ReleaseDirectory = $PSScriptRoot }
    else { $ReleaseDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'build\libs' }
}
if (-not $HandbookDirectory) {
    $releasedPack = Join-Path $ReleaseDirectory "$releaseVersion\resourcepacks\Automatons-Guide"
    if (Test-Path -LiteralPath $releasedPack) { $HandbookDirectory = $releasedPack }
    else { $HandbookDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'resourcepacks\Automatons-Guide' }
}
$sourceJar = Join-Path $ReleaseDirectory "automatons-$releaseVersion.jar"
$expected = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
$modsRoot = (Resolve-Path -LiteralPath (Join-Path $instanceRoot 'mods')).Path
$guideJar = Join-Path $modsRoot 'guideme-20.1.15.jar'
if (-not (Test-Path -LiteralPath $guideJar)) { throw 'Install required GuideME 20.1.15 before this release. No mod files were changed.' }
$packInstaller = Join-Path $PSScriptRoot 'install_handbook_pack.py'
$releasedInstaller = Join-Path $PSScriptRoot "install_handbook_pack-$releaseVersion.py"
if (Test-Path -LiteralPath $releasedInstaller) { $packInstaller = $releasedInstaller }
if (-not (Test-Path -LiteralPath (Join-Path $HandbookDirectory 'pack.mcmeta'))) { throw 'Missing external handbook resource pack. No mod files were changed.' }
if (-not (Test-Path -LiteralPath $packInstaller)) { throw 'Missing handbook installer. No mod files were changed.' }
Get-Command python -ErrorAction Stop | Out-Null
$targetJar = Join-Path $modsRoot "automatons-$releaseVersion.jar"
$stagedJar = Join-Path $modsRoot "automatons-$releaseVersion.jar.pending"
$backupRoot = [System.IO.Path]::GetFullPath((Join-Path $instanceRoot ('.automatons-release-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
if (-not $backupRoot.StartsWith($instanceRoot + '\')) { throw 'Backup path outside the designated instance' }
$oldJars = @(Get-ChildItem -LiteralPath $modsRoot -Filter 'automatons-*.jar' -File)
foreach ($oldJar in $oldJars) { if ($oldJar.DirectoryName -ne $modsRoot) { throw 'Unexpected mod path' } }
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
foreach ($oldJar in $oldJars) { Copy-Item -LiteralPath $oldJar.FullName -Destination (Join-Path $backupRoot $oldJar.Name) }
Copy-Item -LiteralPath $sourceJar -Destination $stagedJar
if ((Get-FileHash -LiteralPath $stagedJar -Algorithm SHA256).Hash -ne $expected) { throw 'Staged JAR hash mismatch; old mods remain installed' }
# Install the guide before switching to a JAR that no longer contains its pages.
# The pack installer backs up options and changed defaults, retaining local edits.
python $packInstaller --source $HandbookDirectory --instance $instanceRoot --backup $backupRoot
if ($LASTEXITCODE -ne 0) { throw 'Handbook installation failed; old mods remain installed' }
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
