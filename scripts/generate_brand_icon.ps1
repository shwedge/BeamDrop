# ============================================================================
#   BeamDrop - Official Brand Icon Generator
#   Matches Android Logo: Vibrant Squircle (Cyan to Purple) + 3-Node Share Glyph
# ============================================================================

Add-Type -AssemblyName System.Drawing

function Create-SquirclePath([float]$x, [float]$y, [float]$size, [float]$radius) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $r = $radius
    $d = $r * 2.0
    $right = $x + $size
    $bottom = $y + $size
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($right - $d, $y, $d, $d, 270, 90)
    $path.AddArc($right - $d, $bottom - $d, $d, $d, 0, 90)
    $path.AddArc($x, $bottom - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Generate-BrandBitmap([int]$canvasSize, [float]$paddingRatio = 0.035) {
    $bmp = New-Object System.Drawing.Bitmap($canvasSize, $canvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $margin = $canvasSize * $paddingRatio
    $sqSize = $canvasSize - ($margin * 2.0)
    $cornerR = $sqSize * 0.285
    $sqPath = Create-SquirclePath $margin $margin $sqSize $cornerR

    # Gradient: Electric Turquoise -> Azure Blue -> Periwinkle -> Deep Orchid / Purple
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF($margin, $margin)),
        (New-Object System.Drawing.PointF(($margin + $sqSize), ($margin + $sqSize))),
        [System.Drawing.Color]::FromArgb(255, 58, 228, 204),
        [System.Drawing.Color]::FromArgb(255, 172, 92, 246)
    )
    $cb = New-Object System.Drawing.Drawing2D.ColorBlend
    $cb.Colors = [System.Drawing.Color[]]@(
        [System.Drawing.Color]::FromArgb(255, 58, 228, 204),  # #3AE4CC (Cyan/Teal)
        [System.Drawing.Color]::FromArgb(255, 68, 180, 248),  # #44B4F8 (Sky Blue)
        [System.Drawing.Color]::FromArgb(255, 126, 142, 248), # #7E8EF8 (Soft Violet)
        [System.Drawing.Color]::FromArgb(255, 172, 92, 246)   # #AC5CF6 (Purple)
    )
    $cb.Positions = [float[]]@(0.0, 0.33, 0.68, 1.0)
    $brush.InterpolationColors = $cb

    $g.FillPath($brush, $sqPath)
    $brush.Dispose()
    $sqPath.Dispose()

    # 3-Node Connected Share Glyph in Obsidian Navy (#0A1118)
    $glyphBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 11, 18, 30))
    $nodeR = $sqSize * 0.096
    $nodeD = $nodeR * 2.0

    $pLeft = New-Object System.Drawing.PointF(($margin + $sqSize * 0.35), ($margin + $sqSize * 0.50))
    $pTR   = New-Object System.Drawing.PointF(($margin + $sqSize * 0.65), ($margin + $sqSize * 0.33))
    $pBR   = New-Object System.Drawing.PointF(($margin + $sqSize * 0.65), ($margin + $sqSize * 0.67))

    # Connecting strokes with round join and round caps
    $linePath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $linePath.AddLines([System.Drawing.PointF[]]@($pTR, $pLeft, $pBR))
    $pen = New-Object System.Drawing.Pen($glyphBrush, ($sqSize * 0.065))
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawPath($pen, $linePath)
    $pen.Dispose()
    $linePath.Dispose()

    # Node circles on top of strokes
    $g.FillEllipse($glyphBrush, ($pLeft.X - $nodeR), ($pLeft.Y - $nodeR), $nodeD, $nodeD)
    $g.FillEllipse($glyphBrush, ($pTR.X - $nodeR), ($pTR.Y - $nodeR), $nodeD, $nodeD)
    $g.FillEllipse($glyphBrush, ($pBR.X - $nodeR), ($pBR.Y - $nodeR), $nodeD, $nodeD)

    $glyphBrush.Dispose()
    $g.Dispose()

    return $bmp
}

