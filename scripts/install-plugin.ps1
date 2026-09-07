[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$LumiHome = 'C:\Program Files (x86)\Steam\steamapps\common\Little LUMI',
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$lumiRoot = (Resolve-Path -LiteralPath $LumiHome).Path
if (-not (Test-Path -LiteralPath (Join-Path $lumiRoot 'app\Shimeji-ee.jar') -PathType Leaf)) {
    throw 'Little LUMI installation not found. Specify -LumiHome.'
}
$source = Join-Path $projectRoot 'dist\lumi-codex\plugins\lumi-codex.jar'
$destination = [IO.Path]::GetFullPath((Join-Path $lumiRoot 'mods\lumi-codex\plugins\lumi-codex.jar'))
if (-not $destination.StartsWith($lumiRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Destination is outside the Little LUMI installation.'
}
# Refuse redirected mod paths so an existing junction cannot change the target.
foreach ($relative in @('mods', 'mods\lumi-codex', 'mods\lumi-codex\plugins', 'mods\lumi-codex\plugins\lumi-codex.jar')) {
    $path = Join-Path $lumiRoot $relative
    if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Redirected install path is not supported: $path"
    }
}
function Assert-LumiStopped {
    foreach ($process in Get-Process) {
        $executable = $null
        try { $executable = $process.Path } catch { }
        if ($executable -and $executable.StartsWith($lumiRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Close Little LUMI before installing (PID $($process.Id))."
        }
    }
    if (Test-Path -LiteralPath $destination) {
        try {
            $handle = [IO.File]::Open($destination, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
            $handle.Dispose()
        } catch {
            throw 'The plugin JAR is locked or not writable. Close Little LUMI and check folder permissions.'
        }
    }
}

if (-not $PSCmdlet.ShouldProcess($destination, 'Install Lumi Codex plugin JAR (optionally build first)')) { return }
Assert-LumiStopped
if ($Build) {
    & (Join-Path $PSScriptRoot 'build-plugin.ps1') -LumiHome $lumiRoot
}
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
    throw 'Build output not found. Run this script with -Build first.'
}
Assert-LumiStopped
$directory = Split-Path $destination -Parent
New-Item -ItemType Directory -Path $directory -Force | Out-Null
if (Test-Path -LiteralPath $destination) {
    $backupDirectory = Join-Path $projectRoot 'build\install-backup'
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    Copy-Item -LiteralPath $destination -Destination (Join-Path $backupDirectory 'lumi-codex.jar.bak') -Force
}
Copy-Item -LiteralPath $source -Destination $destination -Force
if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash) {
    throw 'Installed JAR checksum mismatch.'
}
Write-Output "Installed: $destination"
Write-Output 'Restart Little LUMI and enable Lumi Codex in Settings > Mods.'
