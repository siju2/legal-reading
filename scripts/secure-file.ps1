param(
    [Parameter(Mandatory=$true)][ValidateSet('encrypt','decrypt')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$InputFile,
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$classes=Join-Path $projectRoot 'target/crypto-tools'
New-Item -ItemType Directory -Force $classes | Out-Null
& javac '-encoding' 'UTF-8' '-d' $classes (Join-Path $projectRoot 'src/main/java/com/example/project/security/DataCipher.java') (Join-Path $PSScriptRoot 'SecureFileTool.java')
if($LASTEXITCODE -ne 0){throw 'Could not compile encryption tool.'}
& java '-cp' $classes 'SecureFileTool' $Mode $InputFile $OutputFile
if($LASTEXITCODE -ne 0){throw 'Encryption/decryption failed. Verify the key and input file; existing output files are never overwritten.'}
