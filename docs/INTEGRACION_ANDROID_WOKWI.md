# Resultado: integración Android + Wokwi

**Evaluación Sumativa N.º 2 · 23 de octubre de 2026**

Se integró la aplicación Android instalada en el Moto G35 con los ESP32 virtuales de **Cama** y **Comedero/Gateway** en Wokwi. La prueba cubrió telemetría MQTT/TLS, confirmaciones firmadas, enlace del Panel al gateway, dispensación manual, estados seguros y cooldown. Los resultados corresponden a simulación Wokwi; no se contaba con placas ESP32 físicas.

## Arquitectura probada

```text
Moto G35 (Panel Android)
        │ TCP/5050 por ADB reverse y Private Wokwi IoT Gateway
        ▼
ESP32 Comedero/Gateway (Wokwi) ── MQTT/TLS + HMAC ── ESP32 Cama (Wokwi)
        │                                                │
        └── servo simulado y ACK de entrega ─────────────┘
```

El teléfono se conectó al gateway mediante `127.0.0.1:5050`, `adb reverse tcp:5050 tcp:5050` y el reenvío definido en `wokwi pruebas/comedero/wokwi.toml`. La conexión entrante requiere el **Private Wokwi IoT Gateway**. Cama y Comedero se conectaron a HiveMQ usando MQTT sobre TLS; el gateway validó HMAC y secuencias antes de aplicar lecturas o ACK.

## Resultados observados

| Prueba | Resultado de integración |
|---|---|
| Conexión del Panel (PL3) | El Moto G35 mostró `CONECTADO`, `0/3` y `CERRADO`; el socket TCP/5050 se mantuvo activo durante más de 60 s. |
| Lecturas y confirmaciones (PL1, PL2) | Las pulsaciones de Cama llegaron al gateway como `1/3`, `2/3` y `3/3`; cada lectura fue confirmada por MQTT. Al vencer la ventana de 5 s, el reinicio a `0/3` también fue confirmado. |
| Latencia (PL7) | El estudiante informó una medición manual de **1,95 s** entre la segunda pulsación y observar `2/3` en el Moto, bajo el umbral de 2 s. Los logs confirman la secuencia MQTT; no se conservó una captura simultánea de cronómetro y Panel, por lo que el tiempo queda identificado como medición reportada. |
| Dispensación y cierre (PL4, P14) | El operador inició la entrega desde Android. Wokwi mostró el movimiento del servo por aproximadamente 1,5 s, el retorno a `CERRADO`, la confirmación de entrega y el ACK. La aplicación volvió a `0/3` y `CERRADO`. |
| Cooldown (PL8) | Después del cierre, nuevas entregas quedaron bloqueadas durante 10 s; tras expirar el cooldown se aceptó una nueva solicitud. |
| Desconexión segura (PL5) | Con `3/3` pendiente, se desconectó el Panel. El gateway cerró de forma segura, dejó el servo en `CERRADO` y publicó ACK `CANCELADA`; Cama recibió el ACK y restableció `0/3`. |
| Firma inválida (PL6) | Una lectura MQTT/TLS con HMAC incorrecto fue rechazada por el gateway; el actuador permaneció `CERRADO` y no se ejecutó dispensación. |
| Timeout de enlace (P10) | El modo Android–Android se probó en las dos direcciones. Además, en Android + Wokwi el gateway canceló una solicitud pendiente al perder los latidos del Panel; un harness TCP local verificó el timeout de Android cuando el gateway deja de responder a sus latidos. |

## Evidencia incluida

- La matriz completa P1–P15 y PL1–PL8, con el resultado y alcance de cada caso, está en [`PRUEBAS_SUMATIVA.md`](PRUEBAS_SUMATIVA.md).
- La conexión Android–gateway está respaldada por [`PL3-Panel-MotoG35-conectado-20261023.png`](evidencia-sumativa/PL3-Panel-MotoG35-conectado-20261023.png), [`PL3-Gateway-Wokwi-60s-20261023.png`](evidencia-sumativa/PL3-Gateway-Wokwi-60s-20261023.png) y [`PL3-PL6-P10-Gateway-TCP-20261023.txt`](evidencia-sumativa/PL3-PL6-P10-Gateway-TCP-20261023.txt).
- La dispensación, el ACK y el cooldown constan en [`PL4-Wokwi-ACK-Cooldown-20261023.png`](evidencia-sumativa/PL4-Wokwi-ACK-Cooldown-20261023.png), junto con las capturas del Panel en [`PL4-DISPENSANDO-MotoG35-20261023.png`](evidencia-sumativa/PL4-DISPENSANDO-MotoG35-20261023.png) y [`PL4-ACK-Cerrado-MotoG35-20261023.png`](evidencia-sumativa/PL4-ACK-Cerrado-MotoG35-20261023.png).
- La cancelación por desconexión y el ACK de Cama se documentan en [`PL5-ACK-CANCELADA-Cama-Wokwi-20261023.png`](evidencia-sumativa/PL5-ACK-CANCELADA-Cama-Wokwi-20261023.png); el rechazo de firma está en [`PL6-HMAC-invalido-rechazado-Comedero-Wokwi-20261023.png`](evidencia-sumativa/PL6-HMAC-invalido-rechazado-Comedero-Wokwi-20261023.png).
- El resultado de PL7 y su límite de medición están descritos en [`PL7-Medicion-latencia-20261023.txt`](evidencia-sumativa/PL7-Medicion-latencia-20261023.txt), con logs en [`PL7-MQTT-ACK-Cama-Comedero-20261023.png`](evidencia-sumativa/PL7-MQTT-ACK-Cama-Comedero-20261023.png) y el estado final del Panel en [`PL7-MotoG35-estado-final-20261023.png`](evidencia-sumativa/PL7-MotoG35-estado-final-20261023.png).

## Alcance y limitaciones

- La integración demuestra el intercambio de mensajes y la secuencia lógica del servo dentro de Wokwi; no mide fuerza mecánica, cantidad de alimento ni comportamiento eléctrico de placas físicas.
- P10 se aprobó en las dos direcciones Android–Android. La prueba integrada Panel→gateway con el Comedero Wokwi validó cancelación y ACK; la respuesta silenciosa gateway→Panel se comprobó con un harness TCP local, no con el firmware del Comedero. No se realizaron pruebas de pérdida con placas físicas.
- P12 y P15 cuentan con pruebas automatizadas del código, pero no con inyección directa de tramas en dispositivos físicos.
- El canal de prueba App–gateway es TCP local sin cifrado ni autenticación criptográfica. Debe usarse únicamente en una red controlada; MQTT entre los ESP32 conserva TLS y HMAC.
- Las credenciales locales y `secrets.h` no se distribuyen en Git. Se incluyen solamente plantillas `secrets.example.h`; quien repita la simulación debe configurar sus propios secretos locales.

La guía de ejecución está en [`INSTALACION.md`](INSTALACION.md), el análisis del firmware en [`ANALISIS_WOKWI.md`](ANALISIS_WOKWI.md) y la relación de seguridad en [`SEGURIDAD_ISO_OT.md`](SEGURIDAD_ISO_OT.md).
