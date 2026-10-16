# Secuencias propias y colisiones en Visual Lab
Estado: propuesta para revision del usuario; no implementada.
Base revisada: 63fb7a1. Navegacion de retorno: correccion independiente autorizada.

## Objetivo y criterio de exito
El usuario quiere practicar con un simulador creible de Epson, no repetir un ciclo
oculto. Debe poder mover el C4, guardar posiciones, crear y guardar su propio
recoger/colocar y entender por que se detiene. Solo Start/Resume inicia ejecucion.
La primera entrega protege pinza, pieza y soportes de la celda. No se presenta
como comprobacion de colisiones de todos los eslabones ni controlador Epson real.

## Lo que ya existe
- VisualLabPointController.captureCurrent usa C4PointCapture y guarda juntas,
  pose y marco SIMULATION_Z_UP en TeachPoint.
- Controles articulares, previsualizacion TCP y puntos compartidos.
- VisualProgrammingPanel edita argumentos de fuentes existentes mediante el
  adaptador SPEL; no es un editor ejecutable de ejercicios locales.
- TrainingSequence genera diez acciones fijas; TrainingMotionRuntime ejecuta
  MoveJoints/WaitForGripper dentro del coordinador canonico.
- Guardado local restaura la sesion detenida. TrainingCellCodec tiene extension
  fija v3; una secuencia variable requiere evolucion del formato con limites.
- WorkcellSceneProjection dibuja dedos/palma; esas formas no constituyen aun
  un detector operativo. Las pruebas SAT existentes solo verifican el preset.

## Alternativas
A. Editor local de pasos que reutiliza el motor existente (recomendado).
   Permite aprender y ensenar posturas sin afirmar equivalencia SPEL.
B. Ejecutar directamente el editor de codigo Epson: exige validar antes mas
   semantica y movimientos RC+; no es un atajo fiable para esta entrega.
C. Exponer solo cinco posiciones del preset: rapido, pero mantiene una receta
   rigida y no satisface crear una secuencia propia.

## Flujo del usuario
Visual Lab conserva la escena y los controles actuales, con panel Mi secuencia.
1. Preparar celda crea pieza, herramienta y soportes, sin ejecutar movimientos.
2. Mover manualmente el robot y Guardar posicion con nombre.
3. Anadir pasos: Ir a posicion, Abrir pinza, Cerrar pinza, Esperar.
4. Seleccionar, reordenar, editar o eliminar pasos antes de ejecutar.
5. Ejecutar, Pausar, Continuar y Detener muestran el paso actual y su resultado.
El ejercicio de demostracion se ofrece explicitamente como Cargar ejemplo.
Cambiar puntos o pasos NO mueve la pieza, las bases ni el robot. La geometria
de la celda permanece independiente del programa creado por el usuario.
Los mensajes visibles explican acciones y errores sin exponer clases o formatos.
Volver al inicio permanece visible en RC Trainer y Visual Lab; no descarta
proyectos. La salida de una ejecucion activa debe pausarla, nunca continuar oculta.

## Modelo y ejecucion
Una secuencia local pertenece al proyecto y tiene pasos con identificadores
estables. Ir a posicion referencia un TeachPoint capturado con sus seis juntas;
no se inventa IK ni se ejecuta una pose manual sin juntas verificables.
Al iniciar se valida y toma una instantanea de puntos, TCP, robot y pasos.
Punto ausente, marco no compatible, juntas fuera de limites o TCP distinto del
usado en la captura bloquean el inicio con un mensaje asociado al paso.
No se cambia silenciosamente un punto para hacerlo alcanzable.
Primer alcance: C4 y pinza de entrenamiento; movimientos articulares locales con
duracion explicita, no Move lineal Epson ni estimacion de tiempo industrial.
Limites iniciales: 100 pasos; movimiento 100..60000 ms; espera 0..60000 ms.
Abrir/cerrar usa la salida de la pinza y espera su final fisico, no un retardo
arbitrario. Durante ejecucion/pausa se bloquean edicion y comandos incompatibles.
Detener libera la reserva de movimiento; continuar solo existe tras una pausa
valida. Una colision requiere corregir la trayectoria y volver a iniciar.
Se conservan un solo estado canonico y un solo reloj, sin animacion paralela.
Una secuencia terminada no afirma automaticamente que hubo recoger/colocar:
muestra pasos terminados y estado real de pieza/pinza.

