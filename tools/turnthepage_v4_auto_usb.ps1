# Turn the Page V4 - recuperacion de ADB e instalacion automatica.
# No hace cambios ni toca libros si Windows no tiene un Android autorizado.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$candidates = @()
$command = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($null -ne $command) { $candidates += $command.Source }
$candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
$candidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\platform-tools\adb.exe')
$adb = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $adb) { throw 'No esta disponible ADB en esta computadora.' }

function Test-AdbUsb {
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo.FileName = $adb
    $process.StartInfo.Arguments = '-d get-state'
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    [void]$process.Start()
    $err = $process.StandardError.ReadToEndAsync()
    $out = $process.StandardOutput.ReadToEnd()
    $msg = $err.GetAwaiter().GetResult()
    $process.WaitForExit()
    return (($process.ExitCode -eq 0) -and ($out.Trim() -eq 'device'))
}
function Detect-AdbState {
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo.FileName = $adb
    $process.StartInfo.Arguments = 'devices -l'
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    [void]$process.Start()
    $err = $process.StandardError.ReadToEndAsync()
    $out = $process.StandardOutput.ReadToEnd()
    [void]$err.GetAwaiter().GetResult()
    $process.WaitForExit()
    return $out
}
Write-Host 'TURN THE PAGE V4 - ZOOM FOCAL' -ForegroundColor Cyan
# Restart only the host ADB service; no device files, apps or permissions changed.
& $adb kill-server 2>$null | Out-Null
& $adb start-server 2>$null | Out-Null
$ready = Test-AdbUsb
if (-not $ready) {
    # A Windows Plug and Play rescan can recover an already plugged-in USB device.
    try { & pnputil.exe /scan-devices 2>$null | Out-Null } catch {}
    for ($i=0; $i -lt 12 -and -not $ready; $i++) {
        Start-Sleep -Seconds 2
        $ready = Test-AdbUsb
    }
}
if (-not $ready) {
    $devices = Detect-AdbState
    $status = if ($devices -match '(?m)^\S+\s+unauthorized\b') {
        'La tablet esta conectada pero Android no autorizo ADB.'
    } elseif ($devices -match '(?m)^\S+\s+offline\b') {
        'Android esta visible, pero su conexion ADB esta desconectada.'
    } else {
        'Windows no tiene ninguna tablet USB disponible para ADB.'
    }
    Write-Host $devices
    throw "$status No se modificaron aplicaciones ni libros."
}
Write-Host 'USB y ADB verificados. Iniciando instalacion segura.' -ForegroundColor Green
$uri = 'https://raw.githubusercontent.com/Youteach-org/EpsonRAsimulator/09eab012f26f0a8a89f029637837d0bb6351203a/tools/instalar_turnthepage_zoom_focal_v4.ps1'
$installer = (Invoke-WebRequest -Uri $uri -UseBasicParsing).Content
if (-not $installer.Contains('com.avstaim.listai.tablet.armando.v4') -or
    -not $installer.Contains('a9e43dc5e2d7b7d73cb5c21297b99690cb5b9e2a9dc9feaddebb2f73063aa186')) {
    throw 'La version publicada no corresponde con el instalador V4 comprobado.'
}
& ([scriptblock]::Create($installer))
