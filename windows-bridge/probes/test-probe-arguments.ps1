param([string]$PowerShellPath = (Get-Process -Id $PID).Path)
$ErrorActionPreference = 'Stop'
$probe = Join-Path $PSScriptRoot 'virtual-controller-probe.ps1'
$cases = @(
    @('-Stage', 'NotAStage', '-ServerInstance', '11'),
    @('-Stage', 'Preflight', '-ServerInstance', '0'),
    @('-Stage', 'Preflight', '-ServerInstance', '11'),
    @('-Stage', 'Preflight', '-ServerInstance', 'not-an-integer'),
    @('-Stage', 'Inventory'),
    @('-Stage', 'Connect')
)
foreach ($case in $cases) {
    $output = & $PowerShellPath -NoProfile -File $probe @case
    $code = $LASTEXITCODE
    if ($code -ne 64) { throw "Expected argument exit 64, got $code." }
    $result = ($output -join "`n") | ConvertFrom-Json
    if ($result.schemaVersion -ne 1 -or $result.status -ne 'INVALID_ARGUMENTS' -or $result.success -ne $false) {
        throw 'Expected one structured argument failure.'
    }
}
Write-Output "PASS: $($cases.Count) argument regressions."
