param([string]$LumiHome = 'C:\Program Files (x86)\Steam\steamapps\common\Little LUMI')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$javac = Join-Path $projectRoot '.tools\jdk-25\bin\javac.exe'
$jar = Join-Path $projectRoot '.tools\jdk-25\bin\jar.exe'
$sourceRoot = Join-Path $projectRoot 'plugin\src\main\java'
$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw 'Add Java plugin sources under plugin/src/main/java first.' }
$classes = Join-Path $projectRoot 'build\java'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$classPath = (Join-Path $LumiHome 'app\Shimeji-ee.jar') + ';' + (Join-Path $LumiHome 'app\lib\*')
& $javac --release 25 -encoding UTF-8 -cp $classPath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$resources = Join-Path $projectRoot 'plugin\src\main\resources'
$dist = Join-Path $projectRoot 'dist\lumi-codex\plugins'
New-Item -ItemType Directory -Path $dist -Force | Out-Null
& $jar --create --file (Join-Path $dist 'lumi-codex.jar') -C $classes . -C $resources .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
