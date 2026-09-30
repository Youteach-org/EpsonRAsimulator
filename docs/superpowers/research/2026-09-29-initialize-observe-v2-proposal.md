# Propuesta de segundo intento InitializeObserve — NO AUTORIZADO, NO EJECUTADO

Preparación únicamente. La instrucción vigente del usuario prohíbe repetir InitializeObserve y ejecutar Inventory/Connect. Un «sigue» no revoca esa restricción. Este comando requiere una autorización explícita nueva antes de ejecutarlo.

## Alcance

Un único supervisor x64 y worker x86, ServerInstance=10, límite interno30segundos y espera externa40segundos. El worker carga la API instalada, construye Spel, asigna ServerInstance10, llama Initialize y finalmente Dispose. Puede iniciar un proceso Epson aunque no aparezca ventana. No selecciona ni demuestra conexión con C4 Sample; no llama Inventory/GetCurrentConnectionInfo/Connect ni operaciones de proyecto, robot, movimiento, tareas, I/O o SPEL. No reintento ni alternativa x64 del worker. No termina procesos Epson compartidos.

Se conservan las confirmaciones previas de AutoConnectOFF, ausencia de controlador físico, instancia10 libre y RC+cerrado. Última comprobación no detectó procesos Epson. El intento original permanece inconcluso; un nuevo resultado nunca lo reemplaza.

## Artefactos preparados

Directorio separado:
`C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work\native-capture-sealed`

Supervisor sellado por CI en c571aa96b0edf91587a5f43c14ca2cfbf06d7880: Windows CI116 y Android CI541 SUCCESS. Artefacto11063812312, ZIP SHA256 C2D723A9D19742F4B81C6520696692955B4FC5DFAC52AAA45768AD76C6CF1AD1, ejecutable27136bytes. Descargado y verificado contra manifiesto y hash. Se conserva su .config. Worker x86 y DLL compartida se copiaron byte a byte de los artefactos revisados anteriores; la DLL debe estar junto al supervisor en x64 y junto al worker en x86. No se copiaron binarios propietarios.

Pruebas locales del supervisor sellado con fixture sintético, después de completar sus dependencias: normal244ms; herencia de salida2122ms; timeout2125ms; evidencia existente preservada y worker no iniciado.4/4PASS. No ejecución nativa. Los hashes exactos están incluidos en el comando.

Solicitud preparada: `{"stage":"InitializeObserve","installRoot":"C:\\EpsonRC70","target":"C4 Sample","serverInstance":10,"approved":true}`. El campo approved es una entrada propuesta, NO autorización del usuario.

## Comando exacto propuesto

Se ejecutaría como comandos PowerShell, sin cambiar ExecutionPolicy ni ejecutar un archivo .ps1. No se ha ejecutado. Usa consola independiente oculta y archivo de resultado nuevo; no redirige stdout/stderr del supervisor ni usa espera ilimitada. Reserva además un recibo antes de iniciar el proceso: incluso un recibo vacío impide un segundo lanzamiento accidental.

```powershell
$ErrorActionPreference = 'Stop'
$runRoot = 'C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work\native-capture-sealed'
$hashes = @{
 'x64/EpsonRa.Bridge.Research.Supervisor.exe.config' = '051099983B896673909E01A1F631B6652ABB88DA95C9F06F3EFEF4BE033091FA'
 'x64/EpsonRa.Bridge.Research.Supervisor.exe' = '4A43FCBB4A3591E16929E3325756DEE2B8E7A8C6B4F3601EBFD23F3452171AF0'
 'x86/EpsonRa.Bridge.Research.Worker.exe' = '0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4'
 'x64/EpsonRa.Bridge.Research.dll' = 'ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF'
 'x86/EpsonRa.Bridge.Research.dll' = 'ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF'
 'initialize-observe.proposed.json' = '410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B'
}
foreach ($item in $hashes.GetEnumerator()) {
 if ((Get-FileHash -LiteralPath (Join-Path $runRoot $item.Key)).Hash -ne $item.Value) { throw 'Artifact mismatch; no execution' }
}
if (@(Get-Process | Where-Object { $_.ProcessName -match '^(erc70|erc70PServer|EpsonRa.*)$' }).Count) { throw 'Existing Epson/research process; no execution' }
$resultPath = Join-Path $runRoot 'initialize-observe-v2.result.json'
$receiptPath = Join-Path $runRoot 'initialize-observe-v2.execution.json'
if (Test-Path -LiteralPath $resultPath) { throw 'Existing result; no retry' }
# CreateNew receipt is reserved BEFORE Process.Start. An empty receipt also blocks replay.
$receipt = [IO.File]::Open($receiptPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$state = [ordered]@{startedUtc=[DateTime]::UtcNow.ToString('o');status='PREPARED_TO_START';supervisorPid=$null;supervisorExited=$false;supervisorExitCode=$null;resultExists=$false;resultBytes=0;cleanup='UNKNOWN'}
$saveReceipt = {
 $bytes = [Text.Encoding]::UTF8.GetBytes(($state | ConvertTo-Json))
 $receipt.Position = 0
 $receipt.SetLength(0)
 $receipt.Write($bytes,0,$bytes.Length)
 $receipt.Flush($true)
}
$process = $null
try {
 & $saveReceipt
 $startInfo = New-Object Diagnostics.ProcessStartInfo
 $startInfo.FileName = Join-Path $runRoot 'x64/EpsonRa.Bridge.Research.Supervisor.exe'
 $startInfo.Arguments = '--worker "' + (Join-Path $runRoot 'x86/EpsonRa.Bridge.Research.Worker.exe') + '" --request "' + (Join-Path $runRoot 'initialize-observe.proposed.json') + '" --timeout-seconds 30 --result-file "' + $resultPath + '"'
 $startInfo.UseShellExecute = $true
 $startInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
 $process = [Diagnostics.Process]::Start($startInfo)
 $state.supervisorPid = $process.Id
 $state.status = 'SUPERVISOR_STARTED'
 & $saveReceipt
 $state.supervisorExited = $process.WaitForExit(40000)
 if ($state.supervisorExited) {
  $state.supervisorExitCode = $process.ExitCode
  $state.status = 'SUPERVISOR_EXIT_OBSERVED'
 } else {
  $state.status = 'INCONCLUSIVE_OUTER_TIMEOUT'
 }
} catch {
 $state.status = 'INCONCLUSIVE_LAUNCH_OR_CAPTURE_ERROR'
 # Preserve UNKNOWN and do not retry/kill any process.
} finally {
 $state.resultExists = Test-Path -LiteralPath $resultPath
 if ($state.resultExists) { $state.resultBytes = (Get-Item -LiteralPath $resultPath).Length }
 try { & $saveReceipt } finally { $receipt.Dispose(); if ($null -ne $process) { $process.Dispose() } }
}
$state | ConvertTo-Json
if ($state.resultExists) { Get-Content -Raw -LiteralPath $resultPath }

```

