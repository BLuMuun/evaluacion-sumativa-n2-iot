#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <mbedtls/md.h>
#include <string.h>
#include <time.h>
#include "secrets.h"

const char* WIFI_SSID = WIFI_SSID_VALUE;
const char* WIFI_PASSWORD = WIFI_PASSWORD_VALUE;
const char* MQTT_HOST = MQTT_HOST_VALUE;
const int MQTT_PORT = 8883;
const char* MQTT_USERNAME = MQTT_USERNAME_VALUE;
const char* MQTT_PASSWORD = MQTT_PASSWORD_VALUE;
const char* MQTT_TOPIC = MQTT_TOPIC_VALUE;
const char* MQTT_ACK_TOPIC = MQTT_TOPIC_ACK_VALUE;
const char* HMAC_SECRET = HMAC_SECRET_VALUE;

static const char* ROOT_CA PROGMEM = R"EOF(
-----BEGIN CERTIFICATE-----
MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4
WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu
ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY
MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc
h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+
0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U
A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW
T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH
B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC
B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv
KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn
OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn
jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw
qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI
rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV
HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq
hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL
ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ
3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK
NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5
ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur
TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC
jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc
oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq
4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA
mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d
emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=
-----END CERTIFICATE-----
)EOF";

const int PIN_RASGUNO = 18;
const int PIN_LED = 2;
const int RASGUNOS_NECESARIOS = 3;
const unsigned long VENTANA_FIJA_MS = 5000;
const unsigned long REINTENTO_SOLICITUD_MS = 2000;
const unsigned long REINTENTO_LECTURA_MQTT_MS = 750;
const unsigned long REINTENTO_WIFI_MS = 8000;
const unsigned long REINTENTO_MQTT_MS = 4000;
const unsigned long DEBOUNCE_MS = 150;
const unsigned long COOLDOWN_DISPENSACION_MS = 10000;
const int CAPACIDAD_COLA = 8;

WiFiClientSecure secureClient;
PubSubClient mqttClient(secureClient);
Preferences preferences;

struct LecturaEnCola {
  uint64_t requestId;
  uint8_t cantidad;
  uint64_t secuencia;
  unsigned long ultimoIntento;
  uint8_t intentos;
};

LecturaEnCola colaLecturas[CAPACIDAD_COLA];
int cabezaCola = 0;
int cantidadCola = 0;

uint8_t contadorRasgunos = 0;
uint64_t requestIdActual = 0;
uint64_t secuenciaSalida = 0;
uint64_t ultimaSecuenciaAck = 0;
unsigned long inicioVentana = 0;
unsigned long ultimoReintentoSolicitud = 0;
unsigned long ultimoIntentoWiFi = 0;
unsigned long ultimoIntentoMQTT = 0;
unsigned long ultimaNotificacionMQTT = 0;
int ultimoEstadoMQTTReportado = 999;
unsigned long ultimaPulsacion = 0;
unsigned long inicioCooldown = 0;
bool solicitudPendiente = false;
bool botonAnterior = HIGH;
bool horaLista = false;
bool cooldownActivo = false;

bool enCooldownDispensacion() {
  return cooldownActivo && millis() - inicioCooldown < COOLDOWN_DISPENSACION_MS;
}

bool generarHMAC(const char* texto, char* salida, size_t largoSalida) {
  unsigned char bytes[32];
  const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  if (info == nullptr || largoSalida < 65) return false;
  if (mbedtls_md_hmac(info,
      reinterpret_cast<const unsigned char*>(HMAC_SECRET), strlen(HMAC_SECRET),
      reinterpret_cast<const unsigned char*>(texto), strlen(texto), bytes) != 0) return false;
  for (int i = 0; i < 32; i++) snprintf(salida + i * 2, 3, "%02x", bytes[i]);
  salida[64] = '\0';
  return true;
}

bool compararConstante(const char* primero, const char* segundo) {
  size_t a = strlen(primero);
  size_t b = strlen(segundo);
  if (a != b) return false;
  uint8_t diferencia = 0;
  for (size_t i = 0; i < a; i++) diferencia |= primero[i] ^ segundo[i];
  return diferencia == 0;
}

