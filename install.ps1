# Builds Overtime as a standalone app, installs it for the current user and
# creates a desktop shortcut. Run again after changes to update the install.
#
#   powershell -ExecutionPolicy Bypass -File .\install.ps1

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$installDir = Join-Path $env:LOCALAPPDATA 'Programs\Overtime'
$appImage = Join-Path $PSScriptRoot 'composeApp\build\compose\binaries\main\app\Overtime'

Write-Host 'Building Overtime...'
& .\gradlew.bat :composeApp:createDistributable --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }

$running = Get-Process -Name 'Overtime' -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -like "$installDir*" }
if ($running) {
    Write-Host 'Closing the running copy of Overtime...'
    $running | Stop-Process -Force
    Start-Sleep -Milliseconds 500
}

Write-Host "Installing to $installDir"
if (Test-Path $installDir) { Remove-Item $installDir -Recurse -Force }
New-Item -ItemType Directory -Path (Split-Path $installDir) -Force | Out-Null
Copy-Item $appImage $installDir -Recurse

$exe = Join-Path $installDir 'Overtime.exe'
$shortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Overtime.lnk'
$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = $exe
$link.WorkingDirectory = $installDir
$link.IconLocation = "$exe,0"
$link.Description = 'Overtime IPTV player'
$link.Save()

# Make Explorer pick up a changed app icon instead of showing the cached one.
Start-Process -FilePath "$env:SystemRoot\System32\ie4uinit.exe" -ArgumentList '-show' -NoNewWindow -Wait -ErrorAction SilentlyContinue

Write-Host "Done. Shortcut: $shortcut"
