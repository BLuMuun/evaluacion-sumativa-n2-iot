package cl.inacap.comederoiot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import cl.inacap.comederoiot.datos.PerfilUsuario
import cl.inacap.comederoiot.ui.PantallaConexion
import cl.inacap.comederoiot.ui.PantallaHistorial
import cl.inacap.comederoiot.ui.PantallaLogin
import cl.inacap.comederoiot.ui.PantallaNodo
import cl.inacap.comederoiot.ui.PantallaPanel
import cl.inacap.comederoiot.ui.PantallaRegistro
import cl.inacap.comederoiot.seguridad.AutenticacionFirebase

class MainActivity : ComponentActivity() {
    private lateinit var pedirNotificaciones: ActivityResultLauncher<String>
    private lateinit var autenticacion: AutenticacionFirebase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        autenticacion = AutenticacionFirebase(this)
        pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Sesion.actualizarPermisoNotificaciones(this)
        }
        Notificaciones.crearCanal(this)

        setContent {
            val estado by Sesion.estado.collectAsState()
            var pantalla by rememberSaveable {
                mutableStateOf(if (autenticacion.haySesionActiva) "cargando" else "login")
            }
            var perfil by remember { mutableStateOf<PerfilUsuario?>(null) }
            var errorInicial by rememberSaveable { mutableStateOf<String?>(null) }

            fun aceptarPerfil(usuario: PerfilUsuario) {
                val lecturas = autenticacion.nuevoRepositorioLecturas()
                if (lecturas == null) {
                    errorInicial = "Firebase no está configurado. Revisa app/google-services.json."
                    autenticacion.cerrarSesion()
                    pantalla = "login"
                    return
                }
                perfil = usuario
                errorInicial = null
                Sesion.configurarUsuario(usuario, lecturas)
                pantalla = "conexion"
            }

            LaunchedEffect(Unit) {
                if (autenticacion.haySesionActiva) {
                    autenticacion.cargarPerfilActual { usuario, mensaje ->
                        if (usuario != null) aceptarPerfil(usuario)
                        else {
                            errorInicial = mensaje ?: "La sesión no está disponible. Inicia sesión nuevamente."
                            autenticacion.cerrarSesion()
                            pantalla = "login"
                        }
                    }
                }
            }

            MaterialTheme(colorScheme = lightColorScheme()) {
                BackHandler(enabled = pantalla == "nodo" || pantalla == "panel") {
                    Sesion.desconectar()
                    pantalla = "conexion"
                }
                BackHandler(enabled = pantalla == "historial") { pantalla = "panel" }
                BackHandler(enabled = pantalla == "registro") { pantalla = "login" }

                Scaffold(modifier = Modifier.fillMaxSize()) { relleno ->
                    val contenido = Modifier.padding(relleno)
                    when (pantalla) {
                        "cargando" -> PantallaCargando(contenido)
                        "login" -> PantallaLogin(
                            firebaseConfigurado = autenticacion.estaConfigurada,
                            onIniciarSesion = autenticacion::iniciarSesion,
                            onLoginCorrecto = ::aceptarPerfil,
                            onCrearCuenta = { pantalla = "registro" },
                            avisoInicial = errorInicial,
                            modifier = contenido
                        )
                        "registro" -> PantallaRegistro(
                            firebaseConfigurado = autenticacion.estaConfigurada,
                            onRegistrar = autenticacion::registrar,
                            onRegistroCorrecto = ::aceptarPerfil,
                            onVolver = { pantalla = "login" },
                            modifier = contenido
                        )
                        "nodo" -> PantallaNodo(
                            estado = estado,
                            onSimularRasgun = Sesion::simularRasgun,
                            onDesconectar = {
                                Sesion.desconectar()
                                pantalla = "conexion"
                            },
                            modifier = contenido
                        )
                        "panel" -> PantallaPanel(
                            estado = estado,
                            onDispensar = Sesion::dispensarComida,
                            onAbrirHistorial = { pantalla = "historial" },
                            onVolverAConexion = {
                                Sesion.desconectar()
                                pantalla = "conexion"
                            },
                            modifier = contenido
                        )
                        "historial" -> perfil?.let {
                            autenticacion.nuevoRepositorioLecturas()?.let { repo ->
                                PantallaHistorial(
                                    repositorio = repo,
                                    onVolver = { pantalla = "panel" },
                                    modifier = contenido
                                )
                            } ?: PantallaCargando(contenido)
                        } ?: PantallaCargando(contenido)
                        "conexion" -> PantallaConexion(
                            estado = estado,
                            correoUsuario = perfil?.correo.orEmpty(),
                            onIniciarNodo = { codigo ->
                                val error = Sesion.iniciarNodo(this, codigo)
                                if (error == null) pantalla = "nodo"
                                error
                            },
                            onConectarPanel = { ip, codigo, modo ->
                                val error = Sesion.conectarPanel(this, ip, codigo, modo)
                                if (error == null) pantalla = "panel"
                                error
                            },
                            onSolicitarNotificaciones = ::solicitarPermisoNotificaciones,
                            onCerrarSesion = {
                                Sesion.limpiarUsuario()
                                autenticacion.cerrarSesion()
                                perfil = null
                                pantalla = "login"
                            },
                            modifier = contenido
                        )
                        else -> PantallaLogin(
                            firebaseConfigurado = autenticacion.estaConfigurada,
                            onIniciarSesion = autenticacion::iniciarSesion,
                            onLoginCorrecto = ::aceptarPerfil,
                            onCrearCuenta = { pantalla = "registro" },
                            avisoInicial = errorInicial,
                            modifier = contenido
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Sesion.actualizarPermisoNotificaciones(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Sesion.actualizarPermisoNotificaciones(this)
    }

    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < 33) {
            Sesion.actualizarPermisoNotificaciones(this)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) Sesion.actualizarPermisoNotificaciones(this)
        else pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@androidx.compose.runtime.Composable
private fun PantallaCargando(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}
