# Propuesta de entrega local C4

Estado: especificación aprobada por el usuario (respuesta «ok»); implementación pendiente. Se divide en planes por bloque; el primero es docs/superpowers/plans/2026-10-02-local-project-points.md.

## Objetivo y límites

Completar el recorrido acordado: abrir C4, mover juntas y TCP con solución IK válida, previsualizar, guardar puntos, operar pinza y ejecutar pick-and-place simulado; guardar y reabrir el trabajo. Mantener RC+ Trainer y Visual Lab sobre SharedRuntime. RC+ 7.5.3 y el bridge permanecen separados; no hay conexión a hardware ni nueva prueba nativa.

Alternativas consideradas: entregar únicamente el visor actual reduciría el objetivo; continuar el bridge no completa las capacidades Android ausentes. Se recomienda completar la simulación local en tres bloques verificables.

## Bloque 1: proyecto local y captura de puntos

Añadir Crear proyecto local a la barra existente, con nombre validado e identidad generada; usar el mismo slot store, metadatos y escritura transaccional del coordinador. No simular una importación desde una carpeta del usuario. Si hay trabajo sin guardar, reutilizar Guardar/Descartar/Cancelar. Cancelar no modifica el estado.

Capturar punto desde la pose canónica actual, conservando juntas preferidas y el marco de simulación explícito. No llamar Epson a coordenadas aún no calibradas. La orientación debe proceder de la transformación FK completa con una convención documentada, no de ceros supuestos. Guardar P1/P2 y reabrir conserva pose, juntas y nombres.

## Bloque 2: objetivo TCP y vista previa

Añadir un solver numérico de posición limitado por las juntas del C4, con semilla en la postura actual, iteraciones acotadas y resultados explícitos: convergencia, sin solución encontrada o entrada inválida. Sin solución encontrada no se presenta como prueba matemática de inalcanzabilidad. No se afirma control completo de orientación en este bloque.

Trabajar internamente en el marco CAD que ya comparten render y FK, con conversión centralizada para las coordenadas mostradas. TCP incluye la transformación de herramienta seleccionada. Un objetivo se considera resuelto con error de posición de hasta 1 mm y todas las juntas dentro de límites. Probar objetivos obtenidos por FK y casos degenerados/no finitos; no aceptar un resultado solo porque terminó la iteración.

Modo TCP separado del gesto de cámara: arrastre en un plano seleccionado y ajuste del tercer eje mediante control explícito. Al arrastrar, actualizar solo una pose candidata y su fantasma. Aplicar es una acción explícita; cancelar, cambiar robot/herramienta o modificar la postura base invalida la propuesta. El solver trabaja fuera del hilo de interfaz; resultados atrasados se descartan mediante identificador de solicitud.

El renderer proyecta estado; no decide límites ni modifica el runtime. La vista distingue robot actual y fantasma con estilo propio. No se anuncia evitación de colisiones ni calibración RC+.

## Bloque 3: pinza, secuencia y persistencia

Integrar una celda de entrenamiento determinista con pieza y pinza de dos dedos usando los modelos de dominio existentes. La pose de montaje deriva de la misma FK que mueve el robot; no se introduce un segundo estado de robot. Transformaciones y render de herramienta deben respetar orientación, no solo trasladar cajas.

Extender las tareas canónicas con movimiento articular simulado hacia puntos que tengan solución válida, sincronizado con el reloj existente. No interpretar Go/Move de SPEL+ como equivalentes sin un adaptador verificado. La primera secuencia se construye con acciones de entrenamiento: ir a aproximación, recoger, cerrar, retirar, trasladar, colocar y abrir. Las transiciones esperan que movimiento y pinza terminen. Una sola tarea posee el movimiento del robot; conflictos se rechazan sin sobrescribir la tarea activa.

Inicio, pausa, continuar y detener actúan sobre el mismo reloj y estado de tarea. Pausar mantiene postura y pieza; detener cancela movimiento pendiente conservando postura actual y estado explícito de la pinza. La app pasa a pausa al dejar primer plano. Rotar o recomponer la pantalla no inicia otro temporizador ni otra secuencia.

Extender el sidecar con versión y validación de datos para celda, herramienta y secuencia de entrenamiento. Las sesiones antiguas siguen cargando. Al reabrir no se reanuda movimiento automáticamente: restaurar configuración, puntos y postura en pausa; reiniciar la secuencia desde inicio explícito. Conservar los recursos nativos opacos byte a byte. Snapshot inválido no reemplaza el estado válido. La persistencia no crea un clon de SharedRuntime.

## Aceptación de la entrega

1. Desde instalación limpia crear proyecto local, abrir C4, mover juntas y capturar P1/P2 sin importar archivos RC+.
2. Arrastrar objetivo TCP, ver fantasma, cancelar sin mutación y aplicar una solución dentro de tolerancia/límites. Error de IK no mueve el robot.
3. Ejecutar una secuencia de recoger y colocar: la pieza se adjunta a la pinza, sigue el robot y queda liberada en el destino. Pausa y detener funcionan durante movimiento y cierre.
4. Guardar, recrear Activity y reiniciar proceso: recuperar proyecto, puntos y configuración sin movimiento automático ni pérdida de recursos.
5. Pruebas unitarias para solver, control de solicitudes, movimiento/reloj, agarre y migración; pruebas instrumentadas para creación, botones y estado tras recreación; aceptación visual del modelo y gestos en dispositivo/emulador.
6. CI genera APK del commit revisado. Entrega incluye hash, instrucciones breves y límites reales. Un build verde no sustituye aceptación visual.

## Orden y revisión

Primero proyecto/puntos; luego TCP/fantasma; después pinza/secuencia y su persistencia. Cada bloque conserva el recorrido previo. Antes de implementación preparar plan por archivos y pruebas a partir de esta especificación revisada. No mezclar estos cambios en PR24 del bridge: usar una rama de entrega local desde el código Android verificado, con base revisada antes de publicar.