bool leerU64(const String& texto, uint64_t& valor) {
  if (texto.isEmpty()) return false;
  for (size_t i = 0; i < texto.length(); i++) if (!isDigit(texto[i])) return false;
  char* fin = nullptr;
  unsigned long long leido = strtoull(texto.c_str(), &fin, 10);
  if (fin == nullptr || *fin != '\0') return false;
  valor = static_cast<uint64_t>(leido);
  return true;
}

void encolarLectura(uint8_t cantidad) {
  if (cantidadCola == CAPACIDAD_COLA) {
    cabezaCola = (cabezaCola + 1) % CAPACIDAD_COLA;
    cantidadCola--;
  }
  int final = (cabezaCola + cantidadCola) % CAPACIDAD_COLA;
  colaLecturas[final] = {requestIdActual, cantidad, 0, 0, 0};
  cantidadCola++;
}

bool publicarLectura(LecturaEnCola& lectura) {
  if (!mqttClient.connected()) return false;
  if (lectura.secuencia == 0) {
    lectura.secuencia = secuenciaSalida + 1;
    if (lectura.secuencia == 0) return false;
    secuenciaSalida = lectura.secuencia;
    preferences.putULong64("txSeq", secuenciaSalida);
  }

  char firmado[128];
  snprintf(firmado, sizeof(firmado), "R2|%llu|%llu|LECTURA;rasgunos;%u",
      static_cast<unsigned long long>(lectura.secuencia),
      static_cast<unsigned long long>(lectura.requestId),
      static_cast<unsigned int>(lectura.cantidad));
  char firma[65];
  if (!generarHMAC(firmado, firma, sizeof(firma))) return false;

  String payload = String(firmado) + "|" + firma;
  bool reintento = lectura.intentos > 0;
  lectura.ultimoIntento = millis();
  if (lectura.intentos < UINT8_MAX) lectura.intentos++;
  bool enviado = mqttClient.publish(MQTT_TOPIC, payload.c_str());
  if (enviado) {
    Serial.printf("[MQTT/TLS] Lectura %s: secuencia %llu, valor %u/3; esperando acuse del gateway.\n",
        reintento ? "reenviada" : "publicada",
        static_cast<unsigned long long>(lectura.secuencia),
        static_cast<unsigned int>(lectura.cantidad));
  } else {
    Serial.printf("[MQTT/TLS] Publicación pendiente: secuencia %llu; se conservará para reintentar.\n",
        static_cast<unsigned long long>(lectura.secuencia));
  }
  return enviado;
}

void despacharCola() {
  if (cantidadCola == 0 || !mqttClient.connected()) return;
  LecturaEnCola& lectura = colaLecturas[cabezaCola];
  if (lectura.intentos > 0 && millis() - lectura.ultimoIntento < REINTENTO_LECTURA_MQTT_MS) return;
  publicarLectura(lectura);
}

