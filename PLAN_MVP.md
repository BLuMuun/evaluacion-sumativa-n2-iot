# PLAN_MVP — Comedero IoT · Evaluación Sumativa Unidad 2

**Estado:** diseño Sumativo aprobado por el docente según confirmación del estudiante. La app Android y ambos sketches ESP32 compilan; las pruebas unitarias Android pasan. La configuración conectada de Firebase y las pruebas físicas con teléfonos y placas permanecen pendientes.

## 1. Resumen y alcance

El proyecto representa el problema de la Unidad 1: un gato rasguña la cama cuando quiere alimento y una compuerta libera una porción. Se conserva la app Android de la Formativa (Nodo y Panel) y se agrega Login, roles, persistencia e historial. El estudiante entregó los dos proyectos Wokwi de Unidad 1. El estudiante informa que el profesor aprobó conservar Wi-Fi/MQTT entre placas y hacer del ESP32 comedero un gateway TCP para el Panel; ambos sketches se adaptan para esperar la decisión manual y procesar ACK.

La app seguirá usando dos teléfonos para la demostración Android a Android. Firebase Authentication y Cloud Firestore son decisiones del estudiante para esta Sumativa; esta selección explícita prevalece sobre las alternativas de ejemplo de la guía. No se implementan servicios de datos diferentes.

La aprobación de la Formativa y sus resultados P1–P7 se conservan en el repositorio Formativa y se referencian en `docs/PRUEBAS_FORMATIVA.md`. Como el protocolo y la seguridad cambiaron, P1–P7 se repiten con el build Sumativo antes de declarar la entrega lista.

## 2. MoSCoW y requisitos Must

Todos los requisitos de evaluación se tratan como Must. Las mejoras expresamente sugeridas por el profesor se incorporan a las fases de seguridad.

| ID | Must del MVP Sumativo | Criterio |
|---|---|---|
| M1 | App Android en Kotlin/Android Studio instalada y operativa en dos teléfonos. | 2.1.1, 2.1.4 |
| M2 | Problemática, solución, funcionalidades y herramientas justificadas y revisadas. | 2.1.1 |
| M3 | Mockup con pantallas, navegación y control del dispositivo. | 2.1.1 |
| M4 | Wi-Fi local TCP justificado y operativo entre los teléfonos. | 2.1.2 |
| M5 | El Nodo publica rasguños y el Panel muestra lecturas en vivo. | 2.1.2, 2.1.4 |
| M6 | El Panel solicita dispensación y recibe confirmación; el Nodo acciona la compuerta. | 2.1.2, 2.1.4 |
| M7 | Alerta de solicitud en el Panel y notificación de comando recibido en el Nodo. | 2.1.4 |
| M8 | Login y creación de cuenta con Firebase Authentication; roles operador/observador. | 2.1.2, 2.1.3 |
| M9 | Guardar lecturas en Cloud Firestore y consultar historial persistente. | 2.1.2 |
| M10 | Cifrado, validación, control de acceso, permisos mínimos y conexión segura documentados. | 2.1.3 |
| M11 | Pérdida de enlace cierra la compuerta y cancela la dispensación. | 2.1.3 |
| M12 | Pruebas Sumativas en dos teléfonos y pruebas PL1–PL7 con los ESP32 de Unidad 1. | 2.1.2, 2.1.4 |
| M13 | GitHub con código, README, plan y declaración honesta de uso de IA; link preparado para AAI. | Entrega |
| M14 | Demostración en clase de login, conexión, lectura, control, historial y seguridad. | 2.1.4 |

No se agregan requisitos Should/Could hasta terminar todos los Must y confirmar que la pauta oficial del AAI no los incorpora.

## 3. Declaración técnica

