# BeamDrop for Windows — PowerShell Launcher
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "              ⚡ BeamDrop for Windows ⚡" -ForegroundColor Cyan
Write-Host "      Universal Native Peer-to-Peer File Sharing" -ForegroundColor DarkGray
Write-Host "========================================================" -ForegroundColor Cyan

if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "[✓] Launching BeamDrop..." -ForegroundColor Green
    python $PSScriptRoot\BeamDrop.py
} elseif (Get-Command py -ErrorAction SilentlyContinue) {
    Write-Host "[✓] Launching BeamDrop via py launcher..." -ForegroundColor Green
    py $PSScriptRoot\BeamDrop.py
} else {
    Write-Host "[!] Python 3 was not detected." -ForegroundColor Red
    Write-Host "Please install Python 3 from https://www.python.org/downloads/ (check 'Add python.exe to PATH')." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
}
