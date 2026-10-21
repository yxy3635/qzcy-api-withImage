$ErrorActionPreference = 'Stop'
$auditRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$auditBackendRoot = Join-Path $auditRepoRoot 'backend'

Push-Location $auditBackendRoot
try {
    # The old vulnerable-baseline harness is archived as AccountSecurityAudit.java.txt.
    & .\mvnw.cmd -q '-Dtest=AccountSecurityRegressionTest,AccountAttemptLimiterTest,AccountSecurityHttpTest,EmailCodeSecurityTest,RelayTokenGroupSecurityTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Account security regression tests failed' }
} finally {
    Pop-Location
}
