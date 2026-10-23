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
- Solo el ACK HMAC válido para el ID de solicitud activo (`DISPENSADA` o `CANCELADA`) libera la solicitud y reinicia el contador. Tras una entrega confirmada, el botón queda en cooldown por 10 s.

### Comedero / gateway

- Conserva HiveMQ Cloud MQTT sobre TLS y valida HMAC, formato, secuencia y valores de lectura.
- Se suscribe a `comedero/solicitud` y publica ACK firmados en `comedero/acuse`. La ACL de HiveMQ debe permitir: Cama publica solicitud/lee acuse; Comedero lee solicitud/publica acuse.
- Escucha TCP/5050 para el Panel Android, negocia un nonce aleatorio por conexión y usa secuencia creciente por dirección. Reenvía lecturas y estados al Panel y cierra la sesión por seguridad tras 60 s sin latidos válidos.
- Rechaza `DISPENSAR` si no hay solicitud pendiente. Al aceptarla, abre el servo por ~1,5 s sin bloquear; después cierra, persiste el ID finalizado y envía `ACK;comida;DISPENSADA` tanto al Panel como a la Cama.
- Mantiene un cooldown de 10 s desde el cierre del servo; durante ese intervalo no acepta otra dispensación, incluso si llega una orden por otra ruta.
- Si cae Wi-Fi/Panel o vence el latido, cierra el servo, cancela la solicitud y deja un ACK `CANCELADA` en espera de recuperar MQTT.
- El ACK MQTT se reintenta hasta recibir una lectura 0 de la Cama para el ID confirmado.

## Protocolo actualizado

Mensajes de aplicación:

```text
LECTURA;rasgunos;0..3
LECTURA;comedero;CERRADO|DISPENSANDO
CMD;comida;DISPENSAR
ACK;comida;DISPENSADA|CANCELADA
ACK;lectura;secuenciaCama
ACK;enlace;LATIDO
```

El sobre firmado MQTT tiene forma `R2|secuencia|idSolicitud|TIPO;CLAVE;VALOR|HMAC-SHA-256`. Cada lectura recibe un acuse firmado que identifica la secuencia de Cama. Cama conserva el mensaje en su cola y reintenta la misma secuencia cada 750 ms hasta recibir el acuse; el gateway confirma de nuevo las retransmisiones válidas sin aplicar dos veces la lectura. Las credenciales y la clave HMAC se declaran en el archivo local ignorado `secrets.h`. Se deben configurar valores nuevos y no reutilizar credenciales que aparecieron en los archivos originales.

El canal Panel–gateway usa TCP/5050 sin cifrado ni HMAC, limitado a modo de prueba en red local controlada. MQTT/TLS entre placas y AES-GCM Android–Android siguen separados de esa excepción. El nonce/contador TCP no es autenticación criptográfica.

## Evidencia y pendientes

- La compilación de ambos sketches pasó el 22-10-2026 con Arduino CLI 1.5.2-rc.1, ESP32 Arduino core 3.3.11, PubSubClient 2.8 y ESP32Servo 3.2.1 para ESP32 DevKit V1. La Cama ocupa 77 % de flash y 14 % de RAM; el Comedero/Gateway ocupa 79 % de flash y 14 % de RAM.
- La prueba visual inicial confirmó el movimiento y el cierre del servo; después, el 23-10-2026, se revalidó el cooldown de 10 s y se capturó el ACK de entrega. También se validaron PL5 y el timeout Panel→gateway con una solicitud pendiente: el gateway mantuvo el actuador cerrado, canceló la solicitud, publicó `CANCELADA` y la Cama restableció su estado seguro. El timeout del Panel ante un gateway silencioso se probó con un harness TCP local, no con el firmware del Comedero. Las pruebas con placas físicas siguen pendientes; el detalle vigente está en [`PRUEBAS_SUMATIVA.md`](PRUEBAS_SUMATIVA.md).
- `build/`, ELF, BIN, mapas, `arduino-cli.exe` y credenciales locales se excluyen del repositorio. Solo se publican fuentes, diagramas, dependencias y plantillas sin secretos.

## Diagnóstico de `Error MQTT: -2` en Wokwi (22-10-2026)

- Las capturas muestran que Wokwi se conecta a `Wokwi-GUEST`, recibe una IP privada y sincroniza NTP. Eso confirma la conectividad Wi-Fi; no indica un fallo de contraseña.
- En [PubSubClient](https://github.com/knolleary/pubsubclient/blob/master/src/PubSubClient.h), `-2` corresponde a `MQTT_CONNECT_FAILED`: el cliente no pudo completar la conexión subyacente. La causa encontrada en las cuatro copias locales (Unidad 1 y Sumativa, Cama y Comedero) era el bloque PEM de la CA, cuya Base64 estaba truncada/mal formada. Se sustituyó por ISRG Root X1. La huella SHA-256 se cotejó con la [publicación de certificados de Let’s Encrypt](https://letsencrypt.org/certificates/) y el certificado se pudo parsear en los cuatro sketches.
- Un ensayo de TLS desde el PC, usando esa misma CA, validó TLS 1.2 contra el puerto 8883. Una prueba MQTT 3.1.1 de conexión y desconexión recibió `CONNACK 0`, por lo que el broker aceptó las credenciales locales probadas. No se publicó ningún mensaje ni se creó una suscripción durante ese ensayo.
- Las cuatro variantes compilaron con Arduino CLI 1.5.2-rc.1 y ESP32 core 3.3.11: Cama Unidad 1, 76 % flash/14 % RAM; Comedero Unidad 1, 78 %/14 %; Cama Sumativa, 77 %/14 %; Comedero Sumativa, 79 %/14 %.
- La clave HMAC de las dos copias locales de Unidad 1 se rotó a una clave aleatoria de 256 bits y quedó igual en ambas. No se documenta ni publica su valor.
- La pendiente histórica de volver a iniciar las simulaciones quedó resuelta: el 23-10-2026 ambos ESP32 virtuales intercambiaron lecturas y ACK por MQTT/TLS, completaron una dispensación con servo cerrado y cooldown, y ejecutaron la cancelación segura de PL5. La matriz [`PRUEBAS_SUMATIVA.md`](PRUEBAS_SUMATIVA.md) mantiene separados los casos validados, parciales y aún pendientes.
- Los sketches locales originales de Unidad 1 tienen usuario/contraseña MQTT incrustados. No se copiaron al repositorio Sumativa. Los `secrets.h` locales de Sumativa están ignorados por Git: cama y comedero comparten host, tópicos y clave HMAC, y usan credenciales MQTT separadas. No se publican sus valores. La configuración ya fue probada con ambos nodos conectados; conserva esas cuentas solo en los archivos locales.
