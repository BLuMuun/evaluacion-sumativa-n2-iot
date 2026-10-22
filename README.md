# Comedero IoT — Evaluación Sumativa N.º 2

Aplicación Android y firmware ESP32 para monitorear rasguños de un gato y controlar un comedero IoT. La aplicación requiere Login; el Nodo Android simula el dispositivo y el Panel permite monitorear y dispensar. Para la demostración con hardware, el ESP32 del comedero funciona como gateway TCP del Panel y mantiene MQTT/TLS con el ESP32 de la cama.

> **Estado de verificación:** la app compila y pasan 12 pruebas unitarias. Android Lint termina sin errores y reporta avisos de actualización de SDK/dependencias. P2 y P3 constan aprobadas en el registro anterior; en HONOR y Moto G35 están aprobadas P5, P6, P7, P10 y P11. P4 confirma el bloqueo seguro para un `OBSERVADOR`, y P13 verifica que la app sigue funcionando con notificaciones denegadas. P9 es parcial: la consulta a Firestore funciona, pero no hay lecturas guardadas porque las cuentas de prueba son `OBSERVADOR` y las reglas reservan la escritura a `operador`. P12 y parte de P15 tienen cobertura automatizada; sus pruebas físicas de repetición y envío directo siguen pendientes. P1, P8, P14–P15 y las pruebas PL1–PL7 con placas siguen pendientes. Una compilación no equivale a una prueba física.

## Problemática

En el hogar, un gato rasguña la cama cuando quiere alimento, provocando agujeros y deterioro. No hay una forma automática de interpretar ese comportamiento y gestionar una solicitud. El proyecto representa la detección de rasguños y el control del comedero con dispositivos Android y ESP32.

## Solución

Se instala la aplicación en dos teléfonos. En modo **Teléfonos**, uno funciona como Nodo y el otro como Panel, conectados por Wi-Fi local/TCP. En modo **ESP32**, el Panel se conecta al gateway TCP del ESP32 comedero; la cama publica lecturas al comedero mediante MQTT sobre TLS. El usuario operador decide cuándo alimentar, y la entrega se confirma después de que el servo vuelve a cerrar la compuerta.

## Relación con Unidad 1

En Unidad 1 el flujo era:

```text
Sensor/botón → ESP32 cama → Wi-Fi/MQTT TLS → ESP32 comedero → servo
```

La app amplía el flujo para monitoreo y decisión manual:

```text
ESP32 cama ←→ MQTT/TLS + HMAC ←→ ESP32 comedero/gateway ←→ Wi-Fi TCP/5050 ←→ Panel Android
```

Para pruebas de app sin placas, dos teléfonos sustituyen temporalmente los ESP32. En Android–Android el canal TCP cifra las tramas con AES-GCM.

## Herramientas y decisiones técnicas

| Herramienta o decisión | Uso y justificación |
|---|---|
| Android Studio | IDE para compilar, ejecutar en emulador o teléfono y revisar el proyecto Android. |
| Kotlin | Lenguaje de la app; permite expresar la sesión y el protocolo de forma clara. |
| Jetpack Compose + Material 3 | Pantallas Login, Registro, Conexión, Nodo, Panel e Historial con interfaz declarativa. |
| Gradle Kotlin DSL | Configuración Gradle del proyecto con Kotlin DSL. |
| Wi-Fi local y TCP/5050 | Adecuado para una red local/hotspot entre habitaciones y mensajes IoT pequeños. El Nodo Android escucha; Panel Android o ESP32 gateway se conecta. |
| Firebase Authentication | Identifica a las cuentas con correo y contraseña sin guardar contraseñas en la app. |
| Cloud Firestore | Almacena perfiles y lecturas válidas para mostrar un historial persistente. |
| MQTT sobre TLS + HMAC en ESP32 | Conserva el enlace de Unidad 1 entre la cama y el comedero, autentica mensajes y mantiene confidencialidad de transporte al broker. |

