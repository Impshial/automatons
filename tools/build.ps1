param([string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot', [switch]$Test)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    python tools/validate_assets.py
    if ($LASTEXITCODE -ne 0) { throw 'Asset validation failed' }
    if ($Test) { & .\gradlew.bat --console=plain runGameTestServer build } else { & .\gradlew.bat --console=plain build }
    if ($LASTEXITCODE -ne 0) { throw 'Gradle failed' }
} finally { Pop-Location }
