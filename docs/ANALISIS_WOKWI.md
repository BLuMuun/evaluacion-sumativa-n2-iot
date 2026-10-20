# Análisis de los proyectos Wokwi de Unidad 1

## Inventario

| Carpeta | Archivos | Componentes del diagrama | Proyecto original |
|---|---|---|---|
| `wokwi pruebas/cama/` | `cama.ino`, `diagram.json`, `libraries.txt`, `wokwi.toml`, `wokwi-project.txt`, `secrets.example.h` | ESP32 DevKit V1, botón/sensor simulado en GPIO18 a GND con `INPUT_PULLUP`, LED GPIO2 | [Wokwi cama — Unidad 1](https://wokwi.com/projects/473372736232098817) |
| `wokwi pruebas/comedero/` | `comedero.ino`, `diagram.json`, `libraries.txt`, `wokwi.toml`, `wokwi-project.txt`, `secrets.example.h` | ESP32 DevKit V1, servo GPIO18, LED GPIO2 | [Wokwi comedero — Unidad 1](https://wokwi.com/projects/473372724066513921) |

Los proyectos Wokwi enlazados son las referencias recibidas de Unidad 1; las nuevas modificaciones locales del repositorio no se han publicado en esos enlaces. Las bibliotecas indicadas son PubSubClient y ESP32Servo.

## Comportamiento recibido

- La Cama conectaba Wi-Fi, sincronizaba tiempo y utilizaba MQTT sobre TLS en HiveMQ Cloud. El botón representaba el sensor de vibración y contaba tres pulsaciones en una ventana fija de cinco segundos.
- El sketch recibido publicaba una solicitud firmada al llegar a tres, pero reiniciaba el contador e iniciaba un bloqueo de 30 segundos sin esperar el resultado del comedero.
- El Comedero verificaba HMAC y un contador monotónico, pero abría automáticamente el servo al recibir la solicitud MQTT. No recibía una decisión del Panel ni devolvía un ACK a la Cama.
- El servo usaba una máquina de estados no bloqueante para abrir ~1,5 s y se posicionaba cerrado al iniciar.

Los sketches recibidos contenían credenciales MQTT y una clave HMAC escritas en el código fuente. En esta copia se separaron en `secrets.h`, ignorado por Git, con `secrets.example.h` como plantilla. Las credenciales expuestas deben rotarse y reemplazarse localmente por credenciales nuevas antes de ejecutar placas o publicar Wokwi.

## Adaptación Sumativa en esta copia

### Cama

- Conserva la ventana fija de 5 s, el botón de GPIO18 y el LED de estado.
- Publica cada lectura 1–3 y el reinicio a 0 por MQTT con HMAC-SHA-256, secuencia monotónica persistente e ID persistente de solicitud.
- Al completar tres, fija una solicitud pendiente, ignora pulsaciones adicionales y reenvía 3/3 periódicamente hasta recibir un ACK válido.
- Solo el ACK HMAC válido para el ID de solicitud activo (`DISPENSADA` o `CANCELADA`) libera la solicitud y reinicia el contador.

### Comedero / gateway

- Conserva HiveMQ Cloud MQTT sobre TLS y valida HMAC, formato, secuencia y valores de lectura.
- Se suscribe a `comedero/solicitud` y publica ACK firmados en `comedero/acuse`. La ACL de HiveMQ debe permitir: Cama publica solicitud/lee acuse; Comedero lee solicitud/publica acuse.
- Escucha TCP/5050 para el Panel Android, negocia un nonce aleatorio por conexión y usa secuencia creciente por dirección. Reenvía lecturas y estados al Panel.
- Rechaza `DISPENSAR` si no hay solicitud pendiente. Al aceptarla, abre el servo por ~1,5 s sin bloquear; después cierra, persiste el ID finalizado y envía `ACK;comida;DISPENSADA` tanto al Panel como a la Cama.
- Si cae Wi-Fi/Panel o vence el latido, cierra el servo, cancela la solicitud y deja un ACK `CANCELADA` en espera de recuperar MQTT.
- El ACK MQTT se reintenta hasta recibir una lectura 0 de la Cama para el ID confirmado.

## Protocolo actualizado

Mensajes de aplicación:

```text
LECTURA;rasgunos;0..3
LECTURA;comedero;CERRADO|DISPENSANDO
CMD;comida;DISPENSAR
ACK;comida;DISPENSADA|CANCELADA
ACK;enlace;LATIDO
```

El sobre firmado MQTT tiene forma `R2|secuencia|idSolicitud|TIPO;CLAVE;VALOR|HMAC-SHA-256`. Las credenciales y la clave HMAC se declaran en el archivo local ignorado `secrets.h`. Se deben configurar valores nuevos y no reutilizar credenciales que aparecieron en los archivos originales.

El canal Panel–gateway usa TCP/5050 sin cifrado ni HMAC, limitado a modo de prueba en red local controlada. MQTT/TLS entre placas y AES-GCM Android–Android siguen separados de esa excepción. El nonce/contador TCP no es autenticación criptográfica.

## Evidencia y pendientes

- El análisis del código fuente y diagramas es estático. La compilación de ambos sketches pasó con Arduino CLI 1.5.2-rc.1, ESP32 Arduino core 3.3.11, PubSubClient 2.8 y ESP32Servo 3.2.1 para ESP32 DevKit V1. La Cama ocupa 77 % de flash y 14 % de RAM; el Comedero/Gateway ocupa 79 % de flash y 14 % de RAM.
- El sketch Comedero/Gateway compila después de usar `WiFiServer.accept()`. En la primera compilación limpia, ESP32Servo informó avisos no fatales por variables/etiquetas no usadas. No se ha cargado el firmware a placas ni probado en Wokwi; completar PL1–PL7 en [`PRUEBAS_SUMATIVA.md`](PRUEBAS_SUMATIVA.md).
- `build/`, ELF, BIN, mapas, `arduino-cli.exe` y credenciales locales se excluyen del repositorio. Solo se publican fuentes, diagramas, dependencias y plantillas sin secretos.