void procesarAcuseMQTT(char* topic, byte* payload, unsigned int largo) {
  if (strcmp(topic, MQTT_ACK_TOPIC) != 0 || largo == 0 || largo >= 220) return;
  char texto[220];
  memcpy(texto, payload, largo);
  texto[largo] = '\0';
  String linea(texto);

  int a = linea.indexOf('|');
  int b = a < 0 ? -1 : linea.indexOf('|', a + 1);
  int c = b < 0 ? -1 : linea.indexOf('|', b + 1);
  int d = c < 0 ? -1 : linea.indexOf('|', c + 1);
  if (a <= 0 || b <= a + 1 || c <= b + 1 || d <= c + 1 || linea.indexOf('|', d + 1) >= 0) return;
  if (linea.substring(0, a) != "R2") return;

  uint64_t secuencia = 0;
  uint64_t id = 0;
  if (!leerU64(linea.substring(a + 1, b), secuencia) ||
      !leerU64(linea.substring(b + 1, c), id) || secuencia <= ultimaSecuenciaAck) return;

  String mensaje = linea.substring(c + 1, d);
  bool esAcuseLectura = mensaje.startsWith("ACK;lectura;");
  uint64_t secuenciaLeida = 0;
  if (esAcuseLectura) {
    if (!leerU64(mensaje.substring(String("ACK;lectura;").length()), secuenciaLeida)) return;
  } else if (mensaje != "ACK;comida;DISPENSADA" && mensaje != "ACK;comida;CANCELADA") {
    return;
  }
  String firmado = linea.substring(0, d);
  char firmaEsperada[65];
  if (!generarHMAC(firmado.c_str(), firmaEsperada, sizeof(firmaEsperada))) return;
  String firmaRecibida = linea.substring(d + 1);
  if (!compararConstante(firmaEsperada, firmaRecibida.c_str())) {
    Serial.println("ACK MQTT rechazado: firma inválida.");
    return;
  }

  if (esAcuseLectura) {
    if (cantidadCola == 0) return;
    LecturaEnCola& lectura = colaLecturas[cabezaCola];
    if (id != lectura.requestId || secuenciaLeida != lectura.secuencia) {
      Serial.println("Acuse MQTT ignorado: no coincide con la lectura pendiente.");
      return;
    }

    ultimaSecuenciaAck = secuencia;
    preferences.putULong64("ackSeq", ultimaSecuenciaAck);
    Serial.printf("[MQTT/TLS] Gateway confirmó la lectura %u/3 (secuencia %llu).\n",
        static_cast<unsigned int>(lectura.cantidad),
        static_cast<unsigned long long>(lectura.secuencia));
    cabezaCola = (cabezaCola + 1) % CAPACIDAD_COLA;
    cantidadCola--;
    return;
  }

  if (!solicitudPendiente || id != requestIdActual) {
    Serial.println("ACK MQTT ignorado: no corresponde a la solicitud activa.");
    return;
  }

  ultimaSecuenciaAck = secuencia;
  preferences.putULong64("ackSeq", ultimaSecuenciaAck);
  solicitudPendiente = false;
  contadorRasgunos = 0;
  inicioVentana = 0;
  cabezaCola = 0;
  cantidadCola = 0;
  preferences.putBool("pendiente", false);
  digitalWrite(PIN_LED, LOW);
  if (mensaje == "ACK;comida;DISPENSADA") {
    inicioCooldown = millis();
    cooldownActivo = true;
    Serial.println("[Cooldown] Cama bloqueada por hasta 10 s después de dispensar.");
  }
  encolarLectura(0);
  Serial.println(mensaje == "ACK;comida;DISPENSADA"
      ? "Entrega confirmada; ventana reiniciada."
      : "Solicitud cancelada; estado seguro restablecido.");
}

void conectarWiFiSiCorresponde() {
  static bool enlaceWiFiMostrado = false;
  if (WiFi.status() == WL_CONNECTED) {
    if (!enlaceWiFiMostrado) {
      Serial.printf("[Wi-Fi] Conectado. IP: %s\n", WiFi.localIP().toString().c_str());
      enlaceWiFiMostrado = true;
    }
    return;
  }
  if (enlaceWiFiMostrado) {
    Serial.println("[Wi-Fi] Enlace perdido; intentando reconectar.");
    enlaceWiFiMostrado = false;
  }
  if (mqttClient.connected()) mqttClient.disconnect();

  unsigned long ahora = millis();
  if (ahora - ultimoIntentoWiFi < REINTENTO_WIFI_MS) return;
  ultimoIntentoWiFi = ahora;
  Serial.println("[Wi-Fi] Sin conexión; se reintentará automáticamente.");
  WiFi.reconnect();
}

