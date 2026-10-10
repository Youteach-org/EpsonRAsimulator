# Proyecto local y captura de puntos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task if the user selects native execution, or superpowers:subagent-driven-development if selected. Steps use checkbox syntax for tracking.

**Goal:** Crear un proyecto sin importar archivos RC+, capturar P1/P2 desde la postura real del simulador y recuperar esos datos al reabrir.

**Architecture:** Conservar ProjectPersistenceCoordinator como propietario de las transacciones y SharedRuntime como propietario del robot/puntos. Generalizar la solicitud de reemplazo existente para aceptar importación o creación local, conservando sus garantías frente a ediciones concurrentes. Una conversión pura FK→pose captura postura, marco y juntas preferidas; el sidecar las conserva sin tocar archivos nativos.

**Tech Stack:** Kotlin, Compose/Material3, JUnit4, Gradle 9.6.0/JDK17, SDK37, SceneView4.35.0. Sin nuevas dependencias de producción.

**Spec:** `docs/superpowers/specs/2026-10-02-local-release-design.md`, aprobada el 2026-10-02.

## Alcance y relación con la entrega

Este es el plan del bloque 1, no la entrega completa. Bloque 2: IK de posición, gesto TCP y fantasma. Bloque 3: movimiento canónico, pinza, secuencia y persistencia de celda. Se elaborarán sus planes sobre los contratos verificados del bloque anterior; no se supone que ya están implementados. La aceptación de proyecto/puntos es útil por sí misma.

Base leída: snapshot a3b6d637a0a040ba8ed8402a01a3bbcd6fb6cb9d; APK de referencia en outputs/release-audit. Antes de implementar, verificar cabeza/base en GitHub y árbol Android contra la base limpia de entrega. No publicar el snapshot local como historial Git. PR24 y sus fuentes Windows no forman parte de estos cambios.

## Global Constraints

- Un solo SharedRuntime y ProjectRuntime; RC+ Trainer y Visual Lab comparten puntos.
- RC+ 7.5.3 permanece separado. No repetir Epson, Inventory ni Connect.
- No atribuir calibración Epson a las coordenadas CAD/candidatas.
- No sobrescribir recursos nativos opacos; exportación nativa sigue excluyendo el sidecar.
- Creación y restauración se publican solo tras validar y persistir; fallo/cancelación conserva el proyecto anterior.
- El bloque no implementa IK, pinza, secuencia ni movimiento al reabrir.
- Mantener compatibilidad de lectura de sidecars existentes. Las coordenadas antiguas carecen de marco verificado y no se reclasifican por inferencia.
- Aplicar TDD a cambios de comportamiento. No declarar aceptación visual por tener CI verde.

## Review Focus

1. Crear mientras termina una importación/guardado: nunca publicar el puntero durable de una solicitud obsoleta (tarea 1).
2. Primera creación después de mover el robot/guardar puntos sin proyecto: no perder ese trabajo anónimo (tarea 1).
3. Orientación próxima a ±90° y rotación de herramienta: conservar la matriz completa, no solo XYZ (tarea 2).
4. Editar manualmente un punto capturado: conservar su marco, invalidar juntas preferidas que ya no representan la pose (tarea 3).
5. Muerte de proceso, versión vieja o corrupción del sidecar: recuperar solo datos válidos y no activar movimiento (tarea 4).

## Mapa de archivos

Los paths Kotlin indicados debajo de `main/` y `test/` son relativos a `app/src/main/java/mx/youteachtk/epsonrasimulator/` y `app/src/test/java/mx/youteachtk/epsonrasimulator/` respectivamente. `androidTest/` corresponde a `app/src/androidTest/java/mx/youteachtk/epsonrasimulator/`. Esta convención expande cada archivo a un path único.

## Task 1: crear proyecto local con reemplazo transaccional

**Files:**
- Create: `main/project/persistence/ProjectOpenRequest.kt`.
- Modify: `main/project/persistence/ProjectPersistenceCoordinator.kt`.
- Test: `test/project/persistence/ProjectPersistenceCoordinatorTest.kt` (reutilizar harness de ejecución, gateway y stores).

