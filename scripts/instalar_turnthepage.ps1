$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$pkg = 'com.avstaim.listai.tablet.armando.smart'
$apkUrl = 'https://raw.githubusercontent.com/Youteach-org/EpsonRAsimulator/9dd73f3a25ad1949a7efa9e4c1d9f8bbc5e6e4df/apk/TurnThePage_Tablet_ArmandoAnota_Smart.apk'
$expectedSha = 'e2ca4e6531b12a1587e3d5596bfd1703c6bb391e20bba048e2e027f35eccf643'

$cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
$adb = if ($cmd) { $cmd.Source } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $adb)) { throw 'ADB no esta instalado en esta PC.' }

function ADB([string]$argsText) {
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo.FileName = $adb
    $p.StartInfo.Arguments = $argsText
    $p.StartInfo.UseShellExecute = $false
    $p.StartInfo.RedirectStandardOutput = $true
    $p.StartInfo.RedirectStandardError = $true
    [void]$p.Start()
    $out = $p.StandardOutput.ReadToEnd()
    $err = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    return [pscustomobject]@{ Code = $p.ExitCode; Out = $out; Err = $err }
}

function StreamData([string]$argsText, [string]$file, [bool]$restore) {
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo.FileName = $adb
    $p.StartInfo.Arguments = $argsText
    $p.StartInfo.UseShellExecute = $false
    $p.StartInfo.RedirectStandardInput = $restore
    $p.StartInfo.RedirectStandardOutput = $true
    $p.StartInfo.RedirectStandardError = $true
    [void]$p.Start()
    if ($restore) {
        $inputFile = [System.IO.File]::OpenRead($file)
        try {
            $inputFile.CopyTo($p.StandardInput.BaseStream)
            $p.StandardInput.Close()
        } finally {
            $inputFile.Dispose()
        }
        $p.StandardOutput.ReadToEnd() | Out-Null
    } else {
        $outputFile = [System.IO.File]::Create($file)
        try {
            $p.StandardOutput.BaseStream.CopyTo($outputFile)
        } finally {
            $outputFile.Dispose()
        }
    }
    $err = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "Error de transferencia ($($p.ExitCode)): $err" }
}

# Confirm only one authorized USB-connected Android device.
$device = ADB '-d get-state'
if ($device.Code -ne 0 -or $device.Out.Trim() -ne 'device') {
    throw 'Conecta la tablet por USB y autoriza la depuracion USB.'
}

$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$folder = Join-Path $env:USERPROFILE "TurnThePage_Respaldos\Instalacion_$timestamp"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$newApk = Join-Path $folder 'TurnThePage_by_Armando_Anota.apk'
Write-Host 'Preparando la actualizacion del lector...' -ForegroundColor Cyan
Invoke-WebRequest -Uri $apkUrl -OutFile $newApk -UseBasicParsing
$actualSha = (Get-FileHash $newApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSha -ne $expectedSha) { throw 'El archivo descargado no coincide con la compilacion verificada.' }

$installed = ADB "-d shell pm path $pkg"
$hasOldApp = $installed.Code -eq 0 -and $installed.Out -match '^package:|\npackage:'
$oldApks = @()
$backupTar = Join-Path $folder 'Biblioteca.tar'

if ($hasOldApp) {
    # Pull every original APK (including split APKs) before touching the installation.
    $parts = @($installed.Out -split "`r?`n" | Where-Object { $_ -match '^package:' } |
        ForEach-Object { $_.Substring(8).Trim() })
    $counter = 0
    foreach ($part in $parts) {
        $saved = Join-Path $folder ('Original_{0:D2}.apk' -f $counter)
        $copy = ADB ('-d pull "' + $part + '" "' + $saved + '"')
        if ($copy.Code -ne 0 -or -not (Test-Path $saved) -or (Get-Item $saved).Length -lt 1024) {
            throw 'No se pudo guardar la APK anterior. No se ha desinstalado nada.'
        }
        $oldApks += $saved
        $counter++
    }

    # Stop SQLite writes; include WAL and preferences in an app-owned TAR archive.
    $access = ADB "-d shell run-as $pkg pwd"
    if ($access.Code -ne 0) { throw 'No se puede respaldar la biblioteca actual. No se desinstalo nada.' }
    ADB "-d shell am force-stop $pkg" | Out-Null
    $directories = @()
    foreach ($name in @('files','databases','shared_prefs','no_backup')) {
        if ((ADB "-d shell run-as $pkg test -d $name").Code -eq 0) { $directories += $name }
    }
    if ('files' -notin $directories -or 'databases' -notin $directories) {
        throw 'No se encontraron las carpetas de libros y base de datos. No se desinstalo nada.'
    }
    Write-Host 'Respaldando libros, progreso, marcadores y preferencias...' -ForegroundColor Cyan
    StreamData "-d exec-out run-as $pkg tar -cf - $($directories -join ' ')" $backupTar $false
    $entries = @( & tar.exe -tf $backupTar )
    if ($LASTEXITCODE -ne 0 -or -not ($entries | Where-Object { $_ -match '(^|/)databases/library\.db$' }) -or
        -not ($entries | Where-Object { $_ -match '(^|/)files/' })) {
        throw 'La copia de seguridad no pudo verificarse. No se desinstalo nada.'
    }
    Write-Host "Respaldo verificado en $folder" -ForegroundColor Green
}

