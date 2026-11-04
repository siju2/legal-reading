$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    # Resolve the project's JDBC and BCrypt dependencies using Maven.
    & mvn '-q' 'compile' 'dependency:build-classpath' '-Dmdep.outputFile=target/demo-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Maven dependency resolution failed.' }
    $classpath = (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath 'target/demo-classpath.txt' -Raw).Trim()
    & java '-Dfile.encoding=UTF-8' '-cp' $classpath 'scripts/SeedProjectDemo.java' $projectRoot 'data/demo-login.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Base demo seeding failed.' }
    & java '-Dfile.encoding=UTF-8' '-cp' $classpath 'scripts/ExpandProjectDemo.java' $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Expanded demo seeding failed.' }
    Write-Host 'Demo data ready. New account credentials: data/demo-login.txt.enc (excluded from Git).'
} finally {
    Pop-Location
}