**Interfaces:**
- Consumes: `ProjectSessionPersistencePort.capture(): ByteArray`, `prepareRestore(ProjectSnapshot): ProjectSessionRestorePlan`, `ProjectSlotStore.save`, `ActiveProjectRecordStore`, `ProjectRuntime.loadProject(name, files)`.
- Produces: `ProjectPersistenceController.requestCreateLocal(name: String): Unit`; implementar también en los dobles existentes, sin default silencioso.
- Internal: `sealed interface ProjectOpenRequest` con `Import(val selection: DocumentTreeSelection)` y `Local(val name: String)`. Sustituye los campos pendientes/encolados ligados exclusivamente a importación; un solo contador de generación para ambas solicitudes.

- [ ] **Step 1 — RED:** añadir casos al harness existente: `createLocalWithoutProjectPersistsCurrentSessionWithoutGateway`, `invalidLocalNameDoesNotAllocateId`, `createReplacementCancelKeepsCurrentProject`, `createReplacementSaveCommitsOldRevisionFirst`, `createReplacementDiscardDoesNotSaveDirtyRevision`, `createLocalWriteFailureKeepsLiveProjectAndPointer`, `importAndCreateShareSupersessionGeneration`, `editDuringCreateRequiresFreshDecision`.

```kotlin
// Después de start() y drain(), con robot/puntos anónimos ya modificados:
h.coordinator.requestCreateLocal("  Mi celda  ")
h.execution.drain()
assertEquals("Mi celda", h.coordinator.state.projectName)
assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
assertNull(h.coordinator.state.origin)
// Además: snapshot sin recursos; sidecar idéntico al capturado;
// gateway no recibió llamadas; estado del robot/puntos sin cambios.
```

- [ ] **Step 2 — verificar RED:** `gradle testDebugUnitTest --tests '*ProjectPersistenceCoordinatorTest'`; falla por API ausente. Tras añadir contrato mínimo, confirmar fallo de comportamiento antes de implementar la transacción.
- [ ] **Step 3 — implementar:** normalizar nombre con trim y las reglas UTF-8/metadata existentes (límite `PersistenceLimits.maxNameBytes`, no longitud UTF-16). Validar antes de asignar ID o encolar. Proyecto inicial: recursos vacíos, sesión actual capturada en UI; reemplazo de proyecto existente: recursos vacíos, sidecar vacío que prepareRestore interpreta como sesión neutral. UUID validado, revisión 1, `origin=null`. Preparar restore, guardar slot, comprobar revisión/generación, publicar registro activo y después publicar sesión. Reutilizar rollback, autorización de reemplazo y espera de save en vuelo. No invocar gateway para crear; liberar origen anterior solo después de éxito. No modificar loadProject para disparar una creación implícita.
- [ ] **Step 4 — GREEN:** comando de Step 2 y todos los tests `*ProjectPersistence*` pasan; añadir permutaciones import→create y create→import con trabajo atrasado, fallo de publicación y cierre de coordinador. Un fallo de la segunda solicitud no habilita la primera obsoleta.
- [ ] **Step 5 — commit:** `feat: create local projects through transactional persistence`.

## Task 2: pose de simulación y captura FK completa

**Files:**
- Create: `main/kinematics/SimulationPoseTransforms.kt`.
- Create: `main/kinematics/C4PointCapture.kt`.
- Modify: `main/domain/RobotModels.kt`, `main/ui/visual/VisualLabPointController.kt`.
- Test: `test/kinematics/SimulationPoseTransformsTest.kt`, `test/kinematics/C4PointCaptureTest.kt`, `test/ui/visual/VisualLabPointControllerTest.kt`.

**Interfaces:**
- Consumes: `C4Kinematics.forward(List<Double>).baseToTcp`, `Matrix4`, `ToolDefinition.tcp`, `SharedRuntime.state`, `RuntimeCommand.SaveTeachPoint`.
- Produces: `enum class TeachPointFrame { UNSPECIFIED, SIMULATION_Z_UP }`; `TeachPoint.frame: TeachPointFrame = UNSPECIFIED` como último parámetro para conservar llamadas existentes.
- `SimulationPoseTransforms.fromPose(pose: CartesianPose): Matrix4` y `toPose(transform: Matrix4): CartesianPose`; mm, grados, rotación `Rz(rz) * Ry(ry) * Rx(rx)`.
- `C4PointCapture.capture(name: String, joints: JointState, toolTcp: CartesianPose = CartesianPose(0.0, 0.0, 0.0)): TeachPoint`.
- `VisualLabPointController.captureCurrent(name: String): VisualLabPointResult`.