## Proteccion contra colisiones: primer bloque implementable
Extraer geometria compartida para que renderer y detector usen las mismas cajas
orientadas y transformaciones. Incluir soportes, palma, dedos segun apertura y
pieza libre/sujeta. La brida es una union permitida; la pieza apoyada y los dedos
en contacto con sus caras son contactos, no penetraciones.
Comprobar el recorrido antes de publicar cada avance canonico, incluyendo grandes
saltos de reloj, aproximacion inicial, jog manual y cambios de apertura.
AABB sirve solo como filtro; la decision de penetracion usa OBB.
El chequeo de recorrido debe subdividir conservadoramente segun desplazamiento
geometrico y rechazar si no puede establecer separacion dentro del presupuesto.
No basta comprobar extremos ni asumir que un intervalo fijo de20ms es seguro.
Definir tolerancia numerica unica en implementacion y probar tangencias y
obstaculos delgados; no introducir un margen que oculte penetraciones visibles.
Al detectar contacto invalido: conservar ultima postura valida, pausar reloj y
tarea, conservar agarre coherente, mostrar los dos objetos y el paso responsable.
El usuario puede detener y corregir; Resume no debe saltar el bloqueo.
La importacion/restauracion no ejecuta nada y comunica estados incompatibles.
No adaptar bases, piezas ni herramienta para que una trayectoria incorrecta pase.

## Entregas separadas
1. Geometria compartida + deteccion/paro + aviso, con prueba deliberada de choque.
   Esta entrega funciona sobre el ciclo actual y movimiento manual, sin editor nuevo.
2. Editor Mi secuencia + compilacion al motor + guardado/restauracion + pruebas
   de un ejercicio creado desde cero. Consume la proteccion ya comprobada.
Cada entrega tendra su plan y validacion propios. Metodo previamente elegido:
implementacion directa y revision independiente al cierre del bloque, no por tarea.
No mezclar refactors de PR24, cambios de protocolo Epson, Inventory o Connect.

## Integracion prevista
Geometria/detector: runtime/workcell y runtime/tool; WorkcellSceneProjection consume
las mismas dimensiones. Coordinacion: SimulationCoordinator, TrainingMotionRuntime,
SharedRuntime y sus comandos manuales; reportar bloqueo en estado canonico.
Editor: nuevo panel/controlador en ui/visual/training, reutilizando puntos
VisualLabPointController, TaskProgram y las acciones MoveJoints/WaitForGripper.
Persistencia: nueva extension versionada con lectura v1-v3, validacion acotada,
restauracion atomica detenida y conservacion de fuentes nativas byte a byte.

## Aceptacion
- La pinza toca la brida; pieza descansa sobre bases horizontales completas.
- Recorrido valido termina; uno que intenta atravesar una base se detiene y avisa.
- Un gran salto de reloj no permite atravesar un obstaculo entre extremos libres.
- Jog, apertura de pinza y pieza transportada usan la misma proteccion.
- Cambiar una posicion del editor no recoloca los soportes.
- Crear un ejercicio sin cargar ejemplo, guardarlo, reabrir detenido y ejecutarlo.
- Puntos borrados, TCP cambiado, nombres invalidos, entradas no finitas y limites
  de archivo producen errores claros sin ejecucion parcial ni corrupcion.
- Pausar/cambiar modo/rotar tablet/cerrar app no reanuda movimientos automaticamente.
- Pruebas unitarias del comportamiento y aceptacion Android en ambas orientaciones;
  video/capturas mas prueba en Samsung. Ningun aprobado global solo por mostrar
  Pick and place completed.

## Revision de la propuesta
Autorrevision: se separan detector y editor para entregas comprobables; no se
promete deteccion de todos los eslabones ni fidelidad Epson aun no validada.
La navegacion solicitada se entrega aparte. Falta revision del usuario de este
diseno antes del plan de implementacion del primer bloque.