| Decisión | Valor declarado | Justificación |
|---|---|---|
| Lenguaje y proyecto | Kotlin, Android Studio, Gradle Kotlin DSL, `minSdk 26`, package `cl.inacap.comederoiot`. | Reutiliza el proyecto individual Formativo y el estándar indicado por la asignatura. |
| Interfaz | Jetpack Compose y Material 3; Login, Conexión, Nodo, Panel e Historial. | Mantiene las pantallas actuales y agrega el flujo Sumativo requerido. |
| Enlace entre teléfonos | Wi-Fi local TCP, puerto 5050; Nodo servidor, Panel cliente. | Adecuado para habitaciones distintas y mensajes pequeños. P1–P7 de la Formativa se registraron en el repositorio Formativo; deben repetirse con esta versión Sumativa. |
| Enlace con hardware | Diseño aprobado según confirmación del estudiante: conservar MQTT/HiveMQ cama–comedero y añadir TCP 5050 entre el Panel Android y el ESP32 del comedero. | Los archivos Wokwi recibidos se analizaron en `docs/ANALISIS_WOKWI.md`; ambos sketches se adaptan para enviar lecturas, mantener solicitudes pendientes y procesar el ACK. |
| Protocolo de aplicación | El mensaje descifrado conserva exactamente `TIPO;CLAVE;VALOR`. Mensajes del comedero: `LECTURA;rasgunos;X`, `LECTURA;comedero;CERRADO|DISPENSANDO`, `CMD;comida;DISPENSAR`, `ACK;comida;DISPENSADA`. Se suma `ACK;enlace;LATIDO` para latidos en ambos sentidos. | Conserva las tres partes de la Formativa y agrega el mensaje de disponibilidad solicitado por el profesor. |
| Anti-repetición y clave de sesión | Contador monotónico por dirección en el sobre de transporte; aceptar solo secuencias nuevas. En teléfonos, sal aleatoria de sesión negociada al conectar y usada en PBKDF2-HMAC-SHA256 antes de AES-256-GCM. | Descarta órdenes repetidas y evita reutilizar la clave derivada con una sal fija. El código de vinculación de 6 dígitos se conserva; se documenta que sigue teniendo entropía limitada. |
| Login y roles | Firebase Authentication, correo/contraseña. Cada cuenta nueva recibe rol `observador`; un administrador asigna `operador` en Firestore Console al usuario autorizado. Solo operador envía órdenes. | Evita que una persona se otorgue privilegios desde la app. Firebase administra las credenciales; la app no guarda contraseñas. |
| Persistencia | Cloud Firestore, colección `lecturas`; documentos con `momento` (server timestamp), `clave`, `valor`, `origen` y `sesionId`. El Panel guarda las lecturas válidas recibidas y consulta las últimas 50 por fecha descendente. | Cumple la decisión explícita de usar Firestore y deja un historial verificable tras cerrar la app. |
| Reglas Firestore | `firestore.rules`: usuarios autenticados con perfil pueden leer historial compartido; escritura de lecturas solo por operador; cada perfil solo se consulta a sí mismo y el rol no puede cambiarse desde el cliente. El administrador asigna el operador desde Firebase Console. | Control de acceso y mínimo privilegio; las reglas se publican manualmente en Firebase antes de probar la app. |
| Notificaciones | NotificationCompat locales; solicitud de comida en el Panel y comando recibido en el Nodo. | Reutiliza el diseño Formativo; no requiere FCM. |
| Enlace con placa | Modo de prueba sin cifrado ni autenticación criptográfica para la sesión app–ESP32; Android a Android conserva AES-GCM, y MQTT entre placas conserva TLS/HMAC. | Modo restringido a red de laboratorio controlada; no se presenta como seguridad de producción. |
| Seguridad complementaria | Latido en ambas direcciones; cierre tras 6 s sin latido válido; rechazo de secuencias repetidas; estado `CERRADO`; permisos mínimos; `allowBackup=false`; `usesCleartextTraffic=false`. | Cubre las mejoras recomendadas por el profesor y la guía de seguridad IoT/OT. El modo debug de placa es una excepción de prueba documentada. |

La definición de lectura es cada valor de `LECTURA;rasgunos;X` recibido, incluido el latido de rasguños publicado cada 2 segundos. Una solicitud completa permanece en 3/3 hasta dispensación correcta o pérdida del enlace. La regla es de ventana fija de 5 segundos, definida por el estudiante y conservada sin cambios.

## 4. Arquitectura prevista

