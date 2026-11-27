# Pinza vertical en XYZ

Confirmado por el usuario: punta hacia abajo, orientación completa constante mientras el brazo compensa. CAD usa Y arriba; el eje de aproximación de la pinza original es +Z local. Por tanto R0 * Z = -Y en CAD.

1. Probar alineación con TCP estacionario y conservación de R0 en X/Y/Z, incluidos puntos intermedios.
2. Añadir DownwardToolOrientation: rotación mínima del eje de aproximación hacia -Y y resolver pequeños pasos de orientación con el IK existente. Mantener rumbo con esa rotación completa, no asignar J5=90.
3. Al entrar en XYZ sin pieza, precalcular y certificar toda la alineación antes de aplicarla. Rechazar si hay choque o no existe solución; no mover parcialmente. Con pieza inclinada, no reorientarla automáticamente.
4. XYZ siempre conserva orientación vertical; quitar el checkbox que permitía desactivarla. Conservar el planificador cartesiano y su certificación progresiva.
5. Validar pruebas, compilar APK; no declarar instalación sin ADB y confirmación de instalación.

Se conservan los cambios pendientes de etiquetas reales y fusibles manuales. Sus fallos deben resolverse antes de entregar el APK combinado.