## Interpretación y límites

El recibo describe la captura externa; nunca sustituye al resultado del supervisor. SUPERVISOR_EXIT_OBSERVED no significa éxito nativo. Verificar JSON completo, código real del supervisor, concordancia de códigos, resultado del worker, observación y cleanup. Un archivo vacío/parcial, timeout externo, error de escritura, código74, estado desconocido o proceso residual impide aceptar la etapa. El recibo mantiene cleanupUNKNOWN por diseño; cualquier confirmación debe provenir del resultado validado. La espera externa acota el proceso, no garantiza que las llamadas al sistema de archivos terminen en un tiempo fijo.

Un timeout externo no mata supervisor, worker ni Epson y no autoriza reintento. La captura nueva reduce el riesgo de salida perdida, pero no prueba la causa exacta del intento original ni ausencia de tráfico breve/USB o conexiones implícitas.

## Decisión pendiente

Autorizar o mantener prohibido **un único segundo intento InitializeObserve** con estos artefactos/comando. Inventory y Connect siguen fuera del alcance. No se solicita repetir las confirmaciones ambientales ya dadas.



## Verificación del comando externo

El comando completo se ejecutó con un directorio aislado y worker sintético (sin Epson), sustituyendo únicamente rutas y hashes de los datos sintéticos. A2026-09-30T00:58:45Z registró SUPERVISOR_EXIT_OBSERVED, exit0 y378bytes de JSON completo. Se preservó el resultado y el marcador del worker con nombres distintos; un segundo lanzamiento propuesto fue rechazado por CreateNew porque el recibo ya existía, aun sin archivo de resultado. Los hashes del recibo y del resultado preservado no cambiaron y no apareció otro marcador del worker. Esta comprobación no ejecutó InitializeObserve real.




## Incidente de preparación sintética

El primer ensayo local del supervisor sellado se lanzó sin colocar la DLL Research junto al ejecutable x64 y venció la espera externa de4.5segundos. El usuario aportó una captura del diálogo EpsonRa.Bridge.Research.Supervisor.exe con excepción0xe0434352. La captura confirma un fallo de la aplicación, pero no identifica por sí sola la DLL ausente. No se encontraron eventos1026 coincidentes. Al añadir la DLL revisada (hashACED9B8F...) las cuatro pruebas sintéticas pasaron. El paquete inicial era incompleto; el supervisor sellado no fue modificado. Ningún ensayo utilizó el worker Epson ni ejecutó InitializeObserve real. No se terminó ningún proceso Epson. No contar este ensayo fallido como ejecución nativa ni ocultarlo en la evidencia.


Verificación final del comando con el supervisor sellado y worker sintético: 2026-09-30T01:06:24.1299465Z, SUPERVISOR_EXIT_OBSERVED, exit0, JSON378bytes. El archivo de solicitud Epson no se ejecutó. El paquete preparado conserva el supervisor sellado4A43FCBB..., configuración05109998..., DLLACED9B8F... en ambos directorios, worker0B17E319... y solicitud original41064425....