const char* descripcionEstadoMQTT(int estado) {
  switch (estado) {
    case -4: return "se agotó el tiempo de conexión";
    case -3: return "se perdió la conexión con el broker";
    case -2: return "no se pudo abrir la conexión de red";
    case -1: return "el cliente está desconectado";
    case 1: return "el broker rechazó la versión del protocolo";
    case 2: return "el identificador del dispositivo fue rechazado";
    case 3: return "el broker no está disponible";
    case 4: return "el broker rechazó usuario o contraseña";
    case 5: return "el broker no autorizó el acceso de este usuario";
    default: return "respuesta desconocida del broker";
  }
}

bool valorConfiguracionPendiente(const char* valor) {
  if (valor == nullptr || valor[0] == '\0') return true;
  return strstr(valor, "YOUR_") != nullptr ||
         strstr(valor, "GENERATE_") != nullptr ||
         strstr(valor, "CHANGE_ME") != nullptr;
}

bool configuracionMQTTLista() {
  return !valorConfiguracionPendiente(MQTT_HOST) &&
         !valorConfiguracionPendiente(MQTT_USERNAME) &&
         !valorConfiguracionPendiente(MQTT_PASSWORD) &&
         !valorConfiguracionPendiente(HMAC_SECRET) &&
         strlen(HMAC_SECRET) >= 32;
}

void informarFalloMQTT() {
  const int estado = mqttClient.state();
  const unsigned long ahora = millis();
  if (estado == ultimoEstadoMQTTReportado && ahora - ultimaNotificacionMQTT < 30000) return;

  ultimoEstadoMQTTReportado = estado;
  ultimaNotificacionMQTT = ahora;
  Serial.println();
  Serial.printf("[MQTT] Nodo Cama: %s (código %d).\n", descripcionEstadoMQTT(estado), estado);
  if (estado == 4 || estado == 5) {
    Serial.println("[MQTT] Revisa la cuenta y sus permisos en HiveMQ; las credenciales no se muestran.");
  } else {
    Serial.println("[MQTT] Nuevo intento automático en 4 segundos.");
  }
}

bool conectarMQTT() {
  static bool avisoConfiguracionMostrado = false;
  if (!configuracionMQTTLista()) {
    if (!avisoConfiguracionMostrado) {
      Serial.println();
      Serial.println("[Configuración] HiveMQ todavía no está configurado.");
      Serial.println("[Configuración] Completa usuario y contraseña nuevos en secrets.h local y reinicia la simulación.");
      avisoConfiguracionMostrado = true;
    }
    return false;
  }
  if (mqttClient.connected() || WiFi.status() != WL_CONNECTED || !horaLista) return mqttClient.connected();
  unsigned long ahora = millis();
  if (ahora - ultimoIntentoMQTT < REINTENTO_MQTT_MS) return false;
  ultimoIntentoMQTT = ahora;

  String idCliente = "ESP32_CAMA_" + String((uint32_t)ESP.getEfuseMac(), HEX);
  if (!mqttClient.connect(idCliente.c_str(), MQTT_USERNAME, MQTT_PASSWORD)) {
    // PubSubClient 2.8 no cierra el transporte cuando falla la apertura TCP/TLS.
    // Libera el socket para que el siguiente intento empiece con una conexión limpia.
    secureClient.stop();
    informarFalloMQTT();
    return false;
  }
  if (!mqttClient.subscribe(MQTT_ACK_TOPIC)) {
    mqttClient.disconnect();
    Serial.println("No se pudo suscribir al tópico de ACK.");
    return false;
  }
  ultimoEstadoMQTTReportado = 999;
  Serial.println();
  Serial.println("[MQTT/TLS] Conectado a HiveMQ; esperando confirmaciones del comedero.");
  return true;
}

