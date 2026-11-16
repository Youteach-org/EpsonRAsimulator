$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Migration: never remove the current app. Restore into a different package first.
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
    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    return [pscustomobject]@{ Exit = $proc.ExitCode; Output = $stdout; Errors = $stderr }
}

function Stream-ADB([string]$arguments, [string]$path, [bool]$upload) {
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo.FileName = $adb
    $proc.StartInfo.Arguments = $arguments
    $proc.StartInfo.UseShellExecute = $false
    $proc.StartInfo.RedirectStandardInput = $upload
    $proc.StartInfo.RedirectStandardOutput = $true
    $proc.StartInfo.RedirectStandardError = $true
    [void]$proc.Start()
    if ($upload) {
        $inputFile = [System.IO.File]::OpenRead($path)
        try {
            $inputFile.CopyTo($proc.StandardInput.BaseStream)
            $proc.StandardInput.Close()
        } finally { $inputFile.Dispose() }
        $outText = $proc.StandardOutput.ReadToEnd()
    } else {
        $outputFile = [System.IO.File]::Create($path)
        try { $proc.StandardOutput.BaseStream.CopyTo($outputFile) }
        finally { $outputFile.Dispose() }
        $outText = ''
    }
    $errText = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) { throw "Transferencia ADB fallida: $errText $outText" }
    return $outText
}

$device = Invoke-ADB '-d get-state'
if ($device.Exit -ne 0 -or $device.Output.Trim() -ne 'device') { throw 'Conecta la tablet por USB y autoriza la depuracion USB.' }
$old = Invoke-ADB "-d shell pm path $oldPackage"
if ($old.Exit -ne 0 -or $old.Output -notmatch 'package:') { throw 'No se encontro la aplicacion actual. No se ha eliminado nada.' }
$access = Invoke-ADB "-d shell run-as $oldPackage pwd"
if ($access.Exit -ne 0) { throw 'No se puede respaldar la instalacion actual. No se ha eliminado nada.' }

