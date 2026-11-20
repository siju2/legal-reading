param([string]$RuntimePath=(Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex/local-legal-ai'),[string]$Model='legal-qwen3-8b-candidate:latest')
$ErrorActionPreference='Stop'
try {$health=Invoke-RestMethod 'http://127.0.0.1:11435/health' -TimeoutSec 2} catch {$health=$null}
if($health.ready) {
    if($health.model -eq $Model){'Local legal AI is already running.';return}
    throw "Another model is already serving on port 11435: $($health.model). Stop it before switching to $Model."
}
if(Get-NetTCPConnection -State Listen -LocalPort 11435 -ErrorAction SilentlyContinue){throw 'The local AI bridge is running but its model is not ready. Check Ollama before restarting it.'}
$available=(Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 2).models.name
if($Model -notin $available){throw "Ollama model is not installed: $Model"}
$env:HF_HUB_DISABLE_TELEMETRY='1';$env:PYTHONUTF8='1'
$serveScript=Join-Path $PSScriptRoot 'serve.py'
$arguments=@('"' + $serveScript + '"','--model',$Model,'--token-file','"' + (Join-Path $RuntimePath 'api-token.txt') + '"')
$process=Start-Process -FilePath "$RuntimePath/python-portable/python.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimePath/server.log" -RedirectStandardError "$RuntimePath/server.err"
"Starting local legal AI (PID $($process.Id)). Check http://127.0.0.1:11435/health when ready."