- [ ] **Step 1 — RED:** `poseRoundTripPreservesMatrixAtGimbalLock`, `capturedPointMatchesFkTranslationAndOrientation`, `rotatedFlangeRotatesToolTcpOffset`, `captureStoresCopiedPreferredJointsAndFrame`, `captureRejectsUnsupportedRobotOrBlankNameWithoutMutation`.

```kotlin
val q = JointState(C4Kinematics.calibrationPoseDegrees)
val point = C4PointCapture.capture("P1", q)
assertEquals(TeachPointFrame.SIMULATION_Z_UP, point.frame)
assertEquals(q, point.preferredJointState)
assertEquals(C4Kinematics.tcpRcCandidateMm(q.values).x, point.pose.x, 1e-9)
// Comparar matriz reconstruida, no igualdad de Euler: varias ternas equivalen.
```

- [ ] **Step 2 — verificar RED:** `gradle testDebugUnitTest --tests '*SimulationPoseTransformsTest' --tests '*C4PointCaptureTest' --tests '*VisualLabPointControllerTest'`; fallo por APIs nuevas y luego aserciones de comportamiento.
- [ ] **Step 3 — implementar:** definir C como rotación CAD→Z-up `(x,y,z) → (x,-z,y)`. Captura: `C * baseToTcp * fromPose(toolTcp)`, donde toolTcp se expresa en el marco local de brida, no en mundo. `toPose` comprueba componentes finitos y transformación rígida; para gimbal lock elige rx=0 y calcula rz que conserve la matriz. Validar juntas contra definición C4, no clamping silencioso. Capturar una sola instantánea del runtime; toolTcp de la herramienta activa o identidad. Marco/juntas explícitos; no mutar robot.
- [ ] **Step 4 — GREEN:** comandos de Step 2 pasan con identidad, pose de calibración, pitch ±90°, cerca del bloqueo, offset de herramienta no nulo y entradas no finitas. Error de reconstrucción de matriz ≤1e-8; XYZ ≤1e-6 mm en fixtures.
- [ ] **Step 5 — commit:** `feat: capture simulation teach points from full C4 forward pose`.

## Task 3: conservar marco y juntas en sidecar compatible

**Files:**
- Modify: `main/project/persistence/session/ProjectSessionSnapshot.kt`, `ProjectSessionCodec.kt`, `AppProjectSessionPersistence.kt` (los tres en ese mismo directorio).
- Modify: `main/ui/visual/VisualLabPointController.kt`, `main/ui/rcplus/project/RcPointController.kt`.
- Test: `test/project/persistence/session/ProjectSessionCodecTest.kt`, `AppProjectSessionPersistenceTest.kt` (mismo directorio); `test/ui/visual/VisualLabPointControllerTest.kt`; `test/ui/rcplus/project/RcPointControllerTest.kt`.

**Interfaces:**
- Consumes: TeachPointFrame y TeachPoint de tarea 2.
- Produces: `PersistedTeachPoint.frame: String = "UNSPECIFIED"` al final de la data class; codificación de sesión esquema 2 con marcador estable 0=UNSPECIFIED, 1=SIMULATION_Z_UP después de juntas preferidas de cada punto. El codec lee esquemas 1 y 2 y escribe 2.
- El envelope `ProjectSnapshot.sidecarVersion` sigue 1: la versión interna de sesión es un contrato distinto.

- [ ] **Step 1 — RED:** fixture binario fijo de esquema1; `v1PointRemainsUnspecified`, `v2CaptureRoundTripKeepsFrameAndPreferredJoints`, `unknownFrameOrTruncatedV2RejectedBeforeApply`, `manualEditPreservesFrameAndClearsPreferredJoints`, `newManualPointRemainsUnspecified`.

```kotlin
val restored = codec.decodeOrDefault(codec.encode(snapshotWithCapturedP1))
assertEquals("SIMULATION_Z_UP", restored.teachPoints.single().frame)
assertEquals(snapshotWithCapturedP1.teachPoints, restored.teachPoints)
assertEquals("UNSPECIFIED", codec.decodeOrDefault(v1Fixture).teachPoints.single().frame)
```

