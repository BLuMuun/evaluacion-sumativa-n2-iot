# Instalación y ejecución

## Requisitos de desarrollo

- Android Studio.
- JDK 21 para Gradle (en Android Studio, usa **Settings → Build Tools → Gradle → Gradle JDK 21**).
- Android SDK Platform 36 y Build Tools instalados mediante SDK Manager.
- Teléfonos Android con API 26 o posterior para pruebas reales.
- Una cuenta/proyecto Firebase configurado según [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md).

## Compilar y abrir en Android Studio

1. Clona `https://github.com/BLuMuun/evaluacion-sumativa-n2-iot.git` o descarga su ZIP.
2. Copia el `google-services.json` descargado de Firebase a `app/google-services.json`.
3. Abre en Android Studio la carpeta raíz `evaluacion-sumativa-n2-iot` y espera la sincronización de Gradle.
4. Selecciona un teléfono físico o emulador y ejecuta la configuración `app`.

Desde PowerShell, con JDK 21 configurado:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. `google-services.json`, `local.properties` y los archivos locales `secrets.h` están ignorados por Git.

## Probar con dos teléfonos Android

1. Instala el APK en ambos teléfonos, inicia sesión con una cuenta autorizada y habilita notificaciones si el sistema lo pregunta.
2. Conecta ambos a la misma red Wi-Fi o hotspot. Evita redes con aislamiento de clientes.
3. En Teléfono A, elige **Teléfonos → Nodo**, ingresa un código de 6 dígitos y toca **Iniciar Nodo**. Anota la IP que muestra.
4. En Teléfono B, elige **Teléfonos → Panel**, ingresa el mismo código y la IP del Nodo y toca **Conectar Panel**.
5. Verifica rasguños, alerta, permisos del rol, dispensación y ACK. Un rol Operador debe estar asignado desde Firebase Console.

Los dos teléfonos deben usar el mismo código durante esa sesión. Una sal aleatoria se negocia al conectar; la clave AES se deriva en ambos extremos.

## Probar con el gateway ESP32

1. En cada carpeta `wokwi pruebas/cama` y `wokwi pruebas/comedero`, copia `secrets.example.h` como `secrets.h`.
2. Introduce credenciales **nuevas** para Wi-Fi/HiveMQ y una clave HMAC aleatoria compartida por ambas placas; no pongas esos datos en el `.ino` ni los publiques.
3. En HiveMQ configura la ACL para que la Cama publique en `comedero/solicitud` y lea `comedero/acuse`; el Comedero/Gateway lee `comedero/solicitud` y publica `comedero/acuse`.
4. Compila y carga los sketches en las placas ESP32 de Unidad 1. Conecta ambas a la misma red Wi-Fi y revisa la IP del gateway en el monitor serie.
5. En la app elige **ESP32**, ingresa la IP del gateway y toca **Conectar Panel**. Esta modalidad de prueba no pide código de vinculación.

La comunicación App–ESP32 en esta modalidad está sin cifrar y solo debe usarse en una red de laboratorio controlada. La comunicación cama–comedero conserva MQTT sobre TLS y HMAC. Las pruebas PL1–PL7 siguen pendientes hasta realizarlas con hardware.
