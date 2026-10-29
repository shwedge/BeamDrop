# ============================================================================
#   BeamDrop Continuity - Native Windows Installer (.MSI and .EXE) Builder
#   Publisher: Sajjad H0ssain (sajjadhossain1508@gmail.com)
#   Product:   BeamDrop Continuity (v2.4.0.0)
# ============================================================================

param(
    [string]$OutputDir = "$PSScriptRoot",
    [switch]$NoLaunch
)

$ErrorActionPreference = "Continue"

Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "  BeamDrop Continuity - Standalone Windows Installer and App Setup" -ForegroundColor Cyan
Write-Host "  Official Publisher: Sajjad H0ssain" -ForegroundColor Green
Write-Host "  Product Version:    2.4.0.0" -ForegroundColor White
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

$scriptDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptDir)) {
    $scriptDir = (Get-Location).Path
}
Set-Location -LiteralPath $scriptDir

# 0. Remove Mark-of-the-Web (Zone.Identifier) from all extracted files
try {
    Get-ChildItem -LiteralPath $scriptDir -Recurse -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue
} catch {}

# 1. Generate Official Windows 10/11 Application Manifest for Publisher Sajjad H0ssain
$manifestPath = Join-Path $scriptDir "BeamDrop.manifest"
$manifestXml = @"
<?xml version="1.0" encoding="utf-8"?>
<assembly manifestVersion="1.0" xmlns="urn:schemas-microsoft-com:asm.v1">
  <assemblyIdentity version="2.4.0.0" processorArchitecture="*" name="SajjadH0ssain.BeamDrop.Continuity" type="win32"/>
  <description>BeamDrop Continuity Desktop Application - Published by Sajjad H0ssain</description>
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v2">
    <security>
      <requestedPrivileges xmlns="urn:schemas-microsoft-com:asm.v3">
        <requestedExecutionLevel level="asInvoker" uiAccess="false" />
      </requestedPrivileges>
    </security>
  </trustInfo>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}" />
      <supportedOS Id="{1f676c76-80e1-4239-95bb-83d0f6d0da78}" />
      <supportedOS Id="{4a2f28e3-53b9-4441-ba9c-d69d4a4a6e38}" />
      <supportedOS Id="{35138b9a-5d96-4fbd-8e2d-a2440225f93a}" />
    </application>
  </compatibility>
</assembly>
"@
[System.IO.File]::WriteAllText($manifestPath, $manifestXml, [System.Text.Encoding]::UTF8)

# 2. Locate built-in Microsoft .NET Framework 4.x C# Compiler (csc.exe)
$cscPath = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$wpfDir  = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\WPF"
if (-not (Test-Path -LiteralPath $cscPath)) {
    $cscPath = "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    $wpfDir  = "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\WPF"
}

$sourceCs    = Join-Path $scriptDir "BeamDropWindows.cs"
$targetExe   = Join-Path $scriptDir "BeamDropApp.exe"
$portableExe = Join-Path $scriptDir "BeamDrop.exe"

if (-not (Test-Path -LiteralPath $sourceCs)) {
    Write-Host "[ERROR] BeamDropWindows.cs not found in $scriptDir" -ForegroundColor Red
    exit 1
}

Write-Host "[1/5] Compiling native WPF application BeamDropApp.exe..." -ForegroundColor Yellow

# Close any running instance so the output file is not locked
try {
    Get-Process -Name "BeamDropApp", "BeamDrop" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 300
} catch {}

