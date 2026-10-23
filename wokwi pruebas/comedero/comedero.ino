#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <WiFiServer.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <ESP32Servo.h>
#include <mbedtls/md.h>
#include <mbedtls/base64.h>
#include <time.h>
#include <string.h>
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

const int PIN_SERVO = 18;
const int PIN_LED = 2;
const int SERVO_CERRADO = 0;
const int SERVO_ABIERTO = 90;
const unsigned long TIEMPO_ABIERTO_MS = 1500;
const unsigned long COOLDOWN_DISPENSACION_MS = 10000;
const unsigned long TIEMPO_MAX_SIN_PANEL_MS = 60000;
const unsigned long VENTANA_RASGUNOS_MS = 5000;
const unsigned long INTERVALO_TELEMETRIA_MS = 2000;
const unsigned long INTERVALO_ACK_MQTT_MS = 2000;
const unsigned long REINTENTO_WIFI_MS = 8000;
const unsigned long REINTENTO_MQTT_MS = 4000;
const size_t MAX_LINEA_TCP = 256;
const size_t MAX_MENSAJE_MQTT = 220;

WiFiClientSecure secureClient;
PubSubClient mqttClient(secureClient);
WiFiServer servidorTCP(5050);
WiFiClient clientePanel;
Preferences preferences;
Servo servoComedero;

uint64_t ultimaSecuenciaCama = 0;
uint64_t secuenciaAckMQTT = 0;
uint64_t ultimoIdFinalizado = 0;
uint64_t idSolicitudActiva = 0;
uint64_t secuenciaPanelEntrada = 0;
uint64_t secuenciaPanelSalida = 0;
uint8_t rasgunosActuales = 0;
bool solicitudPendiente = false;
bool dispensando = false;
bool panelSessionActiva = false;
bool horaLista = false;
bool cooldownActivo = false;
unsigned long inicioCooldown = 0;
String noncePanel;
String ackPendiente;
uint64_t idAckPendiente = 0;
unsigned long inicioEntrega = 0;
unsigned long ultimoHeartbeatPanel = 0;
unsigned long ultimaTelemetria = 0;
unsigned long ultimoAckMQTT = 0;
unsigned long ultimoIntentoWiFi = 0;
unsigned long ultimoIntentoMQTT = 0;
unsigned long ultimaNotificacionMQTT = 0;
int ultimoEstadoMQTTReportado = 999;
unsigned long proximaLecturaTCP = 0;
unsigned long inicioVentanaRasgunos = 0;
bool ventanaRasgunosActiva = false;
char bufferTCP[MAX_LINEA_TCP + 1];
size_t largoTCP = 0;
bool lineaTCPExcedida = false;