function Build-WindowsIco([System.Drawing.Bitmap]$masterBmp, [string]$outIcoPath) {
    $sizes = @(256, 128, 64, 48, 32, 16)
    $pngStreams = [System.Collections.Generic.List[byte[]]]::new()

    foreach ($sz in $sizes) {
        $scaled = New-Object System.Drawing.Bitmap($sz, $sz, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($scaled)
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.DrawImage($masterBmp, 0, 0, $sz, $sz)
        $g.Dispose()

        $ms = New-Object System.IO.MemoryStream
        $scaled.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $scaled.Dispose()
        $pngStreams.Add($ms.ToArray())
        $ms.Dispose()
    }

    $outDir = [System.IO.Path]::GetDirectoryName($outIcoPath)
    if (-not [string]::IsNullOrEmpty($outDir) -and -not (Test-Path $outDir)) {
        [System.IO.Directory]::CreateDirectory($outDir) | Out-Null
    }

    $fs = [System.IO.File]::Create($outIcoPath)
    $bw = New-Object System.IO.BinaryWriter($fs)

    # ICONDIR (6 bytes)
    $bw.Write([uint16]0) # Reserved
    $bw.Write([uint16]1) # ICO type
    $bw.Write([uint16]$sizes.Count) # Count

    # Calculate starting offset for image data: 6 + (16 * count)
    [uint32]$dataOffset = 6 + ($sizes.Count * 16)

    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $sz = $sizes[$i]
        [uint32]$len = $pngStreams[$i].Length
        $entryWidth = if ($sz -ge 256) { [byte]0 } else { [byte]$sz }
        $entryHeight = if ($sz -ge 256) { [byte]0 } else { [byte]$sz }

        $bw.Write($entryWidth)
        $bw.Write($entryHeight)
        $bw.Write([byte]0)   # Color count
        $bw.Write([byte]0)   # Reserved
        $bw.Write([uint16]1) # Planes
        $bw.Write([uint16]32)# Bit count
        $bw.Write($len)
        $bw.Write($dataOffset)

        $dataOffset += $len
    }

    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $bytes = $pngStreams[$i]
        $bw.Write($bytes, 0, $bytes.Length)
    }

    $bw.Flush()
    $bw.Close()
    $fs.Dispose()

    Write-Host "Successfully generated ICO: $outIcoPath ($(Get-Item $outIcoPath).Length bytes)" -ForegroundColor Green
}

# --- Execute Generation ---
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
if ([string]::IsNullOrEmpty($scriptDir)) { $scriptDir = (Get-Location).Path }
$rootDir = (Resolve-Path (Join-Path $scriptDir "..")).Path

Write-Host "Rendering Master 512x512 Brand Logo..." -ForegroundColor Cyan
$master512 = Generate-BrandBitmap 512 0.035
$masterPngPath = Join-Path $rootDir "inspect_screens\official_android_brand_512.png"
$master512.Save($masterPngPath, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Saved Master PNG: $masterPngPath" -ForegroundColor Green

# Save to public and assets
$publicLogo = Join-Path $rootDir "public\logo.png"
$master512.Save($publicLogo, [System.Drawing.Imaging.ImageFormat]::Png)
$assetLogo = Join-Path $rootDir "app\src\main\assets\logo.png"
$master512.Save($assetLogo, [System.Drawing.Imaging.ImageFormat]::Png)

# Build Multi-Resolution ICO files
$icoTargets = @(
    (Join-Path $rootDir "windows\BeamDrop.ico"),
    (Join-Path $rootDir "public\BeamDrop.ico"),
    (Join-Path $rootDir "app\src\main\assets\BeamDrop.ico"),
    "C:\Users\Sajja\AppData\Local\Programs\Sajjad Hossain Mridul\BeamDrop\BeamDrop.ico"
)

foreach ($icoPath in $icoTargets) {
    Build-WindowsIco $master512 $icoPath
}

# Also update Android mipmap launcher icons so Android launcher is 100% synchronized!
$androidMipmaps = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($entry in $androidMipmaps.GetEnumerator()) {
    $folder = $entry.Key
    $sz = $entry.Value
    $dir = Join-Path $rootDir "app\src\main\res\$folder"
    if (Test-Path $dir) {
        $scaled = New-Object System.Drawing.Bitmap($sz, $sz, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($scaled)
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.DrawImage($master512, 0, 0, $sz, $sz)
        $g.Dispose()

        $p1 = Join-Path $dir "ic_launcher.png"
        $p2 = Join-Path $dir "ic_launcher_round.png"
        $scaled.Save($p1, [System.Drawing.Imaging.ImageFormat]::Png)
        $scaled.Save($p2, [System.Drawing.Imaging.ImageFormat]::Png)
        $scaled.Dispose()
        Write-Host "Updated Android launcher asset in $folder ($($sz)x$($sz))" -ForegroundColor Green
    }
}

$master512.Dispose()
Write-Host "Brand icon generation and multi-platform synchronization complete!" -ForegroundColor Cyan
