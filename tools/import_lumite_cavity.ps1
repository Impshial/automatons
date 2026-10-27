param([string]$GeneratedImage, [string]$ArtVersion = '0.7.2', [double]$LumiteBrightness = 1.12)
# Import the generated interior and composite two flat, native 16x16 ore textures.
# Use the supplied Minecraft pixels unchanged outside the painted chipped rim.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$artRoot = Join-Path $projectRoot "art/lumite-$ArtVersion"
if ($GeneratedImage) {
    Copy-Item -LiteralPath $GeneratedImage -Destination (Join-Path $artRoot 'cavity-generated.png') -Force
}
$inputArt = [System.Drawing.Bitmap]::new((Join-Path $artRoot 'cavity-generated.png'))
$nativeArt = [System.Drawing.Bitmap]::new(16,16)
$cavity = [System.Collections.Generic.List[object]]::new()
$emissive = [System.Collections.Generic.List[object]]::new()
for ($v=0; $v -lt 16; $v++) {
    for ($u=0; $u -lt 16; $u++) {
        $pixel = $inputArt.GetPixel([int][Math]::Floor(($u+0.5)*$inputArt.Width/16),[int][Math]::Floor(($v+0.5)*$inputArt.Height/16))
        # The source uses alpha 254 on solid cells and occasional alpha-1 fringe.
        # Normalize to Minecraft cutout alpha while retaining every sampled RGB.
        $pixel = if ($pixel.A -ge 128) { [System.Drawing.Color]::FromArgb(255,$pixel.R,$pixel.G,$pixel.B) } else { [System.Drawing.Color]::FromArgb(0,0,0,0) }
        $glows = $pixel.A -gt 0 -and $pixel.B -ge 110 -and $pixel.B -gt $pixel.G+18 -and $pixel.R -gt $pixel.G+8
        # Slightly brighten the visible mineral; preserve the near-black recess
        # and the original glow selection. Always sample the original source.
        if ($pixel.A -gt 0 -and $pixel.B -gt 80 -and $pixel.B -gt $pixel.G+18 -and $pixel.R -gt $pixel.G+8) {
            $pixel=[System.Drawing.Color]::FromArgb(255,[Math]::Min(255,[int][Math]::Round($pixel.R*$LumiteBrightness)),[Math]::Min(255,[int][Math]::Round($pixel.G*$LumiteBrightness)),[Math]::Min(255,[int][Math]::Round($pixel.B*$LumiteBrightness)))
        }
        $nativeArt.SetPixel($u,$v,$pixel)
        if ($pixel.A -gt 0) {
            if ($u -lt 2 -or $u -gt 13 -or $v -lt 2 -or $v -gt 13) { throw 'Cavity crosses the intact two-pixel border' }
            $cavity.Add(@($u,$v))
            if ($glows) { $emissive.Add(@($u,$v)) }
        }
    }
}
if ($cavity.Count -lt 20 -or $cavity.Count -gt 120 -or $emissive.Count -lt 2) { throw 'Unexpected cavity/glow coverage' }
$nativePath = Join-Path $artRoot 'cavity.png'
$nativeArt.Save($nativePath,[System.Drawing.Imaging.ImageFormat]::Png)
$holeSet = [System.Collections.Generic.HashSet[string]]::new()
foreach ($p in $cavity) { [void]$holeSet.Add("$($p[0]),$($p[1])") }
$rimSet = [System.Collections.Generic.HashSet[string]]::new()
foreach ($p in $cavity) {
    foreach($dx in -1,0,1) { foreach($dy in -1,0,1) {
        $u=$p[0]+$dx; $v=$p[1]+$dy
        if($u -ge 1 -and $u -le 14 -and $v -ge 1 -and $v -le 14 -and -not $holeSet.Contains("$u,$v")) { [void]$rimSet.Add("$u,$v") }
    }}
}
$manifest = [ordered]@{}
foreach ($kind in @('stone','deepslate')) {
    $name = "lumite_ore_${kind}_texture"
    $base = [System.Drawing.Bitmap]::new((Join-Path $artRoot "backgrounds/$kind.png"))
    $face = [System.Drawing.Bitmap]::new(16,16)
    for($v=0;$v -lt 16;$v++) { for($u=0;$u -lt 16;$u++) {
        $pixel=$base.GetPixel($u,$v)
        if($holeSet.Contains("$u,$v")) { $pixel=$nativeArt.GetPixel($u,$v) }
        elseif($rimSet.Contains("$u,$v")) {
            # Paint fractured facets using the matching underlying rock pixel.
            # All depth is an illusion in this texture; block geometry stays flat.
            $lit=$holeSet.Contains("$u,$($v+1)") -or $holeSet.Contains("$($u+1),$v") -or $holeSet.Contains("$($u+1),$($v+1)")
            $tones=if($lit){@(1.32,1.18,1.08)}else{@(0.51,0.61,0.69)}
            $scale=$tones[($u+2*$v)%3]
            $pixel=[System.Drawing.Color]::FromArgb(255,[Math]::Min(255,[int][Math]::Round($pixel.R*$scale)),[Math]::Min(255,[int][Math]::Round($pixel.G*$scale)),[Math]::Min(255,[int][Math]::Round($pixel.B*$scale)))
        }
        $face.SetPixel($u,$v,$pixel)
    }}
    $facePath=Join-Path $artRoot "$name.png"
    $face.Save($facePath,[System.Drawing.Imaging.ImageFormat]::Png)
    Copy-Item -LiteralPath $facePath -Destination (Join-Path $projectRoot "src/main/resources/assets/automatons/textures/block/$name.png") -Force
    $hash=(Get-FileHash -LiteralPath $facePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest[$name] = [ordered]@{source="art/lumite-$ArtVersion/$name.png";sha256=$hash;cavity_pixels=$cavity.ToArray();pixels=$emissive.ToArray()}
    $preview=[System.Drawing.Bitmap]::new(512,512)
    for($y=0;$y -lt 512;$y++){for($x=0;$x -lt 512;$x++){$preview.SetPixel($x,$y,$face.GetPixel([int][Math]::Floor($x/32),[int][Math]::Floor($y/32)))}}
    $preview.Save((Join-Path $artRoot "$kind-preview.png"),[System.Drawing.Imaging.ImageFormat]::Png)
    $preview.Dispose();$face.Dispose();$base.Dispose()
    Write-Output "$kind native 16x16 opaque texture, SHA256 $hash"
}
$json = $manifest | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'reference/current-run/ore-emissive-pixels.json'),$json+[Environment]::NewLine,[System.Text.UTF8Encoding]::new($false))
$preview = [System.Drawing.Bitmap]::new(512,512)
for($y=0;$y -lt 512;$y++){for($x=0;$x -lt 512;$x++){$preview.SetPixel($x,$y,$nativeArt.GetPixel([int][Math]::Floor($x/32),[int][Math]::Floor($y/32)))}}
$preview.Save((Join-Path $artRoot 'cavity-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose(); $nativeArt.Dispose(); $inputArt.Dispose()
Write-Output "Painted textures: 148 unchanged background pixels, $($rimSet.Count) chipped-rim pixels, $($cavity.Count) interior pixels; $($emissive.Count) glowing pixels."