- [ ] **Step 2 — verificar RED:** `gradle testDebugUnitTest --tests '*ProjectSessionCodecTest' --tests '*AppProjectSessionPersistenceTest' --tests '*VisualLabPointControllerTest' --tests '*RcPointControllerTest'`.
- [ ] **Step 3 — implementar:** conservar límites/validación actuales del codec; default UNSPECIFIED solo para versión1 o creación manual, no para marcadores corruptos de versión2. Ambos editores preservan frame del punto existente cuando lo reemplazan manualmente y limpian preferredJointState; no reinterpretar XYZ. Capture/restore traslada frame de forma explícita. No tocar `.pts` ni recursos opacos.
- [ ] **Step 4 — GREEN:** comando de Step 2; suite de persistencia pasa incluyendo recursos byte-idénticos, versión no soportada y sesión vacía legacy. Restaurar puntos no carga tareas ni inicia reloj.
- [ ] **Step 5 — commit:** `feat: persist teach point frames with backward-compatible session decoding`.

## Task 4: controles reales y prueba integrada de guardado/reapertura

**Files:**
- Modify: `main/ProjectPersistenceUi.kt`, `main/AppSessionViewModel.kt`, `main/ui/AppExperienceRoot.kt`, `main/ui/visual/VisualLabPointsPanel.kt`, `main/ui/RobotTrainerScreen.kt`.
- Test: `test/AppSessionViewModelTest.kt`.
- Create: `test/project/persistence/LocalProjectPointsAcceptanceTest.kt`.

**Interfaces:**
- Consumes: requestCreateLocal de tarea1, captureCurrent de tarea2, codec de tarea3.
- Produces: `AppSessionViewModel.createLocalProject(name: String): Unit`; `ProjectPersistenceBar` recibe `onCreateLocal: (String) -> Unit`. Reusar resolveReplacement para ambos tipos de solicitud; texto neutral «reemplazar proyecto», sin hablar exclusivamente de importar.

- [ ] **Step 1 — RED:** `createProjectIntentReachesPersistence`, `createCaptureSaveFreshSessionRestoresP1P2`, `failedSaveDoesNotReportSaved`, `cancelReplacementKeepsPointsAndOpaqueResources`. Prueba integrada con coordinador real, store temporal, AppProjectSessionPersistence real y worker/UI controlados; segundo runtime/coordinador simula reapertura, no reutilizar objetos.

```kotlin
// P1 capturado en zeroState; P2 en calibrationPoseDegrees; guardar y crear sesión fresca.
assertEquals(setOf("P1", "P2"), reopened.runtime.state.teachPoints.keys)
assertEquals(savedPoints, reopened.runtime.state.teachPoints)
assertEquals(savedJoints, reopened.runtime.state.jointState)
assertTrue(reopened.runtime.state.taskState.tasks.isEmpty())
assertFalse(reopened.runtime.state.clockState.running)
```

- [ ] **Step 2 — verificar RED:** `gradle testDebugUnitTest --tests '*AppSessionViewModelTest' --tests '*LocalProjectPointsAcceptanceTest'`.
- [ ] **Step 3 — implementar:** diálogo Crear proyecto local con nombre y Cancelar; no picker ni permisos de carpeta. Deshabilitar durante startup/error/reemplazo; doble toque no duplica solicitud. Mantener mensaje de error del coordinador. Añadir Capturar postura actual usando el campo Name del panel y mostrar marco/unidades por punto (simulación Z-up o no especificado). Captura sobre nombre existente requiere confirmación; Cancelar conserva punto. Retirar el botón SAVE P1 deshabilitado y redundante. TOUCH sigue honestamente pendiente hasta bloque2. No añadir controles ficticios.
- [ ] **Step 4 — GREEN:** comando de Step2 y `gradle testDebugUnitTest assembleDebug`; verificar punto compartido en Robot Manager y Visual Lab, y reapertura con orientación/frame/juntas intactos.
- [ ] **Step 5 — commit:** `feat: expose local project creation and current-pose teaching`.

## Task 5: aceptación Android y APK del bloque