- `MainActivity.kt`: navegación entre Login, Conexión, Nodo, Panel e Historial.
- `seguridad/AutenticacionFirebase.kt`: inicio, registro, cierre de sesión y errores de Firebase Authentication.
- `datos/RepositorioUsuarios.kt`: lectura y creación de perfiles Firestore; rol por defecto observador.
- `datos/RepositorioLecturas.kt`: persistencia y consulta del historial en Firestore.
- `firestore.rules`: acceso autenticado, escritura de lecturas solo por operador y protección de roles.
- `ui/PantallaLogin.kt`: login, registro, validación de contraseña y bloqueo local por intentos fallidos.
- `ui/PantallaHistorial.kt`: últimas 50 lecturas, con fecha y valor.
- `Sesion.kt`, `PantallaPanel.kt`: cargar rol, permitir/denegar comandos y guardar lecturas recibidas.
- `enlace/Mensaje.kt`, `Enlace.kt`, `WifiEnlace.kt`, `seguridad/Cripto.kt`: latidos bidireccionales, sobre con secuencia, sal aleatoria por sesión y protección AES-GCM entre teléfonos.
- Firmware: los sketches de Cama y Comedero están adaptados para lecturas 0–3, espera de decisión del Panel, ACK y estado seguro. Ambos compilan para ESP32 DevKit V1. MQTT/TLS/HMAC se conserva en el canal cama–comedero; TCP/5050 conecta Panel y gateway.
- Documentación: el repositorio incluye `README.md`, análisis Wokwi, diseño/mockup, instrucciones de Firebase e instalación, matriz ISO/OT y las tablas P1–P14/PL1–PL7. Los campos de resultados y evidencia siguen vacíos hasta ejecutar pruebas conectadas y físicas.

El archivo `app/google-services.json` seguirá siendo local e ignorado por Git. El repositorio incluirá instrucciones para configurarlo sin publicar credenciales o contraseñas.

## 5. Fases de trabajo

Las tareas de implementación pueden marcarse al estar codificadas; las validaciones de cuenta real, dos teléfonos y placas requieren evidencia propia. No se declaran pruebas físicas a partir de inspección estática o compilación.

### Fase 0 — Diseño Sumativo y aprobación (M2, M3, M8)

- [x] El estudiante confirma Firebase Authentication + Cloud Firestore, el esquema previsto de lecturas, la política de roles y la propuesta de integración ESP32 documentada aquí.
- [x] El estudiante confirma que el docente revisó y aprobó el diseño Sumativo de Login/autenticación, roles, Firestore, mockup e integración hardware. No se adjuntó registro adicional del comentario.
- [x] Registrar la aprobación docente Sumativa confirmada por el estudiante; no se confunde con la revisión de la Formativa.
- [x] Recibir y hacer revisión estática de los dos sketches `.ino`, diagramas Wokwi, librerías y artefactos incluidos.
- [x] El estudiante y el docente aprueban el flujo propuesto de integración de placas y dispensación manual.

**Criterio de salida:** plan aprobado por el estudiante; diseño Sumativo (incluido el enlace con ESP32) revisado por el docente; firmware original explicado y el comportamiento objetivo acordado. La revisión aprobada de la Formativa no se presenta como aprobación de Sumativa.

### Fase 1 — POC-1 de conexión segura actualizada (M1, M4, M5, M6; 2.1.2, 2.1.4)

- [x] Implementar el sobre de secuencia y rechazo de repeticiones en ambos sentidos; la secuencia se autentica dentro del contenido cifrado Android–Android.
- [x] Negociar una sal aleatoria nueva por sesión entre dos teléfonos y derivar claves AES-GCM desde el código de vinculación.
- [x] Añadir `ACK;enlace;LATIDO` desde Nodo y Panel; declarar pérdida si faltan 6 s y cerrar el actuador en estado seguro.
- [ ] Repetir P1, P2, P3, P4, P5 y P6 con dos teléfonos y el protocolo actualizado; registrar tiempos, resultado y capturas nuevas.
- [x] Añadir pruebas unitarias para el rechazo de secuencias repetidas y retrasadas. La prueba de doble dispensación con dos teléfonos sigue pendiente.

**Criterio de salida:** dos teléfonos se conectan con la nueva sesión; ambos lados detectan pérdida; una trama repetida no vuelve a ejecutar la entrega; P1–P6 pasan en el teléfono.

### Fase 2 — Hito 5: registro, Login y control por roles (M8, M10; 2.1.2, 2.1.3)

- [x] Añadir creación de cuenta por Firebase Authentication Email/Password.
- [x] Rechazar registro con contraseña menor a 8 caracteres; no guardar ni registrar la contraseña en texto plano.
- [x] Crear perfiles Firestore con rol `observador`; documentar que el administrador asigna el rol `operador` desde Firebase Console.
- [x] Mostrar el rol autenticado y permitir `DISPENSAR` solo al operador; el observador puede monitorear, pero no controlar.
- [x] Tras 5 fallos consecutivos, bloquear localmente el siguiente intento durante 30 s y mostrar el tiempo restante.
- [x] Añadir y documentar reglas Firestore que impiden que el cliente se autoasigne el rol operador.
- [ ] Ejecutar P8–P11 y registrar capturas sin credenciales.

