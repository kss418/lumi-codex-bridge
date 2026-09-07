$ErrorActionPreference='Stop'
$log=Join-Path $PSScriptRoot 'update.log'
try {
    $request=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'request.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $parent=Get-Process -Id ([int]$request.parentPid) -ErrorAction SilentlyContinue
    if(-not $parent){throw '꼬미 프로세스를 확인할 수 없습니다.'}
    # Acquire the process handle before announcing readiness, avoiding PID reuse.
    $null=$parent.Handle
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'ready'),'ready')
    if(-not $parent.WaitForExit(120000)){throw '꼬미 종료를 기다리다 시간이 초과되었습니다. 설치하지 않았습니다.'}
    $installer=Join-Path $PSScriptRoot 'install-lumi-codex.ps1'
    $archive=Join-Path $PSScriptRoot 'lumi-codex-windows-x64.zip'
    $hashes=Join-Path $PSScriptRoot 'SHA256SUMS.txt'
    # Recheck the script immediately before executing it.
    $manifest=[IO.File]::ReadAllText($hashes)
    $match=[regex]::Match($manifest,'(?m)^([a-fA-F0-9]{64})[ \t]+\*?install-lumi-codex\.ps1[ \t]*\r?$')
    if(-not $match.Success -or (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash -ne $match.Groups[1].Value){throw '설치 스크립트 검증에 실패했습니다.'}
    & (Join-Path $PSHOME 'powershell.exe') -NoProfile -ExecutionPolicy Bypass -File $installer -LumiHome $request.lumiHome -ArchivePath $archive -ChecksumPath $hashes *> $log
    if($LASTEXITCODE -ne 0){throw "업데이트 설치에 실패했습니다. 로그: $log"}
    Start-Process -FilePath (Join-Path $request.lumiHome 'Little LUMI.exe') -WorkingDirectory $request.lumiHome -WindowStyle Hidden
} catch {
    $message=$_.Exception.Message
    Add-Content -LiteralPath $log -Value $message -Encoding UTF8
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($message+"`r`n로그: "+$log,'Lumi Codex 업데이트 실패') | Out-Null
    exit 1
}
