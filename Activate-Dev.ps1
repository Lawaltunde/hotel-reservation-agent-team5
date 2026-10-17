# Dot-source this file: . .\Activate-Dev.ps1
$projectRoot = $PSScriptRoot
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $projectRoot '.tools\playwright'
$localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) {
    $env:JAVA_HOME = $localJdk.FullName
    $env:PATH = "$($localJdk.FullName)\bin;$env:PATH"
}
$localNode = Join-Path $projectRoot 'frontend\node'
if (Test-Path -LiteralPath (Join-Path $localNode 'node.exe')) {
    $env:PATH = "$localNode;$env:PATH"
    $env:PATH = "$(Join-Path $projectRoot 'dev-tools');$env:PATH"
}
