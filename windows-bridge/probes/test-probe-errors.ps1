$ErrorActionPreference = 'Stop'
# Load function definitions only, never the probe entrypoint/native assembly.
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PSScriptRoot 'virtual-controller-probe.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Probe parse failed.' }
foreach ($definition in $ast.EndBlock.Statements) {
    if ($definition -is [System.Management.Automation.Language.FunctionDefinitionAst]) {
        . ([scriptblock]::Create($definition.Extent.Text))
    }
}
Add-Type -TypeDefinition @'
public sealed class ProbeFixtureException : System.Exception {
    public int ErrorNumber { get { return 4321; } }
    public ProbeFixtureException() : base("private diagnostic must not escape") {}
}
public sealed class ProbeFixture {
    public void Initialize() { throw new ProbeFixtureException(); }
}
'@
try {
    Invoke-NativeMethod -Target (New-Object ProbeFixture) -StageName Inventory -MethodName Initialize
    throw 'Expected fixture to fail.'
}
catch {
    $normalized = Get-NormalizedException -Exception $_.Exception
    if ($normalized.exceptionType -ne 'ProbeFixtureException' -or $normalized.spelErrorNumber -ne 4321) {
        throw 'Native error identity/number lost through reflection wrappers.'
    }
    if (($normalized | ConvertTo-Json -Compress) -match 'private diagnostic') {
        throw 'Private exception message escaped.'
    }
}
Write-Output 'PASS: native error normalization without Epson.'
