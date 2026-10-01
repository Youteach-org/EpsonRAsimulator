# Transición de compatibilidad: preparación, sin instalación autorizada

Estado comprobado: Windows 11 21H2, build 22000.376; EPSON RC+ 7.0 paquete 7.5.3; .NET Framework Release 528449. El preflight v5 bloqueó el lanzamiento. Hubo cuatro intentos nativos de InitializeObserve, no cinco.

La carpeta de instalación indicada por el usuario contiene 7.5.3 según Readme_English.txt. Su setup.exe tiene firma Authenticode válida de SEIKO EPSON CORPORATION. La versión 7.2.0 del bootstrapper no identifica la versión del paquete. No se ejecutó ni se reparó la instalación.

Epson documenta soporte Windows 11 desde 7.5.4. Esa diferencia justifica revisar el entorno, pero no demuestra que una actualización resuelva el timeout. Referencias:
- https://epson.com/Support/wa00852
- https://files.support.epson.com/far/docs/e_EPSONRC70_754_ReleaseNotes.pdf

## Medio necesario

Se necesita un medio autorizado de la familia RC+ 7.5.4 y sus instrucciones. El índice oficial europeo ofrece v754A_with_R1.zip (4233979344 bytes); la inspección parcial del directorio ZIP encontró cifrados setup.exe y Readme_English.txt. No se descargó el archivo completo, no se intentó descifrarlo y no se ejecutó código. No confundir RC+7 con RC+8 ni con RC+Express.

Cuando se facilite el medio correcto: identificar versión por README/metadatos del paquete; calcular SHA256; verificar firma del instalador; leer requisitos de actualización/desinstalación y reinicio. La firma del medio7.5.3 no valida otro archivo ni garantiza compatibilidad. No ejecutar el bootstrapper para descubrir su versión.

## Preservación antes de instalar

Inventario de solo lectura: projects contiene159 archivos,2133097 bytes; Virtual contiene1467 archivos,3451461368 bytes; Simulator contiene99 archivos,9022192 bytes. Config, Backup y Status no mostraron archivos en esa enumeración. Son cifras de preparación, no una copia de seguridad realizada.

Preparar una copia local verificable de los proyectos y del estado/configuración pertinente, incluyendo la definición de C4 Sample, una vez identificada su ubicación. Mantener privados los archivos de usuario, configuración y licencias. Conservar el medio7.5.3 como referencia de recuperación. Una copia de carpetas no equivale a un respaldo completo del instalador/registro/controladores y no permite prometer rollback completo.

## Decisión de instalación

Presentar el medio exacto, hash/firma, componentes afectados, instrucciones del fabricante, respaldo y necesidad de reinicio antes de pedir autorización para instalar. El usuario ha autorizado diagnóstico nativo necesario, no una actualización silenciosa del sistema. No cambiar firmware de controladores, Windows, .NET, opciones de seguridad ni flags de compatibilidad como parte de este plan.

## Después de una actualización autorizada

Comprobar versión real del paquete y nuevos hashes de la API instalada. Revalidar MetadataOnly/LoadOnly para la API nueva antes de InitializeObserve; la prueba de carga antigua corresponde a7.5.3. Usar nuevas rutas de evidencia; el preflight v5 existente es inmutable. Mantener worker original, instancia10, política exacta C4 Sample, AutoConnectOFF y aislamiento físico; cualquier cambio de condición se revisa explícitamente.

La observación externa es un bloqueo independiente: el muestreo actual encuentra cientos de procesos cuya ruta no puede leer. El nuevo guard de PID0 corrige atribución TCP, pero no elimina esos huecos. No aceptar InitializeObserve solamente porque Initialize/Dispose retornen ni relajar las comprobaciones. Inventory/Connect permanecen pendientes.

## Información para Epson, si resulta necesaria

Solicitar confirmación de la revisión7.5.4 apropiada para Windows11 y del procedimiento soportado de actualización desde7.5.3; cómo verificar la opción RC+API para un Virtual Controller sin abrir una conexión implícita; y qué evidencia local puede recopilarse cuando Spel.Initialize con ServerInstance10 no retorna. No enviar mensajes ni archivos sin instrucción del usuario.
