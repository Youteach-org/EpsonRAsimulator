$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Nunca desinstala la aplicacion actual. Instala y verifica la V3 por separado.
$oldPackage = 'com.avstaim.listai.tablet.armando.smart'
$newPackage = 'com.avstaim.listai.tablet.armando.v3'
$apkUrl = 'https://raw.githubusercontent.com/Youteach-org/EpsonRAsimulator/b20216728a1714820dd762f4bf74bb57a7fd8ce7/apk/TurnThePage_By_Armando_Anota_V3.apk'
$expectedSha256 = 'a0da60831efa324b93e10549370797774a9038360dd4272a10e8b58f654fa6ad'

$adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
$adb = if ($adbCommand) { $adbCommand.Source } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $adb)) { throw 'No se encontro ADB.' }

function Invoke-ADB([string]$arguments) {
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo.FileName = $adb
    $proc.StartInfo.Arguments = $arguments
    $proc.StartInfo.UseShellExecute = $false
    $proc.StartInfo.RedirectStandardOutput = $true
    $proc.StartInfo.RedirectStandardError = $true
    [void]$proc.Start()
    # Consumir los dos canales simultaneamente evita bloqueos en adb push.
    $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
    $stderrTask = $proc.StandardError.ReadToEndAsync()
    $proc.WaitForExit()
    return [pscustomobject]@{
        Exit = $proc.ExitCode
        Output = $stdoutTask.GetAwaiter().GetResult()
        Errors = $stderrTask.GetAwaiter().GetResult()
    }
}

function Save-PrivateTar([string]$arguments, [string]$path) {
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo.FileName = $adb
    $proc.StartInfo.Arguments = $arguments
    $proc.StartInfo.UseShellExecute = $false
    $proc.StartInfo.RedirectStandardOutput = $true
    $proc.StartInfo.RedirectStandardError = $true
    [void]$proc.Start()
    $stderrTask = $proc.StandardError.ReadToEndAsync()
    $outFile = [System.IO.File]::Create($path)
    try { $proc.StandardOutput.BaseStream.CopyTo($outFile) }
    finally { $outFile.Dispose() }
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) {
        throw ('Fallo el respaldo binario: ' + $stderrTask.GetAwaiter().GetResult())
    }
}

function Assert-ADB([string]$arguments, [string]$operation) {
    $result = Invoke-ADB $arguments
    if ($result.Exit -ne 0) {
        throw ("$operation : $($result.Output) $($result.Errors)")
    }
    return $result
}

function Device-Sha([string]$app, [string]$path) {
    $result = Invoke-ADB "-d shell run-as $app sha256sum $path"
    if ($result.Exit -ne 0) { return $null }
    $match = [regex]::Match($result.Output, '\b[a-fA-F0-9]{64}\b')
    if (-not $match.Success) { return $null }
    return $match.Value.ToLowerInvariant()
}

$device = Invoke-ADB '-d get-state'
if ($device.Exit -ne 0 -or $device.Output.Trim() -ne 'device') {
    throw 'Conecta la tablet por USB y autoriza la depuracion USB.'
}
if ((Invoke-ADB "-d shell pm path $oldPackage").Output -notmatch 'package:') {
    throw 'No se encontro la aplicacion anterior: no se ha modificado nada.'
}
if ((Invoke-ADB "-d shell run-as $oldPackage pwd").Exit -ne 0) {
    throw 'No se puede respaldar la aplicacion anterior: no se ha modificado nada.'
}
if ((Invoke-ADB "-d shell pm path $newPackage").Output -match 'package:') {
    throw 'La V3 ya existe. Para proteger sus datos, este instalador no la sustituye ni elimina.'
}

$backupRoot = Join-Path $env:USERPROFILE 'TurnThePage_Respaldos'
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$folder = Join-Path $backupRoot ('Migracion_PushSHA_' + $timestamp)
$expanded = Join-Path $folder 'VerificacionLocal'
$backup = Join-Path $folder 'Biblioteca.tar'
$apk = Join-Path $folder 'TurnThePage_By_Armando_Anota_V3.apk'
New-Item -ItemType Directory -Path $expanded -Force | Out-Null

