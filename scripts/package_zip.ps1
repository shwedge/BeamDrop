# Package windows distribution into zip
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zipPath = "public\BeamDrop-Windows-Installer.zip"
$assetZipPath = "app\src\main\assets\BeamDrop-Windows-Installer.zip"

if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

$tempDir = Join-Path $env:TEMP "BeamDropZipPackage"
if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
New-Item -ItemType Directory -Path $tempDir | Out-Null

# Copy windows files to temp dir
$windowsFiles = @(
    "BeamDrop.exe",
    "BeamDropApp.exe",
    "BeamDrop.ico",
    "BeamDrop.manifest",
    "BeamDrop.py",
    "BeamDropWindows.cs",
    "BeamDropWindows.csproj",
    "Build-BeamDropMsi.ps1",
    "Install-Certificate.bat",
    "mdns_discovery.py",
    "Package-BeamDropMsi.bat",
    "Package_BeamDropmsi.bat",
    "README.md",
    "Run-BeamDrop.bat",
    "Run-BeamDrop.ps1",
    "SajjadHossain_CodeSigning.cer",
    "SajjadHossain_CodeSigning.pfx"
)

foreach ($f in $windowsFiles) {
    $src = Join-Path "windows" $f
    if (Test-Path $src) {
        Copy-Item $src -Destination (Join-Path $tempDir $f) -Force
    }
}

[System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $zipPath)
Copy-Item $zipPath -Destination $assetZipPath -Force

Remove-Item $tempDir -Recurse -Force
Write-Host "Created $zipPath ($(Get-Item $zipPath).Length bytes) and copied to $assetZipPath" -ForegroundColor Green
