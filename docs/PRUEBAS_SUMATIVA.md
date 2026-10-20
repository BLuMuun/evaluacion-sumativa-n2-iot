# Pruebas de la Evaluación Sumativa

Los resultados de la Formativa no sustituyen las pruebas sobre este build Sumativo, que incorpora Login, Firestore, secuencias, sal de sesión y el modo gateway. Todo queda **PENDIENTE** hasta ejecutar la prueba y registrar evidencia real.

## Aplicación Android

| ID | Caso | Resultado esperado | Resultado real | Evidencia |
|---|---|---|---|---|
| P1 | Login con una cuenta válida de Firebase | Abre Conexión y muestra el rol cargado desde Firestore | PENDIENTE | Pendiente |
| P2 | Login con contraseña incorrecta | Muestra un error claro; no cierra la app | PENDIENTE | Pendiente |
| P3 | Crear una cuenta nueva | Firebase Authentication crea la cuenta y Firestore crea el perfil `observador` | PENDIENTE | Pendiente |
| P4 | Intentar dispensar como Observador | El botón permanece deshabilitado y no sale un `CMD` | PENDIENTE | Pendiente |
| P5 | Conectar Nodo Android y Panel Android con mismo código | Ambos muestran conectado; el Panel ve rasguños y estado | PENDIENTE | Pendiente |
| P6 | Registrar 1 y 2 rasguños dentro de 5 s | Panel muestra las lecturas en menos de 2 s | PENDIENTE | Pendiente |
| P7 | Completar el tercer rasguño | Panel muestra 3/3 y emite una sola notificación de solicitud | PENDIENTE | Pendiente |
| P8 | Dispensar como Operador | Nodo simulado abre ~1,5 s, cierra y Panel muestra ACK confirmado | PENDIENTE | Pendiente |
| P9 | Revisar el historial de Cloud Firestore | Aparecen lecturas válidas, en orden descendente, y persisten al reiniciar | PENDIENTE | Pendiente |
| P10 | Detener latidos del Panel y del Nodo | Al detectar 6 s sin mensaje válido, la conexión se cierra y queda estado seguro | PENDIENTE | Pendiente |
| P11 | Conectar con códigos distintos o alterar un paquete cifrado | El mensaje no se procesa; el enlace termina por timeout seguro | PENDIENTE | Pendiente |
| P12 | Reenviar una secuencia antigua o repetir `DISPENSAR` | Se descarta la secuencia duplicada/retrocedida y no se ejecuta dos veces | PENDIENTE | Pendiente |
| P13 | Negar permiso de notificaciones | La app sigue conectando y controlando; informa que las notificaciones no están habilitadas | PENDIENTE | Pendiente |
| P14 | Conectar Panel al gateway ESP32 de prueba | Recibe telemetría, valida secuencias, controla servo y muestra ACK | PENDIENTE | Pendiente |

## ESP32 cama ↔ ESP32 comedero/gateway

| ID | Caso | Resultado esperado | Resultado real | Evidencia |
|---|---|---|---|---|
| PL1 | Encender ambas placas y validar MQTT/TLS/HMAC | Cama y comedero conectan a HiveMQ; solo se aceptan mensajes con firma válida | PENDIENTE | Pendiente |
| PL2 | Pulsar el botón para rasguños 1, 2 y 3 | El Panel recibe cada lectura y la solicitud queda pendiente en 3/3 | PENDIENTE | Pendiente |
| PL3 | Conectar Panel a la IP TCP/5050 del comedero | Gateway entrega nonce de sesión, estados y latidos | PENDIENTE | Pendiente |
| PL4 | Operador solicita dispensación | Servo abre ~1,5 s, vuelve a CERRADO y recién después llega `ACK;comida;DISPENSADA` | PENDIENTE | Pendiente |
| PL5 | Cortar Wi-Fi o detener Panel durante una solicitud | Servo queda CERRADO, solicitud se cancela y la cama recibe ACK de cancelación al recuperar MQTT | PENDIENTE | Pendiente |
| PL6 | Repetir una secuencia TCP/MQTT o enviar HMAC inválido | El gateway descarta la orden repetida o firma inválida | PENDIENTE | Pendiente |
| PL7 | Repetir la prueba base de Unidad 1 y medir latencia | MQTT/TLS sigue operativo; lecturas llegan al Panel en menos de 2 s | PENDIENTE | Pendiente |

## Registro de ejecución

Completar esta ficha por sesión de pruebas y conservar capturas completas, sin contraseñas, códigos de vinculación, tokens ni credenciales:

| Fecha | Build / commit | Teléfonos y versión Android | Placas y firmware | Red usada | Responsable | Observaciones |
|---|---|---|---|---|---|---|
| Pendiente | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
