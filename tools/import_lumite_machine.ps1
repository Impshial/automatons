param([Parameter(Mandatory=$true)][string]$GeneratedImage)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$artRoot = Join-Path $projectRoot 'art/lumite-0.9.0'
New-Item -ItemType Directory -Force $artRoot | Out-Null
Copy-Item -LiteralPath $GeneratedImage -Destination (Join-Path $artRoot 'cavity-generated.png') -Force
$generated = [System.Drawing.Bitmap]::new((Join-Path $artRoot 'cavity-generated.png'))
$manifestPath = Join-Path $projectRoot 'reference/current-run/ore-emissive-pixels.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -AsHashtable
foreach ($kind in @('stone','deepslate')) {
    $name = "lumite_ore_${kind}_texture"
    $baseline = [System.Drawing.Bitmap]::new((Join-Path $projectRoot "art/lumite-0.7.2/$name.png"))
    $result = [System.Drawing.Bitmap]::new(16,16)
    $mask = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($p in $manifest[$name]['cavity_pixels']) { [void]$mask.Add("$($p[0]),$($p[1])") }
    $changed = 0
    for ($y=0; $y -lt 16; $y++) { for ($x=0; $x -lt 16; $x++) {
        $original=$baseline.GetPixel($x,$y)
        $pixel=$original
        if ($mask.Contains("$x,$y") -and $original.B -lt 170 -and $original.B -gt $original.G+4) {
            $sample=$generated.GetPixel([int][Math]::Floor(($x+.5)*$generated.Width/16),[int][Math]::Floor(($y+.5)*$generated.Height/16))
            if ($sample.A -ge 128 -and $sample.B -gt $original.B) { $pixel=[System.Drawing.Color]::FromArgb(255,$sample.R,$sample.G,$sample.B); $changed++ }
        }
        $result.SetPixel($x,$y,$pixel)
    }}
    if ($changed -lt 20) { throw "Insufficient dark-purple pixels brightened: $changed" }
    $out=Join-Path $artRoot "$name.png"
    $result.Save($out,[System.Drawing.Imaging.ImageFormat]::Png)
    Copy-Item -LiteralPath $out -Destination (Join-Path $projectRoot "src/main/resources/assets/automatons/textures/block/$name.png") -Force
    $manifest[$name]['source']="art/lumite-0.9.0/$name.png"
    $manifest[$name]['sha256']=(Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash.ToLowerInvariant()
    $preview=[System.Drawing.Bitmap]::new(512,512)
    for($py=0;$py -lt 512;$py++){for($px=0;$px -lt 512;$px++){$preview.SetPixel($px,$py,$result.GetPixel([int][Math]::Floor($px/32),[int][Math]::Floor($py/32)))}}
    $preview.Save((Join-Path $artRoot "$kind-preview.png"),[System.Drawing.Imaging.ImageFormat]::Png)
    $preview.Dispose();$result.Dispose();$baseline.Dispose()
    Write-Output "${name}: $changed dark-purple pixels brightened; original background, chipped rim and highlights preserved."
}
[System.IO.File]::WriteAllText($manifestPath,($manifest | ConvertTo-Json -Depth 10)+[Environment]::NewLine,[System.Text.UTF8Encoding]::new($false))
$generated.Dispose()