## Funcionalidades

- **F1 — Login y cuentas:** Firebase Authentication Email/Password; registro con contraseña mínima de 8 caracteres.
- **F2 — Roles:** nuevas cuentas son `observador`; un administrador asigna `operador` en Firebase Console. Observador monitorea/consulta; solo Operador puede pedir dispensación desde la app.
- **F3 — Monitoreo:** el Panel muestra conexión, 0–3 rasguños y estado del comedero.
- **F4 — Solicitud y alerta:** tres rasguños dentro de una ventana fija de 5 segundos mantienen 3/3 pendiente y notifican una vez al Panel.
- **F5 — Control manual y ACK:** Operador envía `DISPENSAR`; el actuador simula o ejecuta apertura de 1,5 s, cierra y confirma con ACK.
- **F6 — Historial:** el Panel con rol `operador` guarda lecturas válidas en Firestore; los usuarios con perfil pueden consultar las últimas 50.
- **F7 — Enlaces:** Android–Android cifrado con AES-GCM; Android Panel–ESP32 gateway disponible en modo de prueba T3 local.
- **F8 — Estado seguro:** pérdida de enlace, timeout o reinicio cancela una entrega activa y pone el comedero en `CERRADO`.

La ventana es fija: inicia en el primer rasguño y no se desplaza. Si vence sin llegar a 3, el contador vuelve a cero. Al llegar a 3 se ignoran rasguños adicionales hasta confirmación o cancelación segura.

## Protocolo

El contenido lógico permitido tiene exactamente tres campos:

```text
TIPO;CLAVE;VALOR
```

Ejemplos:

```text
LECTURA;rasgunos;2
LECTURA;comedero;DISPENSANDO
CMD;comida;DISPENSAR
ACK;comida;DISPENSADA
ACK;comida;CANCELADA
ACK;enlace;LATIDO
```

Mensajes con campo, tipo, clave o valor inválido se descartan. Entre teléfonos, el Nodo negocia una sal aleatoria de 16 bytes y ambos derivan una clave AES-256 con PBKDF2-HMAC-SHA256. Cada trama incluye secuencia creciente autenticada por GCM, IV aleatorio y ciphertext Base64; tramas alteradas, con secuencia repetida o con otro código no se procesan.

En MQTT de los ESP32, los mensajes firmados usan el sobre `R2|secuencia|idSolicitud|TIPO;CLAVE;VALOR|HMAC-SHA-256`. El Comedero verifica HMAC y secuencia antes de pasar una lectura o ACK. La Cama conserva el ID y el estado 3/3 hasta `DISPENSADA` o `CANCELADA`.

En el modo de laboratorio App–ESP32 se usa `nonce;secuencia;TIPO;CLAVE;VALOR`. Ese canal TCP **no está cifrado ni tiene HMAC**; el nonce y la secuencia solo acotan cada sesión y ayudan a rechazar repetición exacta. Debe limitarse a una red controlada. No se presenta como canal seguro de producción.

## Mockup, análisis y plan

- [Diseño Sumativa y mockup](docs/DISENO_SUMATIVA_PARA_REVISION.md)
- [Análisis de los proyectos Wokwi recibidos](docs/ANALISIS_WOKWI.md)
- [Plan MVP por hitos](PLAN_MVP.md)
- [Configuración de Firebase](docs/FIREBASE_SETUP.md)
- [Pruebas P1–P15 y PL1–PL7](docs/PRUEBAS_SUMATIVA.md)
- [Instalación y ejecución](docs/INSTALACION.md)
- [Diseño y mockup de las pantallas](docs/DISENO_SUMATIVA_PARA_REVISION.md)
- [Análisis de los sketches y diagramas Wokwi](docs/ANALISIS_WOKWI.md)

## Firebase

Consulta [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md). Copia el archivo de configuración de Firebase a `app/google-services.json`, habilita Email/Password, crea Firestore y publica [`firestore.rules`](firestore.rules). La configuración JSON, credenciales y archivos locales `secrets.h` se excluyen de Git.