# The in-place path works as soon as future builds share a persistent signer.
$updated = ADB ('-d install -r "' + $newApk + '"')
if ($updated.Code -eq 0 -and $updated.Out -match 'Success') {
    Write-Host 'Actualizacion completada sin eliminar datos.' -ForegroundColor Green
    ADB "-d shell monkey -p $pkg 1" | Out-Null
    return
}
if (-not $hasOldApp) {
    throw "Android rechazo la instalacion: $($updated.Out) $($updated.Err)"
}
if (($updated.Out + $updated.Err) -notmatch 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
    throw "Android rechazo la actualizacion: $($updated.Out) $($updated.Err). No se desinstalo nada."
}

# Existing debug APK used another signature; only continue after verified backup.
$replaced = $false
try {
    $remove = ADB "-d uninstall $pkg"
    if ($remove.Code -ne 0 -or $remove.Out -notmatch 'Success') { throw 'No se pudo retirar la instalacion anterior.' }
    $replaced = $true

    $install = ADB ('-d install "' + $newApk + '"')
    if ($install.Code -ne 0 -or $install.Out -notmatch 'Success') {
        throw "La nueva APK no pudo instalarse: $($install.Out) $($install.Err)"
    }
    if ((ADB "-d shell run-as $pkg pwd").Code -ne 0) {
        throw 'Android no permite restaurar los datos en la nueva APK.'
    }
    Write-Host 'Restaurando la biblioteca guardada...' -ForegroundColor Cyan
    StreamData "-d exec-in run-as $pkg tar -xf -" $backupTar $true
    $dbList = ADB "-d shell run-as $pkg ls -l databases"
    $fileList = ADB "-d shell run-as $pkg ls files"
    if ($dbList.Code -ne 0 -or $dbList.Out -notmatch 'library\.db' -or $fileList.Code -ne 0) {
        throw 'La verificacion de la base de datos restaurada no paso.'
    }
    $replaced = $false
    Write-Host 'INSTALACION Y RESTAURACION COMPLETADAS' -ForegroundColor Green
    Write-Host 'Turn the Page by Armando Anota' -ForegroundColor Green
    ADB "-d shell monkey -p $pkg 1" | Out-Null
} catch {
    $cause = $_.Exception.Message
    if ($replaced -and $oldApks.Count -gt 0) {
        Write-Host 'Restaurando la aplicacion anterior...' -ForegroundColor Yellow
        ADB "-d uninstall $pkg" | Out-Null
        $command = if ($oldApks.Count -eq 1) {
            '-d install "' + $oldApks[0] + '"'
        } else {
            '-d install-multiple ' + (($oldApks | ForEach-Object { '"' + $_ + '"' }) -join ' ')
        }
        $recovered = ADB $command
        if ($recovered.Code -eq 0 -and $recovered.Out -match 'Success') {
            try {
                StreamData "-d exec-in run-as $pkg tar -xf -" $backupTar $true
                Write-Host 'Se restauro la version anterior con sus datos.' -ForegroundColor Green
            } catch {
                Write-Warning "La recuperacion de datos requiere revision. El respaldo sigue en $folder"
            }
        } else {
            Write-Warning "Conserva las APK y el respaldo: $folder"
        }
    }
    throw "La instalacion no termino: $cause. Respaldo: $folder"
}