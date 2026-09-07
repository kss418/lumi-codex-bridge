[CmdletBinding()]
param(
    [string]$LumiHome='C:\Program Files (x86)\Steam\steamapps\common\Little LUMI',
    [switch]$SkipBuild
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $SkipBuild){& (Join-Path $PSScriptRoot 'build-plugin.ps1') -LumiHome $LumiHome}
$metadata=Get-Content -LiteralPath (Join-Path $projectRoot 'plugin\src\main\resources\plugin.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$version=[string]$metadata.version
if($version -notmatch '^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$'){throw 'plugin.json의 버전이 올바르지 않습니다.'}
$stage=Join-Path $projectRoot ('build\release-stage-'+[guid]::NewGuid().ToString('N'))
$output=Join-Path $projectRoot "dist\releases\v$version"
New-Item -ItemType Directory -Path $output -Force | Out-Null
$files=@('plugins/lumi-codex.jar','tools/main.py','tools/codex_client.py','tools/stdio_bridge.py','tools/local_tts.py')
foreach($relative in $files){
    $source=Join-Path $projectRoot ('dist\lumi-codex\'+$relative)
    if(-not(Test-Path -LiteralPath $source -PathType Leaf)){throw "빌드 파일이 없습니다: $relative"}
    $target=Join-Path $stage ('lumi-codex\'+$relative)
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}
$archive=Join-Path $output 'lumi-codex-windows-x64.zip'
if(Test-Path -LiteralPath $archive){Remove-Item -LiteralPath $archive}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::Open($archive,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach($relative in $files){
        $source=Join-Path $stage ('lumi-codex/'+$relative)
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$source,('lumi-codex/'+$relative)) | Out-Null
    }
} finally {$zip.Dispose()}
$installer=Join-Path $output 'install-lumi-codex.ps1'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'install-release.ps1') -Destination $installer -Force
$lines=@($archive,$installer) | ForEach-Object { ((Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant())+'  '+[IO.Path]::GetFileName($_) }
[IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS.txt'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "배포 파일 생성 완료: $output"
Write-Output "GitHub Releases의 v$version 릴리스에 폴더 안의 파일 3개를 업로드하세요."
Write-Output '이 스크립트는 GitHub에 자동 게시하지 않습니다.'