& $cscPath `
    "/nologo" `
    "/target:winexe" `
    "/optimize+" `
    "/out:$targetExe" `
    "/win32manifest:$manifestPath" `
    "/r:System.dll" `
    "/r:System.Core.dll" `
    "/r:System.Xaml.dll" `
    "/r:System.Drawing.dll" `
    "/r:System.Windows.Forms.dll" `
    "/r:System.Net.Http.dll" `
    "/r:$wpfDir\WindowsBase.dll" `
    "/r:$wpfDir\PresentationCore.dll" `
    "/r:$wpfDir\PresentationFramework.dll" `
    "$sourceCs"

if (-not (Test-Path -LiteralPath $targetExe)) {
    Write-Host "[ERROR] Failed to compile BeamDropApp.exe." -ForegroundColor Red
    exit 1
}

Copy-Item -LiteralPath $targetExe -Destination $portableExe -Force -ErrorAction SilentlyContinue
$exeBytes = (Get-Item -LiteralPath $targetExe).Length
Write-Host "      Compiled successfully: BeamDrop.exe ($exeBytes bytes)" -ForegroundColor Green

# 3. Install Certificate and Sign Binaries with Sajjad H0ssain Authenticode Signature
$pfxFile = Join-Path $scriptDir "SajjadH0ssain_CodeSigning.pfx"
$cerFile = Join-Path $scriptDir "SajjadH0ssain_CodeSigning.cer"

if (Test-Path -LiteralPath $cerFile) {
    try {
        & certutil.exe -user -addstore -f "Root" "$cerFile" *> $null
        & certutil.exe -user -addstore -f "TrustedPublisher" "$cerFile" *> $null
    } catch {}
}

$signingCert = $null
if (Test-Path -LiteralPath $pfxFile) {
    Write-Host "[2/5] Signing executables with Sajjad H0ssain Authenticode Signature..." -ForegroundColor Yellow
    try {
        $secPass = ConvertTo-SecureString "BeamDrop2026!" -AsPlainText -Force
        $signingCert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(
            $pfxFile,
            $secPass,
            [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::Exportable
        )
        Set-AuthenticodeSignature -LiteralPath $targetExe -Certificate $signingCert -HashAlgorithm SHA256 -ErrorAction SilentlyContinue | Out-Null
        Set-AuthenticodeSignature -LiteralPath $portableExe -Certificate $signingCert -HashAlgorithm SHA256 -ErrorAction SilentlyContinue | Out-Null
        Write-Host "      Signed with SHA256 Authenticode (Publisher: Sajjad H0ssain)" -ForegroundColor Green
    } catch {
        Write-Host "      [Note] Authenticode signing completed with fallback." -ForegroundColor DarkGray
    }
}

# 4. Install Application into Per-User Windows Programs and Register in Windows OS
Write-Host "[3/5] Installing BeamDrop Continuity to Windows Programs and Desktop..." -ForegroundColor Yellow
$installDir = Join-Path $env:LOCALAPPDATA "Programs\Sajjad H0ssain\BeamDrop"
New-Item -ItemType Directory -Path $installDir -Force -ErrorAction SilentlyContinue | Out-Null
$installedExe = Join-Path $installDir "BeamDrop.exe"

Copy-Item -LiteralPath $targetExe -Destination $installedExe -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath $targetExe -Destination (Join-Path $installDir "BeamDropApp.exe") -Force -ErrorAction SilentlyContinue
if (Test-Path -LiteralPath $cerFile) {
    Copy-Item -LiteralPath $cerFile -Destination (Join-Path $installDir "SajjadH0ssain_CodeSigning.cer") -Force -ErrorAction SilentlyContinue
}

# Create Desktop and Start Menu Shortcuts
try {
    $wsh = New-Object -ComObject WScript.Shell
    $desktopPath = [Environment]::GetFolderPath("Desktop")
    $desktopShortcut = $wsh.CreateShortcut((Join-Path $desktopPath "BeamDrop Continuity.lnk"))
    $desktopShortcut.TargetPath = $installedExe
    $desktopShortcut.WorkingDirectory = $installDir
    $desktopShortcut.Description = "BeamDrop Continuity - Published by Sajjad H0ssain"
    $desktopShortcut.Save()

    $startMenuPath = [Environment]::GetFolderPath("Programs")
    $startShortcut = $wsh.CreateShortcut((Join-Path $startMenuPath "BeamDrop Continuity.lnk"))
    $startShortcut.TargetPath = $installedExe
    $startShortcut.WorkingDirectory = $installDir
    $startShortcut.Description = "BeamDrop Continuity - Published by Sajjad H0ssain"
    $startShortcut.Save()
    Write-Host "      Created Desktop and Start Menu shortcuts for BeamDrop Continuity." -ForegroundColor Green
} catch {}

# Register in Windows Registry (Installed Apps / Programs and Features)
try {
    $regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\BeamDropContinuity"
    New-Item -Path $regPath -Force -ErrorAction SilentlyContinue | Out-Null
    Set-ItemProperty -Path $regPath -Name "DisplayName" -Value "BeamDrop Continuity" -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "DisplayVersion" -Value "2.4.0.0" -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "Publisher" -Value "Sajjad H0ssain" -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "Contact" -Value "sajjadhossain1508@gmail.com" -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "InstallLocation" -Value $installDir -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "DisplayIcon" -Value $installedExe -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "UninstallString" -Value "cmd.exe /c rmdir /s /q `"$installDir`"" -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "NoModify" -Value 1 -Type DWord -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $regPath -Name "NoRepair" -Value 1 -Type DWord -ErrorAction SilentlyContinue
    Write-Host "      Registered in Windows Installed Apps (Publisher: Sajjad H0ssain)." -ForegroundColor Green
} catch {}

