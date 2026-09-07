[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string]$LumiHome,
    [string]$PythonPath,
    [string]$Version = 'latest',
    [string]$ArchivePath,
    [string]$ChecksumPath
)
$ErrorActionPreference='Stop'
$repository='kss418/lumi-codex-bridge'
$assetName='lumi-codex-windows-x64.zip'
$headers=@{'User-Agent'='lumi-codex-installer'}
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12

function Find-Lumi {
    if($LumiHome){return (Resolve-Path -LiteralPath $LumiHome).Path}
    $steam=(Get-ItemProperty 'HKCU:\Software\Valve\Steam' -ErrorAction SilentlyContinue).SteamPath
    $roots=@($steam,'C:\Program Files (x86)\Steam') | Where-Object {$_} | Select-Object -Unique
    $libraries=@($roots)
    foreach($root in $roots){
        $vdf=Join-Path $root 'steamapps\libraryfolders.vdf'
        if(Test-Path -LiteralPath $vdf){
            foreach($match in [regex]::Matches([IO.File]::ReadAllText($vdf),'"path"\s+"([^"]+)"')){
                $libraries+=$match.Groups[1].Value.Replace('\\','\')
            }
        }
    }
    foreach($library in $libraries){
        $candidate=Join-Path $library 'steamapps\common\Little LUMI'
        if(Test-Path -LiteralPath (Join-Path $candidate 'app\Shimeji-ee.jar')){return (Resolve-Path -LiteralPath $candidate).Path}
    }
    throw '꼬미 설치 경로를 찾지 못했습니다. -LumiHome "설치 경로"를 지정하세요.'
}
function Find-Python {
    $candidates=@()
    if($PythonPath){$candidates+=@{path=$PythonPath;args=@()}}
    else {
        foreach($name in @('py.exe','python.exe','python3.exe')){
            $command=Get-Command $name -ErrorAction SilentlyContinue
            if($command){$candidates+=@{path=$command.Source;args=$(if($name -eq 'py.exe'){@('-3')}else{@()})}}
        }
    }
    foreach($candidate in $candidates){
        try {
            $launcher=$candidate.path
            $arguments=@($candidate.args)+@('-c','import sys; assert sys.version_info >= (3,12); print(sys.executable)')
            $output=@(& $launcher @arguments 2>$null)
            if($LASTEXITCODE -eq 0 -and $output.Count -gt 0 -and (Test-Path -LiteralPath $output[-1] -PathType Leaf)){return $output[-1]}
        } catch { }
    }
    throw 'Python 3.12 이상이 필요합니다. 설치 후 다시 실행하거나 -PythonPath "python.exe 경로"를 지정하세요.'
}
function Assert-NotRedirected([string]$path){
    $current=$path
    while($current){
        if((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "연결된 경로에는 설치하지 않습니다: $current"}
        $parent=Split-Path $current -Parent
        if($parent -eq $current){break};$current=$parent
    }
}
$lumiRoot=Find-Lumi
if(-not(Test-Path -LiteralPath (Join-Path $lumiRoot 'app\Shimeji-ee.jar'))){throw '유효한 꼬미 설치 폴더가 아닙니다.'}
$destination=Join-Path $lumiRoot 'mods\lumi-codex'
Assert-NotRedirected $destination
if(-not $PSCmdlet.ShouldProcess($destination,'GitHub 배포본 다운로드 및 설치')){return}
$python=Find-Python
foreach($process in Get-Process){
    $exe=$null;try{$exe=$process.Path}catch{}
    if($exe -and $exe.StartsWith($lumiRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw '꼬미를 완전히 종료한 뒤 설치하세요.'}
}
$work=Join-Path ([IO.Path]::GetTempPath()) ('lumi-install-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work -Force | Out-Null
if($ArchivePath){
    if(-not $ChecksumPath){throw '-ArchivePath 사용 시 -ChecksumPath도 지정하세요.'}
    $archive=(Resolve-Path -LiteralPath $ArchivePath).Path
    $checksums=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $ChecksumPath).Path)
} else {
    $endpoint=if($Version -eq 'latest'){'latest'}else{'tags/'+[Uri]::EscapeDataString($Version)}
    try {$release=Invoke-RestMethod "https://api.github.com/repos/$repository/releases/$endpoint" -Headers $headers}
    catch {throw 'GitHub 릴리스를 찾지 못했습니다. 게시된 배포 버전과 네트워크 연결을 확인하세요.'}
    $zipAsset=@($release.assets | Where-Object name -eq $assetName)
    $hashAsset=@($release.assets | Where-Object name -eq 'SHA256SUMS.txt')
    if($zipAsset.Count -ne 1 -or $hashAsset.Count -ne 1){throw '릴리스에 배포 ZIP 또는 해시 파일이 없습니다.'}
    Write-Output ('다운로드 중: '+$release.tag_name)
    $archive=Join-Path $work $assetName
    Invoke-WebRequest $zipAsset[0].browser_download_url -Headers $headers -OutFile $archive -UseBasicParsing
    $hashFile=Join-Path $work 'SHA256SUMS.txt'
    Invoke-WebRequest $hashAsset[0].browser_download_url -Headers $headers -OutFile $hashFile -UseBasicParsing
    $checksums=[IO.File]::ReadAllText($hashFile)
}
$pattern='(?m)^([a-fA-F0-9]{64})\s+\*?'+[regex]::Escape($assetName)+'\s*$'
$hashMatch=[regex]::Match($checksums,$pattern)
if(-not $hashMatch.Success -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $hashMatch.Groups[1].Value){throw '배포 ZIP의 SHA-256 검증에 실패했습니다.'}
$files=@('plugins/lumi-codex.jar','tools/main.py','tools/codex_client.py','tools/stdio_bridge.py','tools/local_tts.py')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try {
    foreach($entry in $zip.Entries){
        if($entry.FullName.EndsWith('/')){continue}
        if($entry.FullName -notin @($files | ForEach-Object {'lumi-codex/'+$_})){throw "예상하지 못한 배포 파일: $($entry.FullName)"}
    }
    foreach($relative in $files){
        $entries=@($zip.Entries | Where-Object FullName -eq ('lumi-codex/'+$relative))
        if($entries.Count -ne 1 -or $entries[0].Length -gt 50MB){throw "누락되거나 잘못된 파일: $relative"}
        $target=Join-Path $work $relative
        New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0],$target,$false)
    }
} finally {$zip.Dispose()}
$runtime=@{python=$python}|ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $work 'tools/runtime.json'),$runtime,[Text.UTF8Encoding]::new($false))
$files+='tools/runtime.json'
foreach($relative in $files){Assert-NotRedirected (Join-Path $destination $relative)}
$backup=Join-Path $env:LOCALAPPDATA ('LumiCodex\backups\'+[guid]::NewGuid().ToString('N'))
$changed=@()
try {
    foreach($relative in $files){
        $target=Join-Path $destination $relative;$source=Join-Path $work $relative;$old=Join-Path $backup $relative
        $existed=Test-Path -LiteralPath $target
        if($existed){New-Item -ItemType Directory -Path (Split-Path $old -Parent) -Force | Out-Null;Copy-Item -LiteralPath $target -Destination $old}
        New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
        $changed+=@{target=$target;backup=$old;existed=$existed}
        Copy-Item -LiteralPath $source -Destination $target -Force
        if((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $target).Hash){throw "복사 검증 실패: $relative"}
    }
} catch {
    for($i=$changed.Count-1;$i -ge 0;$i--){
        $item=$changed[$i]
        if($item.existed){Copy-Item -LiteralPath $item.backup -Destination $item.target -Force}
        elseif(Test-Path -LiteralPath $item.target){Remove-Item -LiteralPath $item.target -Force}
    }
    throw
}
Write-Output '설치 완료. 꼬미를 실행하고 설정 → 모드에서 Lumi Codex를 켜세요.'
Write-Output '대화하려면 ChatGPT/Codex 앱 또는 Codex CLI에 로그인되어 있어야 합니다.'
Write-Output 'TTS 의존성은 이 설치에 포함되지 않으며, TTS 설정에서 원할 때 설치할 수 있습니다.'