## Seguridad y lineamientos ISO/OT

La app usa AES-256-GCM y sal aleatoria por sesión en modo Android–Android, contador de secuencia anti-repetición, Firebase Authentication, reglas de rol en Firestore, validación estricta de protocolo, `allowBackup=false`, `usesCleartextTraffic=false`, permisos mínimos y estado seguro `CERRADO`. Antes de conectar, el Panel entrega su token Firebase dentro del canal cifrado; el Nodo consulta solo el campo `rol` del perfil autenticado mediante Firestore REST y rechaza los comandos si el rol no es `operador` o no se pudo verificar. Así, el control de rol no depende solo de que el botón esté deshabilitado en la interfaz. Los ESP32 conservan TLS/HMAC en MQTT y verifican secuencias persistentes.

La matriz de [`docs/SEGURIDAD_ISO_OT.md`](docs/SEGURIDAD_ISO_OT.md) enlaza cada medida con las áreas de control ISO/IEC 27400:2022 descritas en el Hito 7 de la guía, controles complementarios de ISO/IEC 27002:2022 y requisitos fundamentales IAC, UC, SI, DC, RDF, TRE y RA de IEC 62443-3-3. La guía no asigna números de cláusula a las medidas del proyecto y no se inventan. Son referencias de diseño, no certificación ni verificación formal de cumplimiento. La excepción de prueba App–ESP32 sin cifrar se documenta en esa matriz.

## Pruebas

Las 12 pruebas unitarias cubren lógica de ventana, validación de mensajes, AES-GCM, secuencias y autorización del rol en el Nodo. Android Lint no informa errores; sus avisos recomiendan actualizar el target SDK y algunas dependencias. Los resultados por caso y las capturas completas de teléfono están en [`docs/PRUEBAS_SUMATIVA.md`](docs/PRUEBAS_SUMATIVA.md). La prueba positiva de dispensación con Operador, el historial con lecturas persistidas y las pruebas físicas de placas aún están pendientes.

## Limitaciones conocidas

- Se deben usar credenciales nuevas de HiveMQ y una clave HMAC aleatoria en los `secrets.h` locales; las credenciales recibidas anteriormente no deben reutilizarse.
- La cuenta Firebase debe tener perfil Firestore y el rol operador asignado manualmente para habilitar el control y guardar lecturas. En esta verificación los dos teléfonos usaron cuentas `OBSERVADOR`; por eso se probó el bloqueo seguro, pero el historial quedó vacío y no se completaron la entrega positiva ni la persistencia de lecturas.
- El Nodo necesita acceso a Internet para verificar en Firestore el token y rol del Panel al iniciar una sesión Android–Android; si la verificación falla, no acepta órdenes. Los observadores pueden monitorear una vez autenticada la sesión.
- El gateway App–ESP32 es una modalidad de prueba sin cifrado ni autenticación criptográfica; no usar en Internet/redes públicas.
- MQTT en `PubSubClient` usa QoS 0; el sketch repite el ACK hasta que la Cama confirma con lectura 0, pero la entrega MQTT no sustituye una prueba de reconexión real.
- Los casos marcados como aprobados tienen ejecución y evidencia en [`docs/PRUEBAS_SUMATIVA.md`](docs/PRUEBAS_SUMATIVA.md); los casos restantes siguen pendientes y no se presentan como completados.

## Uso de IA

**Herramienta:** OpenAI Codex / ChatGPT.

**Uso:** apoyo para planificación, estructura del proyecto, generación inicial de código, revisión técnica y documentación. El estudiante debe revisar, compilar, probar y corregir el código y es responsable de poder explicarlo al profesor. Las pruebas pendientes deben ejecutarse manualmente antes de declarar resultados.

## Entrega

El archivo [`entrega_aai.txt`](entrega_aai.txt) contiene el enlace del repositorio para copiarlo a AAI.
