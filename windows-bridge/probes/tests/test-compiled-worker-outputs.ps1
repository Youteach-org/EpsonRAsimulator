$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$variants = @(
    @{ Name = "x86"; Path = Join-Path $root "windows-bridge\artifacts\compiled-worker\x86" },
    @{ Name = "x64"; Path = Join-Path $root "windows-bridge\artifacts\compiled-worker\x64" }
)

foreach ($variant in $variants) {
    if (-not (Test-Path -LiteralPath $variant.Path -PathType Container)) {
        throw "Missing compiled worker output: $($variant.Name)"
    }

    $exe = Join-Path $variant.Path "EpsonRa.Bridge.Research.Worker.exe"
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
        throw "Missing worker executable: $($variant.Name)"
    }

    $rcapi = Get-ChildItem -LiteralPath $variant.Path -Recurse -File |
        Where-Object { $_.Name -ieq "RCAPINet.dll" }
    if ($rcapi) {
        throw "RCAPINet.dll must not be present in compiled worker output."
    }

    $vendorDlls = Get-ChildItem -LiteralPath $variant.Path -Recurse -File -Filter *.dll |
        Where-Object { $_.VersionInfo.CompanyName -match "SEIKO EPSON" }
    if ($vendorDlls) {
        throw "SEIKO EPSON assemblies must not be present in compiled worker output."
    }

    $referencePackages = Get-ChildItem -LiteralPath $variant.Path -Recurse -Force |
        Where-Object { $_.Name -like "Microsoft.NETFramework.ReferenceAssemblies*" }
    if ($referencePackages) {
        throw "Reference-assembly packages must remain build-only."
    }

    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $exe).Hash.ToLowerInvariant()
    Write-Host ("WORKER_{0}_SHA256={1}" -f $variant.Name.ToUpperInvariant(), $hash)
}

$supervisor = Join-Path $root "windows-bridge\src\EpsonRa.Bridge.Research.Supervisor\bin\Release\net48\EpsonRa.Bridge.Research.Supervisor.exe"
if (-not (Test-Path -LiteralPath $supervisor -PathType Leaf)) {
    throw "Missing compiled supervisor executable."
}
$supervisorHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $supervisor).Hash.ToLowerInvariant()
Write-Host ("SUPERVISOR_SHA256={0}" -f $supervisorHash)

Write-Host "PASS compiled worker outputs are present and proprietary-free"