**Files:**
- Modify: `app/build.gradle.kts` (solo dependencias de prueba y runner).
- Create: `androidTest/LocalProjectPointsUiTest.kt`.
- Create: `.github/workflows/android-local-acceptance.yml`.
- Create: `docs/superpowers/reports/2026-10-02-local-project-points-acceptance.md` (al ejecutar, registrar fecha real).

**Interfaces:**
- Consume flujo público de tarea4. No nuevos servicios productivos.
- Fijar dependencias de prueba `androidx.test:runner:1.7.0`, `androidx.test.ext:junit:1.3.0`, `androidx.test:core:1.7.0` y `androidx.compose.ui:ui-test-junit4` bajo el BOM2026.08.00 existente; `androidx.compose.ui:ui-test-manifest` solo debug. Runner: `androidx.test.runner.AndroidJUnitRunner`. Versiones de AndroidX Test verificadas en https://developer.android.com/jetpack/androidx/releases/test. No actualizar SceneView ni dependencias de producción.

- [ ] **Step 1 — prueba de aceptación:** Create→Visual Lab→capturar P1→cambiar una junta→capturar P2→Save→recreate; afirmar nombres/frame y postura conservados. Probar Cancelar creación/reemplazo y confirmar reemplazo de punto existente. Añadir test tags solo donde textos no den identidad estable. Esta tarea verifica el comportamiento ya implementado con TDD en1–4: no quitar código para fabricar un RED. Si descubre un fallo nuevo, escribir y observar su regresión antes de corregirlo.
- [ ] **Step 2 — ejecutar:** `gradle connectedDebugAndroidTest` con dispositivo de prueba autorizado. Expected: los casos instrumentados pasan sin skips; error de permisos no es prueba de comportamiento. La sesión actual tuvo fallo de acceso ADB a `\\.android`; resolver rutas/permisos de entorno con autorización específica si hace falta, sin elevar ni modificar seguridad. Alternativa CI: emulador aislado API35 x86_64, runner Ubuntu con acceso KVM documentado y permisos mínimos.
- [ ] **Step 3 — GREEN y CI:** workflow en PR para pruebas instrumentadas y artifacts (resultado XML/logcat sin datos del usuario); usar action/emulador verificado y pin de commit al implementar. Ejecutar prueba y leer reporte; el arranque fallido del emulador no cuenta como PASS. Tests unitarios + assembleDebug también pasan.
- [ ] **Step 4 — aceptación visual:** abrir APK en dispositivo/emulador, observar C4 y mover J1/J2; crear proyecto y P1/P2, guardar, detener solo la app de prueba y reabrir. Confirmar que campos/robot se conservan. No tocar proyectos o instalación del usuario sin preservar sus datos. Si no hay dispositivo accesible, marcar explícitamente pendiente y no afirmar bloque aceptado.
- [ ] **Step 5 — revisión y entrega:** revisión independiente de los cambios Android del bloque, corregir hallazgos importantes con RED→GREEN. Publicar PR separado con base verificada, sin merge automático. Recoger APK de CI del commit exacto, verificar digest/firma y entregar hash + evidencia. Actualizar ledger; continuar con planificación del bloque2, no declarar terminado el proyecto completo.

## Comandos y entorno

Todos los comandos Gradle se ejecutan en la raíz del checkout de implementación, con JDK17, Gradle9.6.0 y Android SDK37. El Gradle9.5.0 encontrado localmente no se asume equivalente. No instalar SDK/toolchains ni cambiar ubicaciones fuera del workspace sin permiso requerido. CI existente tiene toolchain fijado y sirve como verificación cuando la ejecución local está bloqueada. RED debe observarse en el entorno disponible antes del cambio, no inferirse del código.

## Self-review del plan

- Cobertura: tareas1/4/5 cubren creación y reemplazo; tareas2/3/4 cubren postura completa, frame y persistencia; tarea5 cubre recorrido visual. IK/fantasma/pinza/secuencia pertenecen explícitamente a los siguientes planes.
- Dependencias: 1→4, 2→3→4, 4→5. No se trabaja en paralelo en coordinator/modelos compartidos.
- Riesgos de Review Focus tienen casos en tareas1–4; entrada UI repetida y recreación se verifican en5.
- No hay estimación de finalización ni afirmación de tests nuevos ejecutados. Esto es un plan pendiente de revisión; el diseño sí está aprobado.

