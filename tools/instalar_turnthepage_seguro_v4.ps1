$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$oldPackage = 'com.avstaim.listai.tablet.armando.smart'
$newPackage = 'com.avstaim.listai.tablet.armando.v3'
$apkUrl = 'https://raw.githubusercontent.com/Youteach-org/EpsonRAsimulator/b20216728a1714820dd762f4bf74bb57a7fd8ce7/apk/TurnThePage_By_Armando_Anota_V3.apk'
$expectedSha = 'a0da60831efa324b93e10549370797774a9038360dd4272a10e8b58f654fa6ad'
$cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
$adb = if ($cmd) { $cmd.Source } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (!(Test-Path $adb)) { throw 'No se encontro adb.exe de Android SDK.' }

function A([string]$args) {
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo.FileName = $adb
    $p.StartInfo.Arguments = "-d $args"
    $p.StartInfo.UseShellExecute = $false
    $p.StartInfo.RedirectStandardOutput = $true
    $p.StartInfo.RedirectStandardError = $true
    [void]$p.Start()
    $stdout = $p.StandardOutput.ReadToEnd()
    $stderr = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "ADB $args : $stdout $stderr" }
    return $stdout.Trim()
}
function BinaryFromAndroid([string]$args, [string]$destination) {
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo.FileName = $adb
    $p.StartInfo.Arguments = "-d exec-out $args"
    $p.StartInfo.UseShellExecute = $false
    $p.StartInfo.RedirectStandardOutput = $true
    $p.StartInfo.RedirectStandardError = $true
    [void]$p.Start()
    $f = [System.IO.File]::Create($destination)
    try { $p.StandardOutput.BaseStream.CopyTo($f) }
    finally { $f.Dispose() }
    $errors = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "Error al leer desde Android: $errors" }
}
function Sha([string]$path) {
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function CheckAndroidFile([string]$relative, [string]$local) {
    $answer = A "shell run-as $newPackage sha256sum $relative"
    if ($answer -notmatch '^([a-fA-F0-9]{64})\s') { throw "No se pudo verificar $relative" }
    if ($Matches[1].ToLowerInvariant() -ne (Sha $local)) {
        throw "Error de integridad en $relative"
    }
}

if ((A 'get-state') -ne 'device') { throw 'Conecta y autoriza la tablet por USB.' }
if ((A "shell pm path $oldPackage") -notmatch 'package:') {
    throw 'No se encontro el lector anterior. No se modifico nada.'
}
[void](A "shell run-as $oldPackage pwd")
if ((A "shell pm list packages $newPackage") -match [regex]::Escape($newPackage)) {
    throw 'La version nueva ya esta instalada; no se sobreescribira ni se borraran sus datos.'
}
$folder = Join-Path $env:USERPROFILE ('TurnThePage_Respaldos\Migracion_Segura_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
$expanded = Join-Path $folder 'VerificacionLocal'
New-Item -ItemType Directory -Force -Path $expanded | Out-Null
$archive = Join-Path $folder 'Biblioteca.tar'
$apk = Join-Path $folder 'TurnThePage_By_Armando_Anota_V3.apk'
Write-Host 'Recuperando APK desde el repositorio...' -ForegroundColor Cyan
Invoke-WebRequest -UseBasicParsing -Uri $apkUrl -OutFile $apk
if ((Sha $apk) -ne $expectedSha) { throw 'APK incorrecta: no se altero la aplicacion anterior.' }

[void](A "shell am force-stop $oldPackage")
$dirs = @()
foreach ($d in @('files', 'databases', 'shared_prefs', 'no_backup')) {
    try { [void](A "shell run-as $oldPackage test -d $d"); $dirs += $d } catch {}
}
if ('files' -notin $dirs -or 'databases' -notin $dirs) { throw 'No se encontro la biblioteca original.' }
Write-Host 'Respaldando datos, libros y marcadores...' -ForegroundColor Cyan
BinaryFromAndroid "run-as $oldPackage tar -cf - $($dirs -join ' ')" $archive
$names = @(& tar.exe -tf $archive)
if ($LASTEXITCODE -ne 0) { throw 'Respaldo tar ilegible.' }
foreach ($name in $names) {
    $n = $name.Replace('\','/')
    if ($n.StartsWith('/') -or $n -match '(^|/)\.\.(/|$)' -or $n -match '^[a-zA-Z]:') { throw 'Ruta insegura en respaldo.' }
}
& tar.exe -xf $archive -C $expanded
if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar el respaldo.' }
$db = Join-Path $expanded 'databases\library.db'
if (!(Test-Path $db) -or (Get-Item $db).Length -lt 4096) { throw 'Falta library.db en el respaldo.' }
$books = @(Get-ChildItem -Path (Join-Path $expanded 'files') -Recurse -File -Filter '*.book')
if ($books.Count -eq 0) { throw 'No se encontro ningun libro dentro del respaldo.' }
Write-Host "Respaldo comprobado: $($books.Count) libros." -ForegroundColor Green

$installed = $false
$stage = "/sdcard/Android/data/$newPackage/files/.tt_restore_$([guid]::NewGuid().ToString('N'))"
try {
    Write-Host 'Instalando la nueva app SIN desinstalar la anterior...' -ForegroundColor Cyan
    $answer = A ('install "' + $apk + '"')
    if ($answer -notmatch 'Success') { throw "La instalacion no concluyo: $answer" }
    $installed = $true
    [void](A "shell am force-stop $newPackage")
    [void](A "shell run-as $newPackage pwd")
    [void](A "shell mkdir -p $stage")
    $all = @(Get-ChildItem -Path $expanded -Recurse -File | Sort-Object FullName)
    $i = 0
    foreach ($item in $all) {
        $i++
        $rel = $item.FullName.Substring($expanded.Length + 1).Replace('\','/')
        if ($rel -notmatch '^(files|databases|shared_prefs|no_backup)/[A-Za-z0-9_./-]+$' -or
            $rel -match '(^|/)\.\.(/|$)') { throw "Ruta no compatible: $rel" }
        $dir = $rel.Substring(0, $rel.LastIndexOf('/'))
        [void](A "shell run-as $newPackage mkdir -p $dir")
        $remote = "$stage/$('{0:D7}' -f $i).bin"
        [void](A ('push "' + $item.FullName + '" ' + $remote))
        [void](A "shell run-as $newPackage cp $remote $rel")
        CheckAndroidFile $rel $item.FullName
        [void](A "shell rm -f $remote")
        Write-Host "Verificado $i/$($all.Count): $rel"
    }
    [void](A "shell rmdir $stage")
    $checkDb = Join-Path $folder 'BaseDeDatos_Verificada.db'
    BinaryFromAndroid "run-as $newPackage cat databases/library.db" $checkDb
    if ((Sha $checkDb) -ne (Sha $db)) { throw 'library.db no coincide byte a byte.' }
    $first = $books[0]
    $relBook = $first.FullName.Substring($expanded.Length + 1).Replace('\','/')
    $checkBook = Join-Path $folder 'Libro_Verificado.book'
    BinaryFromAndroid "run-as $newPackage cat $relBook" $checkBook
    if ((Sha $checkBook) -ne (Sha $first.FullName)) { throw 'El libro verificado no coincide.' }
    [void](A "shell monkey -p $newPackage -c android.intent.category.LAUNCHER 1")
    Start-Sleep -Seconds 3
    [void](A "shell run-as $newPackage test -f databases/library.db")
    Write-Host 'NUEVA APP Y BIBLIOTECA COMPROBADAS.' -ForegroundColor Green
    try { [void](A "shell pm disable-user --user 0 $oldPackage") }
    catch { Write-Warning 'La version antigua seguira visible, pero sus datos estan intactos.' }
    Write-Host "Respaldo local: $folder"
    Write-Host 'La version anterior sigue instalada y sus libros NO se borraron.' -ForegroundColor Green
} catch {
    $problem = $_.Exception.Message
    try { [void](A "shell rm -rf $stage") } catch {}
    if ($installed) { try { [void](A "uninstall $newPackage") } catch {} }
    Write-Warning "Instalacion nueva detenida: $problem"
    Write-Host "Aplicacion original intacta. Respaldo: $folder" -ForegroundColor Yellow
    throw
}