# ============================================================================
#   BeamDrop - Refresh Shortcuts & Flush Windows Icon Cache
# ============================================================================

$installDir = "C:\Users\Sajja\AppData\Local\Programs\Sajjad Hossain Mridul\BeamDrop"
$installedExe = Join-Path $installDir "BeamDrop.exe"
$installedIco = Join-Path $installDir "BeamDrop.ico"

# Ensure BeamDrop.ico is present in installation directory
$sourceIco = "windows\BeamDrop.ico"
if (Test-Path $sourceIco) {
    Copy-Item $sourceIco -Destination $installedIco -Force
    Write-Host "Copied latest BeamDrop.ico to: $installedIco" -ForegroundColor Green
}

$wsh = New-Object -ComObject WScript.Shell

$desktopDirs = @(
    [Environment]::GetFolderPath("Desktop"),
    "C:\Users\Sajja\OneDrive\Desktop"
) | Select-Object -Unique

foreach ($dir in $desktopDirs) {
    if (Test-Path $dir) {
        # 1. BeamDrop.lnk
        $lnk1 = Join-Path $dir "BeamDrop.lnk"
        Remove-Item $lnk1 -Force -ErrorAction SilentlyContinue
        $sc1 = $wsh.CreateShortcut($lnk1)
        $sc1.TargetPath = $installedExe
        $sc1.WorkingDirectory = $installDir
        $sc1.Description = "BeamDrop - Official Continuity & P2P File Sharing"
        $sc1.IconLocation = "$installedIco,0"
        $sc1.Save()
        Write-Host "Created Desktop Shortcut: $lnk1 (Icon: $installedIco,0)" -ForegroundColor Green

        # 2. BeamDrop Continuity.lnk
        $lnk2 = Join-Path $dir "BeamDrop Continuity.lnk"
        Remove-Item $lnk2 -Force -ErrorAction SilentlyContinue
        $sc2 = $wsh.CreateShortcut($lnk2)
        $sc2.TargetPath = $installedExe
        $sc2.WorkingDirectory = $installDir
        $sc2.Description = "BeamDrop Continuity - Official Android & Windows Ecosystem"
        $sc2.IconLocation = "$installedIco,0"
        $sc2.Save()
        Write-Host "Created Desktop Shortcut: $lnk2 (Icon: $installedIco,0)" -ForegroundColor Green
    }
}

# Start Menu Shortcut
$startMenuDir = [Environment]::GetFolderPath("Programs")
if (Test-Path $startMenuDir) {
    $startLnk = Join-Path $startMenuDir "BeamDrop Continuity.lnk"
    Remove-Item $startLnk -Force -ErrorAction SilentlyContinue
    $scStart = $wsh.CreateShortcut($startLnk)
    $scStart.TargetPath = $installedExe
    $scStart.WorkingDirectory = $installDir
    $scStart.Description = "BeamDrop Continuity - Official Android & Windows Ecosystem"
    $scStart.IconLocation = "$installedIco,0"
    $scStart.Save()
    Write-Host "Created Start Menu Shortcut: $startLnk" -ForegroundColor Green
}

# Flush Windows Shell Icon Cache via Win32 API SHChangeNotify
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public class ShellIconCache {
    [DllImport("shell32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern void SHChangeNotify(uint wEventId, uint uFlags, IntPtr dwItem1, IntPtr dwItem2);

    public const uint SHCNE_ASSOCCHANGED = 0x08000000;
    public const uint SHCNF_IDLIST = 0x0000;
    public const uint SHCNF_FLUSH = 0x1000;

    public static void Flush() {
        SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST | SHCNF_FLUSH, IntPtr.Zero, IntPtr.Zero);
    }
}
"@ -ErrorAction SilentlyContinue

try {
    [ShellIconCache]::Flush()
    Write-Host "Notified Windows Shell of icon association changes." -ForegroundColor Cyan
} catch {
    Write-Host "SHChangeNotify flush skipped: $_"
}

# Restart Windows Explorer to immediately force redraw of all desktop icons
try {
    Write-Host "Restarting Windows Explorer to reload icons..." -ForegroundColor Yellow
    Stop-Process -Name explorer -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 800
    if (-not (Get-Process -Name explorer -ErrorAction SilentlyContinue)) {
        Start-Process explorer.exe
    }
    Write-Host "Windows Explorer restarted successfully." -ForegroundColor Green
} catch {
    Write-Host "Explorer restart skipped: $_"
}