$folder = Join-Path $env:USERPROFILE ('TurnThePage_Respaldos\Migracion_Segura_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$backup = Join-Path $folder 'Biblioteca.tar'
$expanded = Join-Path $folder 'VerificacionLocal'
$apk = Join-Path $folder 'TurnThePage_By_Armando_Anota_V3.apk'
New-Item -ItemType Directory -Path $expanded -Force | Out-Null

Write-Host 'Descargando APK verificada...' -ForegroundColor Cyan
Invoke-WebRequest -Uri $apkUrl -OutFile $apk -UseBasicParsing
if ((Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedSha256) {
    throw 'La APK no coincide con el archivo verificado. No se ha alterado la aplicacion actual.'
}

# The old app remains installed, enabled and usable throughout backup and migration.
$stop = Invoke-ADB "-d shell am force-stop $oldPackage"
if ($stop.Exit -ne 0) { throw 'No se pudo detener el lector antes del respaldo.' }
$dirs = @()
foreach ($name in @('files','databases','shared_prefs','no_backup')) {
    if ((Invoke-ADB "-d shell run-as $oldPackage test -d $name").Exit -eq 0) { $dirs += $name }
}
if ('files' -notin $dirs -or 'databases' -notin $dirs) { throw 'No se encontraron los datos privados del lector anterior.' }
Write-Host 'Guardando biblioteca y marcadores actuales...' -ForegroundColor Cyan
Stream-ADB "-d exec-out run-as $oldPackage tar -cf - $($dirs -join ' ')" $backup $false | Out-Null
$names = @(& tar.exe -tf $backup)
if ($LASTEXITCODE -ne 0) { throw 'El respaldo no se puede leer. No se ha modificado la aplicacion.' }
# Reject entries that could escape the extraction directory.
foreach ($name in $names) {
    $normalized = $name.Replace('\','/')
    if ($normalized.StartsWith('/') -or $normalized -match '(^|/)\.\.(/|$)' -or $normalized -match '^[a-zA-Z]:') {
        throw 'El respaldo contiene una ruta insegura.'
    }
}
& tar.exe -xf $backup -C $expanded
if ($LASTEXITCODE -ne 0) { throw 'No se pudo examinar el respaldo local.' }
$sourceDatabase = Join-Path $expanded 'databases\library.db'
if (-not (Test-Path $sourceDatabase) -or (Get-Item $sourceDatabase).Length -lt 4096) {
    throw 'No se encontro library.db dentro del respaldo. No se modifica la instalacion actual.'
}
$sourceHash = (Get-FileHash $sourceDatabase -Algorithm SHA256).Hash.ToLowerInvariant()
$bookFiles = @(Get-ChildItem (Join-Path $expanded 'files') -Recurse -File -Filter '*.book' -ErrorAction SilentlyContinue)
if ($bookFiles.Count -eq 0) { throw 'No se encontraron los archivos PDF importados. La instalacion actual no se modifica.' }
Write-Host ('Respaldo correcto; libros propios: ' + $bookFiles.Count) -ForegroundColor Green

# Install under a NEW package. A signature mismatch with the old one is impossible.
$oldV3 = Invoke-ADB "-d shell pm path $newPackage"
if ($oldV3.Output -match 'package:') {
    throw 'Ya existe una version V3. No se ha modificado ni ella ni el lector anterior.'
}
Write-Host 'Instalando nueva version SIN desinstalar la anterior...' -ForegroundColor Cyan
$install = Invoke-ADB ('-d install "' + $apk + '"')
if ($install.Exit -ne 0 -or $install.Output -notmatch 'Success') {
    throw "No pudo instalarse V3: $($install.Output) $($install.Errors). La aplicacion anterior sigue intacta."
}

try {
    Invoke-ADB "-d shell am force-stop $newPackage" | Out-Null
    $newAccess = Invoke-ADB "-d shell run-as $newPackage pwd"
    if ($newAccess.Exit -ne 0) { throw 'Android no permite abrir la carpeta privada de la nueva version.' }
    Write-Host 'Transfiriendo biblioteca a la nueva version...' -ForegroundColor Cyan
    # Copy individual files with Android dd. This avoids the old unverified
    # adb exec-in + tar extraction path and checks every file on the device.
    $allFiles = @(Get-ChildItem $expanded -Recurse -File | Sort-Object FullName)
    foreach ($item in $allFiles) {
        $relative = $item.FullName.Substring($expanded.Length + 1).Replace('\','/')
        if ($relative -notmatch '^(files|databases|shared_prefs|no_backup)/[A-Za-z0-9_./-]+$') {
            throw "Ruta no compatible para restauracion: $relative. La aplicacion anterior permanece intacta."
        }
        $relativeDir = $relative.Substring(0, $relative.LastIndexOf('/'))
        $makeDir = Invoke-ADB "-d shell run-as $newPackage mkdir -p $relativeDir"
        if ($makeDir.Exit -ne 0) { throw "No se pudo crear ${relativeDir}: $($makeDir.Errors)" }
        Stream-ADB "-d exec-in run-as $newPackage dd of=$relative bs=65536" $item.FullName $true | Out-Null
        $info = Invoke-ADB "-d shell run-as $newPackage stat -c %s $relative"
        if ($info.Exit -ne 0 -or $info.Output.Trim() -ne ([string]$item.Length)) {
            throw "La copia de $relative no coincide con el tamano original. La aplicacion anterior sigue intacta."
        }
    }

    # Independently verify database contents by streaming it BACK from Android.
    $deviceDatabase = Join-Path $folder 'BaseDeDatos_LeidaDesdeTablet.db'
    Stream-ADB "-d exec-out run-as $newPackage cat databases/library.db" $deviceDatabase $false | Out-Null
    if ((Get-FileHash $deviceDatabase -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sourceHash) {
        throw 'La base de datos no coincide byte a byte con la copia. La aplicacion anterior sigue intacta.'
    }
    # Full-byte verification of an actual imported book (not just its filename).
    if ($bookFiles.Count -gt 0) {
        $sample = $bookFiles[0]
        $samplePath = $sample.FullName.Substring($expanded.Length + 1).Replace('\','/')
        $deviceBook = Join-Path $folder 'LibroComprobado.book'
        Stream-ADB "-d exec-out run-as $newPackage cat $samplePath" $deviceBook $false | Out-Null
        if ((Get-FileHash $deviceBook -Algorithm SHA256).Hash.ToLowerInvariant() -ne
            (Get-FileHash $sample.FullName -Algorithm SHA256).Hash.ToLowerInvariant()) {
            throw 'Un PDF restaurado no coincide con el original. La aplicacion anterior sigue intacta.'
        }
    }
    Write-Host 'Base de datos y archivos de libros comprobados.' -ForegroundColor Green

    # Launch to trigger normal library initialization. The previous app remains recoverable.
    $launch = Invoke-ADB "-d shell monkey -p $newPackage -c android.intent.category.LAUNCHER 1"
    if ($launch.Exit -ne 0) { throw 'No se pudo iniciar la nueva version.' }
    Start-Sleep -Seconds 3
    $health = Invoke-ADB "-d shell run-as $newPackage test -f databases/library.db"
    if ($health.Exit -ne 0) { throw 'La nueva version no conserva la base de datos despues de iniciar.' }

    # Hide, do not uninstall, the old version. All of its data remain for easy rollback.
    $disable = Invoke-ADB "-d shell pm disable-user --user 0 $oldPackage"
    if ($disable.Exit -ne 0 -or $disable.Output -notmatch 'disabled') {
        Write-Warning 'No se pudo ocultar la version antigua. Permanece instalada con sus libros.'
    }
    Write-Host 'TURN THE PAGE BY ARMANDO ANOTA - INSTALACION TERMINADA' -ForegroundColor Green
    Write-Host ('Respaldo conservado: ' + $folder)
    Write-Host 'La version anterior no fue desinstalada ni perdio sus datos.'
}
catch {
    $message = $_.Exception.Message
    # Delete only the new temporary installation, NEVER the user's original books.
    $cleanup = Invoke-ADB "-d uninstall $newPackage"
    Write-Host 'No se toco ni elimino la aplicacion anterior.' -ForegroundColor Yellow
    Write-Host ('Respaldo conservado: ' + $folder)
    throw "La migracion nueva no paso la comprobacion: $message"
}