String aTextoU64(uint64_t valor) {
  char texto[24];
  snprintf(texto, sizeof(texto), "%llu", static_cast<unsigned long long>(valor));
  return String(texto);
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

String estadoComedero() {
  return dispensando ? "DISPENSANDO" : "CERRADO";
}

bool enCooldownDispensacion() {
  return cooldownActivo && millis() - inicioCooldown < COOLDOWN_DISPENSACION_MS;
}

bool enviarTCP(const String& mensaje) {
  if (!panelSessionActiva || !clientePanel.connected() || noncePanel.isEmpty()) return false;
  uint64_t siguiente = secuenciaPanelSalida + 1;
  if (siguiente == 0) return false;
  String trama = noncePanel + ";" + aTextoU64(siguiente) + ";" + mensaje + "\n";
  if (trama.length() > MAX_LINEA_TCP) return false;
  size_t escritos = clientePanel.print(trama);
  if (escritos != trama.length()) return false;
  secuenciaPanelSalida = siguiente;
  return true;
}

void publicarEstadoRasgunosTCP() {
  enviarTCP(String("LECTURA;rasgunos;") + rasgunosActuales);
}

void revisarVencimientoRasgunos() {
  if (!ventanaRasgunosActiva || rasgunosActuales == 0 || rasgunosActuales >= 3 ||
      solicitudPendiente || dispensando) return;
  if (millis() - inicioVentanaRasgunos < VENTANA_RASGUNOS_MS) return;

  // Respaldo local: el Panel vuelve a 0/3 aunque el MQTT de reinicio de la Cama se pierda.
  rasgunosActuales = 0;
  ventanaRasgunosActiva = false;
  preferences.putUInt("rasgunos", 0);
  if (panelSessionActiva) publicarEstadoRasgunosTCP();
  Serial.println("[Timeout] Ventana de rasguños vencida en gateway; estado restablecido a 0/3.");
}

void publicarEstadoComederoTCP() {
  enviarTCP(String("LECTURA;comedero;") + estadoComedero());
}

void publicarAckTCP(const String& valor) {
  enviarTCP(String("ACK;comida;") + valor);
}

bool publicarAckMQTT() {
  if (!mqttClient.connected() || ackPendiente.isEmpty()) return false;
  uint64_t siguiente = secuenciaAckMQTT + 1;
  if (siguiente == 0) return false;

  String firmado = "R2|" + aTextoU64(siguiente) + "|" + aTextoU64(idAckPendiente) +
      "|ACK;comida;" + ackPendiente;
  char firma[65];
  if (!generarHMAC(firmado.c_str(), firma, sizeof(firma))) return false;
  String payload = firmado + "|" + firma;

  secuenciaAckMQTT = siguiente;
  preferences.putULong64("ackSeq", secuenciaAckMQTT);
  bool enviado = mqttClient.publish(MQTT_ACK_TOPIC, payload.c_str());
  if (enviado) {
    ultimoAckMQTT = millis();
    Serial.printf("Acuse MQTT pendiente de confirmación de cama: %s\n", ackPendiente.c_str());
  }
  return enviado;
}

bool publicarAcuseLecturaMQTT(uint64_t requestId, uint64_t secuenciaLectura) {
  if (!mqttClient.connected()) return false;
  uint64_t siguiente = secuenciaAckMQTT + 1;
  if (siguiente == 0) return false;

  String firmado = "R2|" + aTextoU64(siguiente) + "|" + aTextoU64(requestId) +
      "|ACK;lectura;" + aTextoU64(secuenciaLectura);
  char firma[65];
  if (!generarHMAC(firmado.c_str(), firma, sizeof(firma))) return false;
  String payload = firmado + "|" + firma;

  secuenciaAckMQTT = siguiente;
  preferences.putULong64("ackSeq", secuenciaAckMQTT);
  bool enviado = mqttClient.publish(MQTT_ACK_TOPIC, payload.c_str());
  if (enviado) {
    Serial.printf("[MQTT/TLS] Acuse enviado para lectura de Cama, secuencia %llu.\n",
        static_cast<unsigned long long>(secuenciaLectura));
  } else {
    Serial.printf("[MQTT/TLS] No se pudo enviar el acuse de lectura %llu; Cama reintentará.\n",
        static_cast<unsigned long long>(secuenciaLectura));
  }
  return enviado;
}

void guardarAckPendiente(uint64_t requestId, const String& valor) {
  idAckPendiente = requestId;
  ackPendiente = valor;
  preferences.putULong64("ackReqId", idAckPendiente);
  preferences.putString("ackValue", ackPendiente);
  ultimoAckMQTT = 0;
}

void cerrarActuador() {
  servoComedero.write(SERVO_CERRADO);
  digitalWrite(PIN_LED, LOW);
  dispensando = false;
  inicioEntrega = 0;
}

void finalizarSolicitud(const String& valor, bool avisarPanel) {
  uint64_t id = idSolicitudActiva;
  cerrarActuador();
  solicitudPendiente = false;
  rasgunosActuales = 0;
  preferences.putBool("pendiente", false);
  preferences.putUInt("rasgunos", 0);

  if (id > ultimoIdFinalizado) {
    ultimoIdFinalizado = id;
    preferences.putULong64("ultimoFin", ultimoIdFinalizado);
  }
  guardarAckPendiente(id, valor);

  publicarEstadoComederoTCP();
  if (avisarPanel) {
    publicarAckTCP(valor);
    publicarEstadoRasgunosTCP();
  }
  publicarAckMQTT();
  Serial.printf("Solicitud %s; actuador en CERRADO.\n", valor.c_str());
}

void cancelarSolicitudPorSeguridad(const char* motivo, bool avisarPanel) {
  bool habiaSolicitud = solicitudPendiente || dispensando;
  cerrarActuador();
  if (habiaSolicitud) {
    Serial.printf("Cancelación segura: %s\n", motivo);
    finalizarSolicitud("CANCELADA", avisarPanel);
  } else {
    publicarEstadoComederoTCP();
  }
}

void limpiarAckTrasConfirmacionCama(uint64_t requestId) {
  if (ackPendiente.isEmpty() || requestId != idAckPendiente) return;
  ackPendiente = "";
  idAckPendiente = 0;
  preferences.remove("ackValue");
  preferences.putULong64("ackReqId", 0);
  Serial.println("La cama confirmó el ACK MQTT con su lectura 0.");
}

void procesarLecturaMQTT(const String& linea) {
  int a = linea.indexOf('|');
  int b = a < 0 ? -1 : linea.indexOf('|', a + 1);
  int c = b < 0 ? -1 : linea.indexOf('|', b + 1);
  int d = c < 0 ? -1 : linea.indexOf('|', c + 1);
  if (a <= 0 || b <= a + 1 || c <= b + 1 || d <= c + 1 ||
      linea.indexOf('|', d + 1) >= 0 || linea.substring(0, a) != "R2") return;

  uint64_t secuencia = 0;
  uint64_t requestId = 0;
  if (!leerU64(linea.substring(a + 1, b), secuencia) ||
      !leerU64(linea.substring(b + 1, c), requestId)) {
    Serial.println("Lectura MQTT mal formada; descartada.");
    return;
  }

  String mensaje = linea.substring(c + 1, d);
  if (!mensaje.startsWith("LECTURA;rasgunos;")) return;
  String valor = mensaje.substring(String("LECTURA;rasgunos;").length());
  if (valor != "0" && valor != "1" && valor != "2" && valor != "3") return;
  if ((valor == "0" || valor == "1" || valor == "2" || valor == "3") && requestId == 0 && valor != "0") return;

  String firmado = linea.substring(0, d);
  char firmaEsperada[65];
  if (!generarHMAC(firmado.c_str(), firmaEsperada, sizeof(firmaEsperada))) return;
  String firmaRecibida = linea.substring(d + 1);
  if (!compararConstante(firmaEsperada, firmaRecibida.c_str())) {
    Serial.println("Lectura MQTT rechazada: HMAC inválido.");
    return;
  }

  if (secuencia <= ultimaSecuenciaCama) {
    Serial.printf("[MQTT/TLS] Lectura duplicada recibida; reenviando acuse de secuencia %llu.\n",
        static_cast<unsigned long long>(secuencia));
    publicarAcuseLecturaMQTT(requestId, secuencia);
    return;
  }

  ultimaSecuenciaCama = secuencia;
  preferences.putULong64("camaSeq", ultimaSecuenciaCama);
  uint8_t lecturaAnterior = rasgunosActuales;
  rasgunosActuales = static_cast<uint8_t>(valor.toInt());
  preferences.putUInt("rasgunos", rasgunosActuales);

  if (rasgunosActuales == 0 || rasgunosActuales >= 3) {
    ventanaRasgunosActiva = false;
  } else if (lecturaAnterior == 0 || !ventanaRasgunosActiva) {
    inicioVentanaRasgunos = millis();
    ventanaRasgunosActiva = true;
  }
  Serial.printf("[MQTT/TLS] Lectura de Cama aceptada: %u/3.\n",
      static_cast<unsigned int>(rasgunosActuales));

  if (rasgunosActuales == 3 && requestId > ultimoIdFinalizado && !dispensando) {
    idSolicitudActiva = requestId;
    solicitudPendiente = true;
    preferences.putULong64("activaId", idSolicitudActiva);
    preferences.putBool("pendiente", true);
    Serial.println();
    Serial.println("========================================");
    Serial.println("SOLICITUD DE ALIMENTO RECIBIDA");
    Serial.printf("ID de solicitud: %s\n", aTextoU64(requestId).c_str());
    Serial.println("Estado: pendiente de autorización del Panel.");
    Serial.println("========================================");
  } else if (rasgunosActuales < 3 && requestId == idSolicitudActiva && !dispensando) {
    solicitudPendiente = false;
    preferences.putBool("pendiente", false);
  }

  if (rasgunosActuales == 0) limpiarAckTrasConfirmacionCama(requestId);
  if (panelSessionActiva) publicarEstadoRasgunosTCP();
  publicarAcuseLecturaMQTT(requestId, secuencia);
}

void callbackMQTT(char* topic, byte* payload, unsigned int largo) {
  if (strcmp(topic, MQTT_TOPIC) != 0 || largo == 0 || largo >= MAX_MENSAJE_MQTT) return;
  char texto[MAX_MENSAJE_MQTT];
  memcpy(texto, payload, largo);
  texto[largo] = '\0';
  procesarLecturaMQTT(String(texto));
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

void conectarWiFiSiCorresponde() {
  static bool enlaceWiFiMostrado = false;
  if (WiFi.status() == WL_CONNECTED) {
    if (!enlaceWiFiMostrado) {
      Serial.printf("[Wi-Fi] Conectado. IP del gateway: %s\n", WiFi.localIP().toString().c_str());
      enlaceWiFiMostrado = true;
      Serial.println("[Gateway] TCP/5050 disponible para el Panel.");
    }
    return;
  }
  if (enlaceWiFiMostrado) {
    Serial.println("[Wi-Fi] Enlace perdido; el comedero queda en estado seguro.");
    enlaceWiFiMostrado = false;
  }
  if (mqttClient.connected()) mqttClient.disconnect();
  if (panelSessionActiva) {
    cancelarSolicitudPorSeguridad("se perdió Wi-Fi", false);
    clientePanel.stop();
    panelSessionActiva = false;
    noncePanel = "";
  } else if (dispensando || solicitudPendiente) {
    cancelarSolicitudPorSeguridad("se perdió Wi-Fi", false);
  }

  unsigned long ahora = millis();
  if (ahora - ultimoIntentoWiFi < REINTENTO_WIFI_MS) return;
  ultimoIntentoWiFi = ahora;
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
  Serial.printf("[MQTT] Nodo Comedero: %s (código %d).\n", descripcionEstadoMQTT(estado), estado);
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

  String idCliente = "ESP32_COMEDERO_" + String((uint32_t)ESP.getEfuseMac(), HEX);
  if (!mqttClient.connect(idCliente.c_str(), MQTT_USERNAME, MQTT_PASSWORD)) {
    // PubSubClient 2.8 no cierra el transporte cuando falla la apertura TCP/TLS.
    // Libera el socket para que el siguiente intento empiece con una conexión limpia.
    secureClient.stop();
    informarFalloMQTT();
    return false;
  }
  if (!mqttClient.subscribe(MQTT_TOPIC)) {
    mqttClient.disconnect();
    Serial.println("No se pudo suscribir al tópico de solicitudes.");
    return false;
  }
  ultimoEstadoMQTTReportado = 999;
  Serial.println();
  Serial.println("[MQTT/TLS] Conectado a HiveMQ; escuchando solicitudes de la cama.");
  return true;
}

bool mensajePanelPermitido(const String& mensaje) {
  return mensaje == "ACK;enlace;LATIDO" || mensaje == "CMD;comida;DISPENSAR";
}

void iniciarDispensacion() {
  if (!solicitudPendiente) {
    Serial.println("[Actuador] Orden ignorada: no hay una solicitud pendiente.");
    return;
  }
  if (dispensando) {
    Serial.println("[Actuador] Orden repetida ignorada: ya hay una entrega en curso.");
    return;
  }
  if (enCooldownDispensacion()) {
    unsigned long restante = COOLDOWN_DISPENSACION_MS - (millis() - inicioCooldown);
    Serial.printf("[Cooldown] Solicitud cancelada; espera %lu s antes de otra entrega.\n",
        (restante + 999) / 1000);
    finalizarSolicitud("CANCELADA", true);
    return;
  }
  if (estadoComedero() != "CERRADO" || !panelSessionActiva || !mqttClient.connected()) {
    Serial.println("[Actuador] Solicitud cancelada: falta un enlace activo o el actuador no está cerrado.");
    finalizarSolicitud("CANCELADA", true);
    return;
  }

  dispensando = true;
  inicioEntrega = millis();
  digitalWrite(PIN_LED, HIGH);
  servoComedero.write(SERVO_ABIERTO);
  publicarEstadoComederoTCP();
  Serial.println();
  Serial.println("[Actuador] Orden del Panel aceptada; abriendo la compuerta por 1,5 s.");
}

void procesarMensajePanel(const String& trama) {
  int a = trama.indexOf(';');
  int b = a < 0 ? -1 : trama.indexOf(';', a + 1);
  if (a <= 0 || b <= a + 1 || trama.indexOf(';', b + 1) < 0) return;
  if (trama.substring(0, a) != noncePanel) return;

  uint64_t secuencia = 0;
  if (!leerU64(trama.substring(a + 1, b), secuencia) || secuencia <= secuenciaPanelEntrada) return;
  String mensaje = trama.substring(b + 1);
  if (!mensajePanelPermitido(mensaje)) return;
  secuenciaPanelEntrada = secuencia;

  if (mensaje == "ACK;enlace;LATIDO") {
    ultimoHeartbeatPanel = millis();
    // Confirma al Panel que el gateway sigue vivo; la app vigila tráfico en ambas direcciones.
    enviarTCP("ACK;enlace;LATIDO");
    return;
  }
  if (mensaje == "CMD;comida;DISPENSAR") iniciarDispensacion();
}

void procesarByteTCP(char caracter) {
  if (caracter == '\r') return;
  if (caracter == '\n') {
    if (!lineaTCPExcedida && largoTCP > 0) {
      bufferTCP[largoTCP] = '\0';
      procesarMensajePanel(String(bufferTCP));
    }
    largoTCP = 0;
    lineaTCPExcedida = false;
    return;
  }
  if (largoTCP >= MAX_LINEA_TCP) {
    lineaTCPExcedida = true;
    return;
  }
  bufferTCP[largoTCP++] = caracter;
}

String crearNonce() {
  unsigned char aleatorio[16];
  unsigned char codificado[25];
  size_t largo = 0;
  esp_fill_random(aleatorio, sizeof(aleatorio));
  if (mbedtls_base64_encode(codificado, sizeof(codificado), &largo, aleatorio, sizeof(aleatorio)) != 0) return "";
  codificado[largo] = '\0';
  return String(reinterpret_cast<char*>(codificado));
}

void iniciarSesionPanel(WiFiClient nuevoCliente) {
  if (panelSessionActiva) {
    if (solicitudPendiente || dispensando) cancelarSolicitudPorSeguridad("se reemplazó el Panel", false);
    clientePanel.stop();
  }
  clientePanel = nuevoCliente;
  panelSessionActiva = true;
  noncePanel = crearNonce();
  secuenciaPanelEntrada = 0;
  secuenciaPanelSalida = 0;
  ultimoHeartbeatPanel = millis();
  largoTCP = 0;
  lineaTCPExcedida = false;

  if (noncePanel.isEmpty() || clientePanel.printf("HELLO;%s\n", noncePanel.c_str()) <= 0) {
    clientePanel.stop();
    panelSessionActiva = false;
    noncePanel = "";
    return;
  }
  Serial.println("Panel conectado al gateway TCP/5050.");
  publicarEstadoRasgunosTCP();
  publicarEstadoComederoTCP();
}

void aceptarClientePanel() {
  WiFiClient nuevoCliente = servidorTCP.accept();
  if (nuevoCliente) {
    nuevoCliente.setNoDelay(true);
    iniciarSesionPanel(nuevoCliente);
  }
}

void mantenerSesionPanel() {
  if (!panelSessionActiva) return;
  if (!clientePanel.connected()) {
    if (solicitudPendiente || dispensando) cancelarSolicitudPorSeguridad("Panel desconectado", false);
    clientePanel.stop();
    panelSessionActiva = false;
    noncePanel = "";
    Serial.println("Panel desconectado; comedero seguro.");
    return;
  }

  while (clientePanel.available()) procesarByteTCP(static_cast<char>(clientePanel.read()));
  if (!panelSessionActiva) return;

  if (millis() - ultimoHeartbeatPanel >= TIEMPO_MAX_SIN_PANEL_MS) {
    cancelarSolicitudPorSeguridad("timeout del latido del Panel", true);
    clientePanel.stop();
    panelSessionActiva = false;
    noncePanel = "";
    Serial.println("Panel sin latido por 60 s; sesión TCP cerrada.");
  }
}

void revisarActuador() {
  if (!dispensando || millis() - inicioEntrega < TIEMPO_ABIERTO_MS) return;
  cerrarActuador();
  inicioCooldown = millis();
  cooldownActivo = true;
  // El servo ya está en posición cerrada; solo ahora se emite el ACK de éxito.
  solicitudPendiente = false;
  rasgunosActuales = 0;
  preferences.putBool("pendiente", false);
  preferences.putUInt("rasgunos", 0);
  if (idSolicitudActiva > ultimoIdFinalizado) {
    ultimoIdFinalizado = idSolicitudActiva;
    preferences.putULong64("ultimoFin", ultimoIdFinalizado);
  }
  guardarAckPendiente(idSolicitudActiva, "DISPENSADA");
  publicarEstadoComederoTCP();
  publicarAckTCP("DISPENSADA");
  publicarEstadoRasgunosTCP();
  publicarAckMQTT();
  Serial.println("[Actuador] Compuerta cerrada; entrega confirmada.");
  Serial.println("[Cooldown] Nueva entrega bloqueada por hasta 10 s.");
}

void aceptarLecturaCamaCero() {
  // La limpieza del ACK se hace tras autenticar el paquete recibido.
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);
  servoComedero.setPeriodHertz(50);
  servoComedero.attach(PIN_SERVO, 500, 2400);
  servoComedero.write(SERVO_CERRADO);

  preferences.begin("comedero", false);
  ultimaSecuenciaCama = preferences.getULong64("camaSeq", 0);
  secuenciaAckMQTT = preferences.getULong64("ackSeq", 0);
  ultimoIdFinalizado = preferences.getULong64("ultimoFin", 0);
  idSolicitudActiva = preferences.getULong64("activaId", 0);
  idAckPendiente = preferences.getULong64("ackReqId", 0);
  ackPendiente = preferences.getString("ackValue", "");
  bool pendienteAntesDeReinicio = preferences.getBool("pendiente", false);
  solicitudPendiente = false;
  dispensando = false;
  preferences.putBool("pendiente", false);

  if (pendienteAntesDeReinicio && idSolicitudActiva > ultimoIdFinalizado) {
    ultimoIdFinalizado = idSolicitudActiva;
    preferences.putULong64("ultimoFin", ultimoIdFinalizado);
    guardarAckPendiente(idSolicitudActiva, "CANCELADA");
  }

  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(callbackMQTT);
  mqttClient.setBufferSize(256);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD, 6);
  servidorTCP.begin();
  servidorTCP.setNoDelay(true);
  ultimoIntentoWiFi = millis();
  Serial.println();
  Serial.println("========================================");
  Serial.println("COMEDERO IoT | SUMATIVA - NODO COMEDERO");
  Serial.println("Actuador iniciado en posición CERRADO");
  Serial.println("Gateway TCP: puerto 5050");
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

  if (WiFi.status() == WL_CONNECTED) {
    aceptarClientePanel();
    mantenerSesionPanel();
  }

  revisarActuador();
  revisarVencimientoRasgunos();

  unsigned long ahora = millis();
  if (panelSessionActiva && ahora - proximaLecturaTCP >= INTERVALO_TELEMETRIA_MS) {
    proximaLecturaTCP = ahora;
    publicarEstadoRasgunosTCP();
  }
  if (mqttClient.connected() && !ackPendiente.isEmpty() &&
      ahora - ultimoAckMQTT >= INTERVALO_ACK_MQTT_MS) {
    publicarAckMQTT();
  }

  delay(2);
}