**Criterio de salida:** operador y observador ingresan, los permisos por rol se respetan, la contraseña corta se rechaza y el bloqueo de 30 s se verifica.

### Fase 3 — Hito 6: historial persistente (M9; 2.1.2)

- [x] Guardar cada lectura válida recibida en `lecturas` con timestamp de servidor, clave, valor, origen y sesión.
- [x] Añadir pantalla Historial con las últimas 50 lecturas en orden descendente.
- [x] Mostrar estados de carga, lista vacía y error de red sin cerrar la app.
- [ ] Verificar que los documentos sobreviven al cierre/reapertura y que las reglas deniegan escrituras de observadores.
- [ ] Ejecutar P12–P14; registrar evidencia de Firebase Console sin exponer credenciales.

**Criterio de salida:** 1 minuto de Nodo conectado produce aproximadamente 30 registros de rasguños; el Historial los consulta después de reiniciar la app; solo el operador puede escribir.

### Fase 4 — Hito 7: seguridad IoT/OT completa (M10, M11; 2.1.3)

- [x] Incorporar el latido bidireccional y vencimiento seguro en la pantalla y sesión de conexión.
- [x] Validar secuencia creciente por dirección y descartar repetición, retroceso, trama inválida o mensaje fuera del rol.
- [x] Sustituir la sal fija de derivación por sal aleatoria por sesión; añadir pruebas unitarias de derivación y cifrado.
- [x] Implementar AES-256-GCM e IV aleatorio por mensaje entre teléfonos; rechazar datos alterados o de otra clave.
- [x] Añadir roles y reglas Firestore; `.gitignore` excluye JSON de Firebase, contraseñas, tokens y secretos locales.
- [x] Actualizar matriz de seguridad: medida, riesgo, archivo/regla, control ISO/IEC 27002:2022 (referencia ISO/IEC 27001 Anexo A) y requisito fundamental OT relacionado.
- [ ] Ejecutar P5, P6, P9, P10, P11 y P14 después de los cambios; registrar evidencia.

**Criterio de salida:** cada medida tiene prueba y ubicación de código documentadas; pérdida de enlace deja CERRADO; el informe aclara que no se afirma certificación ISO ni seguridad de producción.

Matriz de trazabilidad del diseño y el código implementado:

| Medida | Lineamiento ISO/IEC 27400 según Hito 7 | Control ISO/IEC 27002:2022 complementario | Requisito OT IEC 62443-3-3 |
|---|---|---|---|
| Firebase Authentication y manejo de credenciales | Autenticación e información de autenticación | 5.16 Identity management; 5.17 Authentication information; 8.5 Secure authentication | FR1 IAC: Identification and authentication control |
| Rol Firestore operador/observador y reglas por rol | Control de acceso y mínimo privilegio | 5.15 Access control; 5.18 Access rights | FR2 UC: Use control |
| AES-GCM, secuencia autenticada y validación | Protección de datos en tránsito; integridad | 8.24 Use of cryptography; 8.20 Networks security | FR3 SI: System integrity; FR4 DC: Data confidentiality |
| TCP/5050 local y restricción de servicios Firestore | Conexiones seguras y control de exposición | 8.20 Networks security; 8.21 Security of network services | FR5 RDF: Restricted data flow, parcial |
| Permisos mínimos y respaldo Android deshabilitado | Permisos necesarios y protección de datos | 5.15 Access control | FR2 UC: Use control |
| Latidos, timeout y aviso de pérdida | Resiliencia y respuesta ante fallas | 8.16 Monitoring activities | FR6 TRE: Timely response to events |
| Servo CERRADO ante falla o desconexión | Estado seguro ante pérdida del enlace | 8.16 Monitoring activities | FR7 RA: Resource availability, relacionado con recuperación segura |

Estas referencias sirven para explicar el diseño; no implican certificación ni auditoría formal de conformidad. El canal App–ESP32 sin cifrar queda fuera de la afirmación de confidencialidad/integridad criptográfica.

### Fase 5 — Pruebas con placas ESP32 de Unidad 1 (M12; 2.1.2, 2.1.4)

