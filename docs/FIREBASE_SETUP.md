# Configuración de Firebase para la Sumativa

La aplicación usa Firebase Authentication con correo y contraseña, además de Cloud Firestore para perfiles e historial. No se suben configuraciones locales, contraseñas ni cuentas al repositorio.

## 1. Registrar la app Android

1. En Firebase Console abre el proyecto de este comedero y elige **Agregar app → Android**.
2. Usa el identificador exacto `cl.inacap.comederoiot`.
3. Descarga `google-services.json` y guárdalo en `app/google-services.json`.
4. Sincroniza Gradle en Android Studio. El archivo está ignorado por Git y no debe publicarse.

## 2. Habilitar autenticación

En **Authentication → Sign-in method**, habilita **Correo electrónico/Contraseña**. Crea los usuarios desde la app o Firebase Console. La contraseña debe tener al menos 8 caracteres; la app no la guarda en Firestore.

Las cuentas creadas desde la app reciben inicialmente el rol `observador`. El primer operador se designa manualmente:

1. Inicia sesión para que la aplicación cree el documento `usuarios/{UID}`.
2. En **Firestore Database → Data → usuarios → {UID}**, cambia `rol` de `observador` a `operador`.
3. Cierra y vuelve a iniciar sesión en la app para cargar el rol actualizado.

Al iniciar una sesión Android–Android, el Panel envía su token de Firebase dentro del canal AES-GCM. El Nodo usa ese token para leer únicamente el campo `rol` del perfil `usuarios/{UID}` mediante la API REST de Firestore. Firebase aplica la regla de lectura del perfil propio; el Nodo solo permite `CMD` cuando esa consulta devuelve `operador`. Un usuario observador sigue conectado y puede monitorear, pero sus órdenes se descartan incluso si elude la interfaz.

No agregues el campo de rol al formulario de registro. Las reglas bloquean la autoasignación desde la app cliente; los cambios de administración se realizan en Firebase Console.

## 3. Crear Firestore y publicar las reglas

1. Crea una base de datos Cloud Firestore en la región elegida para el proyecto.
2. En **Firestore Database → Rules**, reemplaza las reglas de prueba con el contenido de [`../firestore.rules`](../firestore.rules) y pulsa **Publicar**.
3. La configuración Firebase CLI está en [`../firebase.json`](../firebase.json). Si usas Firebase CLI, inicia sesión y ejecuta `firebase deploy --only firestore:rules` desde la raíz del repositorio seleccionado.

Las reglas permiten a cuentas autenticadas con un perfil leer el historial compartido. Solo un usuario con rol `operador` puede guardar lecturas; el perfil propio solo se puede crear como `observador` y la app no puede editarlo.

## 4. Datos esperados

- `usuarios/{uid}`: `correo`, `rol`, `creadoEn`.
- `lecturas/{id}`: `usuarioUid`, `momento` (timestamp del servidor), `clave`, `valor`, `origen`, `sesionId`.

El historial muestra las últimas 50 lecturas. El Panel guarda los mensajes `LECTURA` válidos que recibe cuando el usuario activo es operador. Un observador conserva monitoreo en vivo y puede consultar lecturas ya guardadas.

## 5. Verificación

- Registra una cuenta nueva y confirma que su rol sea `observador`.
- Asigna `operador` desde Firebase Console a una cuenta autorizada.
- Inicia sesión con cada rol y verifica que solo Operador pueda dispensar.
- Con el Panel como Observador, confirma que los datos siguen llegando y que una orden enviada fuera del botón se descarta en el Nodo.
- Con el Panel como Operador, confirma que el Nodo verifica el rol antes de aceptar una orden. El dispositivo Nodo requiere Internet para consultar Firestore durante la autenticación de sesión; si no puede verificar el perfil, falla de forma cerrada.
- Conecta un Nodo y confirma que aparezcan documentos en `lecturas` y en la pantalla Historial.
- Prueba las reglas desde Firebase Console o el emulador antes de una demostración.

La integración de credenciales y reglas requiere una prueba conectada al proyecto Firebase real. La compilación local por sí sola no prueba el acceso a Firebase.
