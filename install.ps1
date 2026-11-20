# Builds Overtime as a standalone app, installs it for the current user and
# creates desktop and Start Menu shortcuts. Run again after changes to update the install.
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
    # Its files stay locked until the process has fully exited (VLC can take a moment to shut down).
    $running | Wait-Process -Timeout 15 -ErrorAction SilentlyContinue
}

Write-Host "Installing to $installDir"
if (Test-Path $installDir) {
    # Retry briefly in case a file is still locked right after the app closed.
    for ($attempt = 1; ; $attempt++) {
        try { Remove-Item $installDir -Recurse -Force -ErrorAction Stop; break }
        catch {
            if ($attempt -ge 10) { throw }
            Start-Sleep -Milliseconds 500
        }
    }
}
New-Item -ItemType Directory -Path (Split-Path $installDir) -Force | Out-Null
Copy-Item $appImage $installDir -Recurse

# VLC's plugin cache (plugins.dat) isn't shipped, so the first start would load all ~360 plugins to make
# it, which after a reboot can take many seconds. Make it now with the VLC the bundled copy comes from.
$cacheGen = Join-Path $env:ProgramFiles 'VideoLAN\VLC\vlc-cache-gen.exe'
$plugins = Join-Path $installDir 'app\resources\vlc\plugins'
if ((Test-Path $cacheGen) -and (Test-Path $plugins)) {
    Write-Host 'Building the VLC plugin cache...'
    $gen = Start-Process -FilePath $cacheGen -ArgumentList "`"$plugins`"" -NoNewWindow -PassThru
    if (-not $gen.WaitForExit(60000)) { $gen.Kill() }
    if (-not (Test-Path (Join-Path $plugins 'plugins.dat'))) {
        Write-Host 'VLC plugin cache not made; Overtime makes it on its first start instead.'
    }
}

$exe = Join-Path $installDir 'Overtime.exe'
$shell = New-Object -ComObject WScript.Shell

function New-Shortcut([string] $path) {
    $link = $shell.CreateShortcut($path)
    $link.TargetPath = $exe
    $link.WorkingDirectory = $installDir
    $link.IconLocation = "$exe,0"
    $link.Description = 'Overtime IPTV player'
    $link.Save()
}

# Desktop, plus the per-user Start Menu so Overtime shows up in Windows search and can be pinned to Start.
$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Overtime.lnk'
$startMenuShortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'Overtime.lnk'
New-Shortcut $desktopShortcut
New-Shortcut $startMenuShortcut

# Make Explorer pick up a changed app icon instead of showing the cached one.
Start-Process -FilePath "$env:SystemRoot\System32\ie4uinit.exe" -ArgumentList '-show' -NoNewWindow -Wait -ErrorAction SilentlyContinue

Write-Host "Done. Shortcuts: $desktopShortcut"
Write-Host "                 $startMenuShortcut"