- [ ] Confirmar en hardware las placas con firmware original cama → MQTT/HiveMQ → comedero → servo; guardar video de línea base si se requiere.
- [x] Adaptar ambos sketches para lecturas 0–3, solicitud pendiente y ACK; el ESP32 Comedero expone gateway TCP/5050 para el Panel y MQTT entre placas se conserva.
- [x] Implementar secuencia persistente por mensaje, verificación HMAC para MQTT, y validación de comandos del gateway; el canal App–ESP32 sin cifrar queda limitado a modo de laboratorio.
- [x] Implementar cierre del servo y cancelación segura al perder Wi-Fi/Panel, al expirar el latido o durante reinicio/error del firmware.
- [x] Compilar ambos sketches con Arduino CLI para ESP32 DevKit V1. La compilación limpia reportó solo avisos no críticos en ESP32Servo; no se ha cargado a la placa.
- [ ] Ejecutar PL1–PL7: lectura física, control/ACK, notificación, estado seguro, mensaje inválido, latencia menor a 2 s y regresión MQTT/HiveMQ.
- [ ] Guardar video/capturas de resultados y anotar el hardware, versiones y latencia observada.

**Criterio de salida:** PL1–PL7 pasan con las placas físicas; si una prueba falla, corregir y repetir; no marcar pruebas simuladas con teléfono como prueba de placa.

### Fase 6 — Hito 8: documentación y demostración (M13, M14; 2.1.4)

- [ ] Repetir P1–P14 con el build final y completar Resultado real/Evidencia en `docs/PRUEBAS_SUMATIVA.md`.
- [x] Completar README con problemática, elecciones y justificaciones, mockup, esquema Firestore, setup, seguridad, matriz ISO/OT, pruebas y uso de IA.
- [x] Verificar que los archivos locales `google-services.json`, `local.properties` y `secrets.h` estén ignorados por Git; revisar el contenido que se preparará para publicar.
- [x] Compilar APK debug y ejecutar las pruebas unitarias locales. Instalarlo y demostrarlo en los dos teléfonos todavía está pendiente.
- [ ] Ensayar el guion de 5–7 minutos: login, rol, conexión, rasguños, alerta, dispensación, ACK, historial y estado seguro.
- [x] Hacer commit y push de la versión inicial revisada; dejar `entrega_aai.txt` con el enlace correcto para el AAI. Los resultados y evidencias se subirán en commits posteriores cuando se ejecuten las pruebas pendientes.
- [ ] Presentar la demostración al docente y registrar la entrega solo cuando ocurra.

**Criterio de salida:** documentación y build listos; la demostración en clase y el envío por AAI quedan sin marcar hasta realizarlos realmente.

## 6. Plan de pruebas

En esta tabla, **Resultado real** y **Evidencia** se completan después de probar la versión Sumativa. Las pruebas Formativas anteriores no se reciclan como evidencia nueva del build final.

| ID | Caso | Resultado esperado | Resultado real | Evidencia |
|---|---|---|---|---|
| P1–P7 | Regresión Formativa con el nuevo protocolo | Conexión, monitoreo, alerta, control, rechazo de clave, pérdida segura y operación sin permiso de notificaciones siguen funcionando. | Pendiente | Pendiente |
| P8 | Registro y roles | Cuenta autorizada operador; nueva cuenta observador. | Pendiente | Pendiente |
| P9 | Login incorrecto | Cinco contraseñas erróneas generan error y bloqueo de 30 s. | Pendiente | Pendiente |
| P10 | Contraseña corta | Registro con 5 caracteres se rechaza; mínimo 8. | Pendiente | Pendiente |
| P11 | Control de acceso | Observador ve lecturas; botón Dispensar está deshabilitado y la regla deniega la escritura. | Pendiente | Pendiente |
| P12 | Almacenamiento | Un minuto conectado guarda aproximadamente 30 lecturas con hora. | Pendiente | Pendiente |
| P13 | Persistencia | Tras cerrar y volver a abrir, el Historial conserva las lecturas. | Pendiente | Pendiente |
| P14 | Credenciales protegidas | Firebase Authentication gestiona las contraseñas; perfil/lecturas no contienen la contraseña. | Pendiente | Pendiente |
| PL1 | Lectura real | Rasguño/botón de la cama llega al Panel y coincide con el estado reportado por las placas. | Pendiente | Pendiente |
| PL2 | Control real | DISPENSAR mueve el servo y el Panel recibe ACK. | Pendiente | Pendiente |
| PL3 | Notificación real | Evento físico llega al Panel y produce una sola alerta. | Pendiente | Pendiente |
| PL4 | Estado seguro | Cortar Wi-Fi/Panel durante DISPENSANDO deja servo CERRADO y alerta de enlace. | Pendiente | Pendiente |
| PL5 | Mensaje inválido | Enviar `HOLA` no ejecuta acciones ni cierra inesperadamente la app. | Pendiente | Pendiente |
| PL6 | Latencia | Evento físico a lectura/alerta tarda menos de 2 s; registrar el tiempo real. | Pendiente | Pendiente |
| PL7 | Regresión Unidad 1 | La comunicación Wi-Fi/MQTT/HiveMQ entre los dos ESP32 sigue funcionando. | Pendiente | Pendiente |

