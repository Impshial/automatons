# Retained entry point: import current supplied artwork instead of repainting it.
$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'import_shard_textures.py')
if ($LASTEXITCODE -ne 0) { throw 'Shard import failed' }
