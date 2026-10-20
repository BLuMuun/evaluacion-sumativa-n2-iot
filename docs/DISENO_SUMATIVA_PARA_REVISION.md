# Diseño Sumativa Unidad 2

**Estado:** el estudiante informa que el profesor revisó y aprobó el diseño Sumativo. La aprobación se registra según esa confirmación; no se adjuntó una captura o acta formal.

## Alcance Sumativo

Se conserva la base Android Formativa y se agregan:

- Registro y Login con Firebase Authentication por correo y contraseña.
- Roles `operador` y `observador`; las cuentas nuevas quedan como observador. Un administrador asigna el rol operador desde Firebase Console. Un observador puede consultar, pero no ordenar dispensaciones.
- Persistencia de lecturas válidas en Cloud Firestore y pantalla de historial.
- Mejoras sugeridas: latido en ambos sentidos, número de secuencia para rechazar órdenes repetidas, sal aleatoria de sesión y relación explícita de medidas con controles ISO/OT.
- Prueba de integración con los ESP32 de Unidad 1 según la fase aprobada y la guía de la asignatura.

## Mockup de pantallas

### Login

```text
┌─────────────────────────────┐
│          Comedero IoT       │
│                             │
│ Correo                      │
│ [_______________________]   │
│ Contraseña                  │
│ [_______________________]   │
│                             │
│ [ Iniciar sesión ]          │
│ Crear cuenta                │
└─────────────────────────────┘
```

La pantalla de registro solicita correo, contraseña y confirmación. Los errores de autenticación se muestran en pantalla; no se guardan contraseñas en Firestore ni en archivos locales.

### Conexión

```text
┌─────────────────────────────┐
│        Conexión IoT         │
│ Modo: [ Teléfonos | ESP32 ] │
│ Rol:  [ Nodo | Panel ]      │
│ Código: [______]            │
│ IP del Nodo/ESP32: [_____]  │
│ Estado: DESCONECTADO        │
│ [ Conectar / Iniciar ]      │
└─────────────────────────────┘
```

La opción `ESP32` implementa el diseño de integración revisado y aprobado por el docente según confirmación del estudiante. En la prueba de placas, el Panel se conecta por TCP al gateway del comedero; MQTT/TLS y HMAC protegen el enlace entre las placas. El enlace de prueba App–ESP32 no cifra ni autentica criptográficamente las tramas y se limita a una red local controlada; no se presenta como seguridad para producción. El enlace entre teléfonos mantiene AES-GCM.

### Nodo Android

```text
┌─────────────────────────────┐
│ Nodo IoT     Enlace: ACTIVO │
│ Rasguños: 2 / 3             │
│ [ Simular rasguño ]         │
│ Comedero: CERRADO           │
│ [ Desconectar ]             │
└─────────────────────────────┘
```

Se conserva la ventana fija de cinco segundos y el estado 3/3 pendiente hasta que se complete una entrega confirmada. La pérdida del enlace cancela la operación y deja `CERRADO`.

### Panel de control

```text
┌─────────────────────────────┐
│ Panel         Enlace: ACTIVO│
│ Rol: OPERADOR               │
│ Rasguños: 3 / 3             │
│ Comedero: CERRADO           │
│ [ Dispensar comida ]        │
│ Última entrega: confirmada  │
│ [ Historial ] [ Salir ]     │
└─────────────────────────────┘
```

Para `OBSERVADOR`, las lecturas y el historial se pueden consultar, pero `Dispensar comida` aparece deshabilitado. El permiso también se aplica en las reglas de Firestore, no solo en la interfaz.

### Historial

```text
┌─────────────────────────────┐
│ Historial de lecturas       │
│ 20/10 15:30  rasgunos  2    │
│ 20/10 15:30  comedero CERRADO│
│ 20/10 15:29  rasgunos  1    │
│ ... últimas 50 lecturas      │
│ [ Volver al Panel ]         │
└─────────────────────────────┘
```

## Flujo de comunicación que se propone validar

```text
Modo demostración móvil:
Teléfono Nodo  ← Wi-Fi TCP/5050 + AES-GCM →  Teléfono Panel

Modo de prueba con hardware:
ESP32 Cama  ← Wi-Fi/MQTT TLS + HMAC →  ESP32 Comedero/Gateway
                                              ↕ Wi-Fi TCP/5050
                                          Teléfono Panel
```

En la prueba con hardware, el Panel puede decidir la dispensación y el gateway envía la confirmación después de cerrar el servo. Para preservar el estado pendiente definido en este proyecto, la cama también recibe el ACK por MQTT. Ambos sketches se adaptaron y las ACL requeridas están descritas en `docs/ANALISIS_WOKWI.md`; las pruebas con las placas físicas siguen pendientes.

## Decisiones revisadas con el docente

1. Se conserva la integración de dos ESP32 + MQTT/HiveMQ + gateway TCP/5050, con opción Teléfonos/ESP32 en la app.
2. Ambos sketches publican lecturas, dejan la solicitud pendiente y procesan ACK; MQTT conserva TLS/HMAC y anti-repetición.
3. El enlace de prueba app–ESP32 es TCP local sin cifrar; Android–Android conserva AES-GCM. La limitación se documenta en README y matriz ISO/OT.
4. Cloud Firestore usa perfiles/roles y lecturas; las cuentas nuevas son `observador`, y el administrador asigna `operador` desde Firebase Console.

La aprobación docente fue comunicada por el estudiante y se usa como base de implementación. Las pruebas conectadas de Firebase, las pruebas en dos teléfonos y las pruebas físicas de hardware conservan su estado hasta contar con evidencia.
