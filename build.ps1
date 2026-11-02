param([string]$JavaHome = $env:JAVA_HOME, [switch]$Offline, [ValidateSet('fix','trace')][string]$Component = 'fix')
$ErrorActionPreference = 'Stop'
$buildArgs = @((Join-Path $PSScriptRoot 'build.py'), '--component', $Component)
if ($JavaHome) { $buildArgs += @('--java-home', $JavaHome) }
if ($Offline) { $buildArgs += '--offline' }
python @buildArgs
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
