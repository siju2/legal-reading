param([string]$RuntimePath=(Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex/local-legal-ai'))
$ErrorActionPreference='Stop'
try {$ready=(Invoke-RestMethod 'http://127.0.0.1:11435/health' -TimeoutSec 2).ready} catch {$ready=$false}
if($ready){'Local legal AI is already running.';return}
$env:HF_HUB_OFFLINE='1';$env:TRANSFORMERS_OFFLINE='1';$env:HF_HUB_DISABLE_TELEMETRY='1';$env:PYTHONUTF8='1'
$serveScript=Join-Path $PSScriptRoot 'serve.py'
$modelPath=Join-Path $RuntimePath 'candidates/Qwen3-4B-Instruct-2507'
if(!(Test-Path (Join-Path $modelPath 'config.json'))){throw "Local legal model is missing: $modelPath"}
$arguments=@('"' + $serveScript + '"','--base','"' + $modelPath + '"','--token-file','"' + (Join-Path $RuntimePath 'api-token.txt') + '"')
$process=Start-Process -FilePath "$RuntimePath/python-portable/python.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimePath/server.log" -RedirectStandardError "$RuntimePath/server.err"
"Starting local legal AI (PID $($process.Id)). Check http://127.0.0.1:11435/health when ready."
