# Prueba única desde tu escritorio

Ejecuta `work/DesktopInitializeOnce.exe` desde el Explorador de Windows, sin administrador. Mantén las condiciones ya confirmadas: sin controlador físico accesible, RC+ cerrado y AutoConnect desactivado. El programa comprueba el contexto, los archivos, AutoConnect y que no haya procesos Epson antes de iniciar.

Esta prueba sí puede iniciar RC+. Ejecuta únicamente InitializeObserve con instancia10 y worker x86 original; no ejecuta Inventory ni Connect. Puede guardar preferencias normales de RC+ en tu perfil. No cambia permisos, no instala software y no termina procesos Epson residuales.

Espera el mensaje final (normalmente unos30segundos, espera externa45). No ejecutes el archivo otra vez. Dime «listo» y revisaré el resultado. Un código0 del lanzador no basta para aceptar la etapa: hay que leer el resultado del supervisor, los eventos, la limpieza y la observación externa.

Evidencia: `work/native-desktop-v8`. Un recibo reservado antes del arranque impide repetir. No borres, renombres o copies ese directorio para reintentar. Si el lanzador se detiene en un requisito previo, conserva su mensaje y dime qué ocurrió.

Supervisor elegido expresamente:763458C8057D7312B178F2AD9E577FEFBCCCAA9DC5259EEFF5A68B62F3C86661, con corrección de atribución PID0, ya verificada por CI. Worker/request/core originales sin cambios. Esta ejecución todavía NO ha ocurrido; el agente solo ejecutó pruebas sintéticas y el rechazo del contexto restringido.

Verificación: pruebas sintéticas de código de salida, recibo visible antes del inicio del hijo, rechazo de repetición conservando evidencia, timeout acotado y ausencia de cierre automático del hijo. Revisión independiente sin asuntos importantes pendientes; corrigió la etiqueta del código del lanzador para no confundirla con una salida del supervisor ante timeout externo.
