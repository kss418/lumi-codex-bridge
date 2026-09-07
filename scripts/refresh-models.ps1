$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$python = Join-Path $projectRoot '.venv\bin\python.exe'
$main = Join-Path $projectRoot 'bridge\main.py'
$requests = '{"id":1,"method":"model/list"}' + "`n" + '{"id":2,"method":"shutdown"}'
$output = $requests | & $python $main --stdio
if ($LASTEXITCODE -ne 0) { throw 'Codex 모델 목록 조회 실패. 로그인 상태를 확인하세요.' }
$messages = @($output | ForEach-Object { $_ | ConvertFrom-Json })
$models = ($messages | Where-Object { $_.id -eq 1 }).result.models
if (-not $models) { throw '모델 목록이 비어 있습니다.' }
$json = ConvertTo-Json -InputObject @($models) -Depth 12
$path = Join-Path $projectRoot 'plugin\src\main\resources\models.json'
[IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
Write-Output '모델 목록을 갱신했습니다. build-plugin.ps1을 실행해 JAR에 반영하세요.'