# Reutilizar la APK verificada si ya fue descargada, sin pedir que el usuario la busque.
$foundApk = $false
$previousApks = @(Get-ChildItem $backupRoot -Recurse -File -Filter 'TurnThePage_By_Armando_Anota_V3.apk' -ErrorAction SilentlyContinue)
foreach ($candidate in $previousApks) {
    if ((Get-FileHash $candidate.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -eq $expectedSha256) {
        Copy-Item $candidate.FullName $apk
        $foundApk = $true
        break
    }
}
if (-not $foundApk) {
    Write-Host 'Descargando APK verificada...' -ForegroundColor Cyan
    Invoke-WebRequest -Uri $apkUrl -OutFile $apk -UseBasicParsing
} else {
    Write-Host 'APK verificada recuperada del respaldo anterior.' -ForegroundColor Cyan
}
if ((Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedSha256) {
    throw 'La APK no coincide con la compilacion verificada. El lector actual permanece intacto.'
}

# Detener escrituras SQLite y respaldar los archivos de la instalacion actual.
Assert-ADB "-d shell am force-stop $oldPackage" 'No se pudo detener temporalmente el lector' | Out-Null
$dirs = @()
foreach ($dir in @('files', 'databases', 'shared_prefs', 'no_backup')) {
    if ((Invoke-ADB "-d shell run-as $oldPackage test -d $dir").Exit -eq 0) { $dirs += $dir }
}
if ('files' -notin $dirs -or 'databases' -notin $dirs) {
    throw 'Faltan las carpetas privadas del lector actual. No se instalo nada.'
}
Write-Host 'Respaldando los PDF, marcadores y preferencias actuales...' -ForegroundColor Cyan
Save-PrivateTar "-d exec-out run-as $oldPackage tar -cf - $($dirs -join ' ')" $backup
$names = @(& tar.exe -tf $backup)
if ($LASTEXITCODE -ne 0) { throw 'El respaldo TAR no se pudo verificar.' }
foreach ($name in $names) {
    $normalized = $name.Replace('\', '/')
    if ($normalized.StartsWith('/') -or $normalized -match '(^|/)\.\.(/|$)' -or $normalized -match '^[A-Za-z]:') {
        throw 'El respaldo contiene una ruta insegura.'
    }
}
& tar.exe -xf $backup -C $expanded
if ($LASTEXITCODE -ne 0) { throw 'No se pudo expandir la copia local.' }
$sourceDatabase = Join-Path $expanded 'databases\library.db'
if (-not (Test-Path $sourceDatabase) -or (Get-Item $sourceDatabase).Length -lt 4096) {
    throw 'No existe library.db en el respaldo. No se instalo nada.'
}
$header = [System.IO.File]::ReadAllBytes($sourceDatabase)
if ([System.Text.Encoding]::ASCII.GetString($header, 0, 15) -ne 'SQLite format 3') {
    throw 'La cabecera de library.db no es SQLite. No se instalo nada.'
}
$bookFiles = @(Get-ChildItem (Join-Path $expanded 'files') -Recurse -File -Force -Filter '*.book' -ErrorAction SilentlyContinue)
if ($bookFiles.Count -eq 0) { throw 'No se encontraron libros importados; no se instalo nada.' }
Write-Host ("Respaldo valido. Libros: $($bookFiles.Count). Base de datos: $((Get-Item $sourceDatabase).Length) bytes.") -ForegroundColor Green

# Instalacion V3 EN PARALELO: no elimina la aplicacion actual.
$installedNew = $false
$stage = $null
try {
    Write-Host 'Instalando la version nueva sin desinstalar la anterior...' -ForegroundColor Cyan
    $installation = Invoke-ADB ('-d install "' + $apk + '"')
    if ($installation.Exit -ne 0 -or $installation.Output -notmatch 'Success') {
        throw "Android no pudo instalar V3: $($installation.Output) $($installation.Errors)"
    }
    $installedNew = $true
    Assert-ADB "-d shell am force-stop $newPackage" 'No se pudo detener V3' | Out-Null
    Assert-ADB "-d shell run-as $newPackage pwd" 'V3 no permite acceso a sus datos' | Out-Null

    # Buscar una ruta temporal que la V3 pueda leer con SU PROPIO usuario.
    # adb push es binario, pero no depende de exec-in ni de redireccionar dd.
    $probeFile = Join-Path $folder 'AccesoTemporal.txt'
    $probeValue = 'TurnThePage_' + $timestamp
    [System.IO.File]::WriteAllText($probeFile, $probeValue, [System.Text.Encoding]::ASCII)
    $candidates = @(
        "/sdcard/Android/data/$newPackage/files/.migracion_$timestamp",
        "/data/local/tmp/ttppush_$timestamp"
    )
    foreach ($candidate in $candidates) {
        $create = Invoke-ADB "-d shell mkdir -p $candidate"
        if ($create.Exit -ne 0) { continue }
        if ($candidate.StartsWith('/data/local/tmp/')) {
            Invoke-ADB "-d shell chmod 755 $candidate" | Out-Null
        }
        $testPath = "$candidate/probe.txt"
        $push = Invoke-ADB ('-d push "' + $probeFile + '" "' + $testPath + '"')
        if ($push.Exit -ne 0) { continue }
        if ($candidate.StartsWith('/data/local/tmp/')) {
            Invoke-ADB "-d shell chmod 644 $testPath" | Out-Null
        }
        $read = Invoke-ADB "-d shell run-as $newPackage cat $testPath"
        if ($read.Exit -eq 0 -and $read.Output.Trim() -eq $probeValue) {
            $stage = $candidate
            break
        }
    }
    if (-not $stage) {
        throw 'Android impide el acceso al archivo temporal desde la V3. No se copio ni borro ningun libro anterior.'
    }

    Write-Host 'Copiando cada archivo mediante ADB PUSH y comprobando su SHA-256...' -ForegroundColor Cyan
    $items = @(Get-ChildItem $expanded -Recurse -File -Force | Sort-Object FullName)
    $processed = 0
    foreach ($item in $items) {
        $relative = $item.FullName.Substring($expanded.Length + 1).Replace('\', '/')
        if ($relative -notmatch '^(files|databases|shared_prefs|no_backup)/[A-Za-z0-9_./+-]+$' -or
            $relative -match '(^|/)\.\.(/|$)') {
            throw "Nombre de archivo no compatible: $relative. La aplicacion original sigue intacta."
        }
        $hostHash = (Get-FileHash $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $stageFile = "$stage/current.bin"
        $push = Invoke-ADB ('-d push "' + $item.FullName + '" "' + $stageFile + '"')
        if ($push.Exit -ne 0) { throw "ADB PUSH fallo al copiar $relative : $($push.Errors)" }
        if ($stage.StartsWith('/data/local/tmp/')) {
            Assert-ADB "-d shell chmod 644 $stageFile" "No se pudieron abrir los permisos temporales de $relative" | Out-Null
        }
        $stagedHash = Device-Sha $newPackage $stageFile
        if ($stagedHash -ne $hostHash) {
            throw "Archivo temporal $relative no coincide: origen $hostHash, temporal $stagedHash. No se altero la app anterior."
        }
        $relativeDir = $relative.Substring(0, $relative.LastIndexOf('/'))
        Assert-ADB "-d shell run-as $newPackage mkdir -p $relativeDir" "No se pudo crear $relativeDir" | Out-Null
        Assert-ADB "-d shell run-as $newPackage cp $stageFile $relative" "No se pudo restaurar $relative" | Out-Null
        $restoredHash = Device-Sha $newPackage $relative
        if ($restoredHash -ne $hostHash) {
            throw "Archivo restaurado $relative no coincide: origen $hostHash, destino $restoredHash."
        }
        $processed++
        Write-Host ("Archivos comprobados: $processed/$($items.Count)") -ForegroundColor DarkGray
    }
    Write-Host 'PDF, base de datos y preferencias verificados por SHA-256.' -ForegroundColor Green

    $launch = Invoke-ADB "-d shell monkey -p $newPackage -c android.intent.category.LAUNCHER 1"
    if ($launch.Exit -ne 0) { throw 'La version nueva no pudo iniciar.' }
    Start-Sleep -Seconds 2
    $running = Invoke-ADB "-d shell pidof $newPackage"
    if ($running.Exit -ne 0 -or [string]::IsNullOrWhiteSpace($running.Output)) {
        throw 'La nueva aplicacion se cerro al iniciar; se conserva la anterior.'
    }
    $databaseAfterStart = Invoke-ADB "-d shell run-as $newPackage test -s databases/library.db"
    if ($databaseAfterStart.Exit -ne 0) { throw 'La nueva version no conserva su base de datos al iniciar.' }

    # No borrar nunca el lector anterior: se conserva instalado y se oculta solo al terminar.
    $hide = Invoke-ADB "-d shell pm disable-user --user 0 $oldPackage"
    if ($hide.Exit -ne 0 -or $hide.Output -notmatch 'disabled') {
        Write-Warning 'No se pudo ocultar el icono antiguo. Sus archivos siguen intactos.'
    }
    Write-Host 'TURN THE PAGE BY ARMANDO ANOTA: INSTALACION COMPLETADA' -ForegroundColor Green
    Write-Host ('Respaldo conservado: ' + $folder)
    Write-Host 'La aplicacion anterior sigue instalada con sus datos.'
}
catch {
    $message = $_.Exception.Message
    if ($installedNew) { Invoke-ADB "-d uninstall $newPackage" | Out-Null }
    Write-Host 'Se conservo intacto el lector anterior.' -ForegroundColor Yellow
    Write-Host ('Respaldo: ' + $folder)
    throw "La migracion se detuvo sin eliminar tus libros: $message"
}
finally {
    if ($stage) { Invoke-ADB "-d shell rm -rf $stage" | Out-Null }
}