## 7. Matriz de trazabilidad

| Must | Fase | Prueba/evidencia |
|---|---|---|
| M1 | 1, 6 | P1–P7 y build en dos teléfonos |
| M2 | 0 | Revisión docente registrada |
| M3 | 0, 6 | Mockup actualizado y revisado |
| M4 | 1, 5 | P1, PL1 |
| M5 | 1, 3, 5 | P2, P12, PL1, PL6 |
| M6 | 1, 5 | P4, PL2 |
| M7 | 1, 5 | P3, P4, PL3 |
| M8 | 0, 2 | P8, P9, P10 |
| M9 | 3 | P12, P13 |
| M10 | 2, 4 | P5, P9–P11, P14; revisión de reglas/código |
| M11 | 1, 4, 5 | P6, PL4 |
| M12 | 1–5 | P1–P14 y PL1–PL7 con resultados reales |
| M13 | 6 | README, PLAN_MVP, historial Git, revisión de secretos |
| M14 | 6 | Guion y demostración real en clase |

## 8. Riesgos, decisiones y fuera de alcance

- **Firmware y alcance de integración:** los sketches adaptados compilan y el flujo de Panel a gateway está implementado; véase `docs/ANALISIS_WOKWI.md`. Aún no se han cargado a placas ni validado físicamente. Las pruebas PL1–PL7 y las latencias observadas siguen pendientes.
- **Credenciales del firmware:** el código versionado usa un archivo local ignorado `secrets.h` y plantillas sin credenciales reales. Las credenciales que venían escritas en los archivos recibidos deben rotarse antes de usar las placas; no se incluyeron en Git.
- **Archivos generados:** `.gitignore` excluye herramientas descargadas, `.verificacion/` y `build/`; los binarios de compilación no se subieron. La compilación actual se verificó con Arduino CLI y sus versiones se registran en `docs/ANALISIS_WOKWI.md`.
- **Firestore:** requiere habilitar la base en el proyecto Firebase, elegir región una vez y publicar las reglas incluidas en el repositorio. El código no puede considerarse probado hasta ejecutar P8–P14 con el proyecto real.
- **Credenciales de Firebase:** el archivo local `app/google-services.json` se excluye de Git. README explicará cómo configurar el proyecto sin subir secretos.
- **Modo de placa:** el modo debug sin cifrado se limita a pruebas de aula; la app Android a Android conserva AES-GCM. No declarar seguridad de producción en el enlace sin cifrar.
- **Entropía:** la sal aleatoria por sesión reduce reutilización de claves y ataques precalculados, pero no aumenta los seis dígitos del código. La vinculación sigue siendo una demostración y debe declararse como tal.
- **Normas:** `docs/SEGURIDAD_ISO_OT.md` relaciona las medidas implementadas con los temas ISO/IEC 27400 indicados por la guía, controles ISO/IEC 27002 complementarios y principios OT/IEC 62443; es una justificación académica, no certificación ni auditoría de conformidad.
- **Demostración:** solo el estudiante puede realizar la demostración en clase y confirmar su entrega AAI.

Fuera de alcance: MQTT/HiveMQ dentro de la app Android, Firebase Cloud Messaging, SQLite/Room, login social, publicación en Play Store, app iOS, múltiples comederos y funciones no solicitadas. MQTT/HiveMQ se conserva únicamente entre las placas de Unidad 1 si así está implementado en los sketches originales.

## 9. Fuentes de la planificación

- *Guía de desarrollo — Unidad 2 · App Android para IoT*: criterios y requisitos Sumativos de Hitos 5–8, pp. 14 y 27–33; evaluación de hardware adaptada al proyecto en la matriz de pruebas.
- *Desarrollo apoyado con IA*: Must M1–M14, declaración técnica, revisión del plan y trabajo fase por fase, pp. 5–6 y 8–17.
- La escala de apreciación publicada en el AAI conserva prioridad si agrega o precisa criterios.