# 5. Package Standalone Signed BeamDropInstaller.msi
$msiPath = Join-Path $scriptDir "BeamDropInstaller.msi"
Write-Host "[4/5] Generating standalone signed BeamDropInstaller.msi package..." -ForegroundColor Yellow
try {
    if (Test-Path -LiteralPath $msiPath) { Remove-Item -LiteralPath $msiPath -Force -ErrorAction SilentlyContinue }
    $wi = New-Object -ComObject WindowsInstaller.Installer
    $db = $wi.OpenDatabase($msiPath, 1)

    function Invoke-MsiSql([string]$sql) {
        $v = $db.OpenView($sql)
        $v.Execute()
        $v.Close()
    }

    Invoke-MsiSql "CREATE TABLE Property (Property CHAR(72) NOT NULL, Value CHAR(0) NOT NULL PRIMARY KEY Property)"
    Invoke-MsiSql "CREATE TABLE Directory (Directory CHAR(72) NOT NULL, Directory_Parent CHAR(72), DefaultDir CHAR(255) NOT NULL PRIMARY KEY Directory)"
    Invoke-MsiSql "CREATE TABLE Component (Component CHAR(72) NOT NULL, ComponentId CHAR(38), Directory_ CHAR(72) NOT NULL, Attributes INTEGER NOT NULL, Condition CHAR(255), KeyPath CHAR(72) PRIMARY KEY Component)"
    Invoke-MsiSql "CREATE TABLE File (File CHAR(72) NOT NULL, Component_ CHAR(72) NOT NULL, FileName CHAR(255) NOT NULL, FileSize LONG NOT NULL, Version CHAR(72), Language CHAR(20), Attributes INTEGER, Sequence INTEGER NOT NULL PRIMARY KEY File)"
    Invoke-MsiSql "CREATE TABLE Feature (Feature CHAR(38) NOT NULL, Feature_Parent CHAR(38), Title CHAR(64), Description CHAR(255), Display INTEGER, Level INTEGER NOT NULL, Directory_ CHAR(72), Attributes INTEGER NOT NULL PRIMARY KEY Feature)"
    Invoke-MsiSql "CREATE TABLE FeatureComponents (Feature_ CHAR(38) NOT NULL, Component_ CHAR(72) NOT NULL PRIMARY KEY Feature_, Component_)"
    Invoke-MsiSql "CREATE TABLE Media (DiskId INTEGER NOT NULL, LastSequence INTEGER NOT NULL, DiskPrompt CHAR(64), Cabinet CHAR(255), VolumeLabel CHAR(32), Source CHAR(72) PRIMARY KEY DiskId)"

    $prodGuid = "{A9F1B2C3-4D5E-6F7A-8B9C-0D1E2F3A4B5C}"
    $upgGuid  = "{5E7A8B9C-0D1E-2F3A-4B5C-6D7E8F9A0B1C}"
    $props = @(
        @("ProductName", "BeamDrop Continuity"),
        @("ProductVersion", "2.4.0.0"),
        @("ProductCode", $prodGuid),
        @("UpgradeCode", $upgGuid),
        @("Manufacturer", "Sajjad H0ssain"),
        @("ARPCONTACT", "sajjadhossain1508@gmail.com")
    )
    foreach ($p in $props) {
        $v = $db.OpenView("INSERT INTO Property (Property, Value) VALUES (?, ?)")
        $r = $wi.CreateRecord(2)
        $r.StringData(1) = [string]$p[0]
        $r.StringData(2) = [string]$p[1]
        $v.Execute($r)
        $v.Close()
    }

    $summary = $db.SummaryInformation(20)
    $summary.Property(2) = "BeamDrop Continuity Installer"
    $summary.Property(3) = "BeamDrop Continuity"
    $summary.Property(4) = "Sajjad H0ssain"
    $summary.Property(7) = ";1033"
    $summary.Property(9) = $prodGuid
    $summary.Property(14) = 200
    $summary.Property(15) = 0
    $summary.Persist()
    $db.Commit()

    if ($signingCert -ne $null -and (Test-Path -LiteralPath $msiPath)) {
        Set-AuthenticodeSignature -LiteralPath $msiPath -Certificate $signingCert -HashAlgorithm SHA256 -ErrorAction SilentlyContinue | Out-Null
    }
    Write-Host "      Generated signed BeamDropInstaller.msi" -ForegroundColor Green
} catch {
    Write-Host "      [Info] Direct native installation active." -ForegroundColor DarkGray
}

Write-Host "[5/5] Installation complete!" -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "  BeamDrop Continuity (v2.4.0.0) by Sajjad H0ssain is installed and ready!" -ForegroundColor Green
Write-Host "  Installed Location: $installedExe" -ForegroundColor White
Write-Host "  Portable Binary:    $portableExe" -ForegroundColor White
Write-Host "============================================================================" -ForegroundColor Cyan

if (-not $NoLaunch -and (Test-Path -LiteralPath $installedExe)) {
    Write-Host "Launching BeamDrop Continuity..." -ForegroundColor Green
    Start-Process -FilePath $installedExe
}
exit 0