void iniciarRasgunos() {
  unsigned long ahora = millis();
  if (enCooldownDispensacion()) {
    unsigned long restante = COOLDOWN_DISPENSACION_MS - (ahora - inicioCooldown);
    Serial.printf("[Cooldown] Espera %lu s antes de iniciar otra solicitud.\n",
        (restante + 999) / 1000);
    return;
  }
  if (solicitudPendiente) return;

  if (contadorRasgunos > 0 && ahora - inicioVentana >= VENTANA_FIJA_MS) {
    contadorRasgunos = 0;
    inicioVentana = 0;
    encolarLectura(0);
  }

  if (contadorRasgunos == 0) {
    requestIdActual++;
    if (requestIdActual == 0) requestIdActual = 1;
    preferences.putULong64("requestId", requestIdActual);
    inicioVentana = ahora;
  }

  contadorRasgunos++;
  if (contadorRasgunos > RASGUNOS_NECESARIOS) return;

  Serial.printf("[Sensor] Rasguño detectado: %u/3\n", contadorRasgunos);
  encolarLectura(contadorRasgunos);
  if (contadorRasgunos == RASGUNOS_NECESARIOS) {
    solicitudPendiente = true;
    preferences.putBool("pendiente", true);
    digitalWrite(PIN_LED, HIGH);
    ultimoReintentoSolicitud = ahora;
    Serial.println();
    Serial.println("========================================");
    Serial.println("SOLICITUD CONFIRMADA: 3/3 EN 5 s");
    Serial.println("Pendiente: el Panel debe decidir si dispensa.");
    Serial.println("========================================");
  }
}

void revisarVentana() {
  if (solicitudPendiente || contadorRasgunos == 0) return;
  if (millis() - inicioVentana >= VENTANA_FIJA_MS) {
    contadorRasgunos = 0;
    inicioVentana = 0;
    encolarLectura(0);
    Serial.println("Ventana fija de 5 segundos vencida; contador reiniciado.");
  }
}

void configurarHoraTLS() {
  static bool iniciado = false;
  static unsigned long proximaRevision = 0;
  if (!iniciado) {
    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
    iniciado = true;
  }
  if (horaLista || WiFi.status() != WL_CONNECTED || millis() < proximaRevision) return;
  proximaRevision = millis() + 1000;
  struct tm hora;
  if (getLocalTime(&hora, 50)) {
    horaLista = true;
    secureClient.setCACert(ROOT_CA);
    Serial.println("[TLS] Hora sincronizada; certificado de HiveMQ listo.");
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_RASGUNO, INPUT_PULLUP);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);

  preferences.begin("cama", false);
  secuenciaSalida = preferences.getULong64("txSeq", 0);
  ultimaSecuenciaAck = preferences.getULong64("ackSeq", 0);
  requestIdActual = preferences.getULong64("requestId", 0);
  solicitudPendiente = preferences.getBool("pendiente", false);
  contadorRasgunos = solicitudPendiente ? 3 : 0;
  digitalWrite(PIN_LED, solicitudPendiente ? HIGH : LOW);
  if (solicitudPendiente) encolarLectura(3);

  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(procesarAcuseMQTT);
  mqttClient.setBufferSize(256);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD, 6);
  ultimoIntentoWiFi = millis();
  Serial.println();
  Serial.println("========================================");
  Serial.println("COMEDERO IoT | SUMATIVA - NODO CAMA");
  Serial.println("Sensor: 3 rasguños en ventana fija de 5 s");
  Serial.println("Red: Wi-Fi · MQTT/TLS: puerto 8883");
  Serial.println("Estado: esperando conexión de red");
  Serial.println("========================================");
  Serial.println("[Wi-Fi] Conectando a la red de simulación...");
}

void loop() {
  conectarWiFiSiCorresponde();
  configurarHoraTLS();
  conectarMQTT();
  if (mqttClient.connected()) mqttClient.loop();

  unsigned long ahora = millis();
  revisarVentana();

  bool boton = digitalRead(PIN_RASGUNO);
  if (botonAnterior == HIGH && boton == LOW && ahora - ultimaPulsacion >= DEBOUNCE_MS) {
    ultimaPulsacion = ahora;
    iniciarRasgunos();
  }
  botonAnterior = boton;

  if (solicitudPendiente && cantidadCola == 0 &&
      ahora - ultimoReintentoSolicitud >= REINTENTO_SOLICITUD_MS) {
    encolarLectura(3);
    ultimoReintentoSolicitud = ahora;
  }
  despacharCola();
  delay(2);
}
