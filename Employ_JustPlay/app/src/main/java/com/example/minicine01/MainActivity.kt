package com.example.minicine01

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.Gravity
import androidx.cardview.widget.CardView
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var layoutTablero: View
    private lateinit var layoutRegistro: View
    private lateinit var layoutReservasGenerales: View
    private lateinit var layoutVetados: View
    private lateinit var tvTituloToolbar: TextView
    private lateinit var tvBadgeOffline: TextView

    private var estaConectadoInternet: Boolean = true
    private var sesionIniciada: Boolean = false

    private lateinit var tvMinicineOcupadas: TextView
    private lateinit var tvAutitosOcupados: TextView
    private lateinit var tvJustDanceOcupados: TextView

    private lateinit var llContenedorReservasDash: LinearLayout
    private val handlerRelojes = Handler(Looper.getMainLooper())
    private lateinit var runnableRelojes: Runnable

    private var salasMinicine: List<Sala> = emptyList()
    private var salasAutitos: List<Sala> = emptyList()
    private var salasJustDance: List<Sala> = emptyList()

    private val historialCompleto = mutableListOf<RegistroEntry>()
    private val historialFiltrado = mutableListOf<RegistroEntry>()
    private lateinit var registroAdapter: RegistroAdapter

    private val listaReservasGlobales = mutableListOf<Reserva>()
    private val reservasMinicineList = mutableListOf<Reserva>()
    private val reservasAutitosList = mutableListOf<Reserva>()
    private val reservasDanceList = mutableListOf<Reserva>()

    private lateinit var adapterResMinicine: ReservasAdapter
    private lateinit var adapterResAutitos: ReservasAdapter
    private lateinit var adapterResDance: ReservasAdapter

    private val listaVetadosGlobal = mutableListOf<ClienteVetado>()
    private lateinit var vetadosAdapter: VetadosAdapter

    // ESTADO FILTROS REGISTRO
    private var fechaFiltroSeleccionada: String = ""
    private var horaInicioFiltro: String? = null // ej: "14:00"
    private var horaFinFiltro: String? = null    // ej: "18:30"

    // NOTIFICACIONES DE TIEMPO
    private val CHANNEL_ID = "justplay_timer_channel"
    private val salasNotificadasFin = mutableSetOf<Int>()

    // REFERENCIA A FIREBASE Y GESTORES SOLID
    private lateinit var database: DatabaseReference
    private lateinit var roomAuditManager: RoomAuditManager
    private lateinit var productAuditManager: ProductAuditManager
    private lateinit var egresosManager: EgresosManager
    private lateinit var qrValidationHelper: QrPaymentValidationHelper
    private lateinit var otgScanner: OtgBarcodeScannerHelper

    private var isFabMenuExpanded = false
    private lateinit var fabOverlay: View
    private lateinit var llFabVenta: LinearLayout
    private lateinit var llFabReservas: LinearLayout
    private lateinit var fabMain: FloatingActionButton

    // FOTOS: LAUNCHERS DE CÁMARA Y GALERÍA
    private var tempCameraUri: Uri? = null
    private var imageResultCallback: ((String?) -> Unit)? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && tempCameraUri != null) {
            val path = ImageUtils.comprimirYGuardarImagen(this, tempCameraUri!!)
            imageResultCallback?.invoke(path)
        } else {
            imageResultCallback?.invoke(null)
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val path = ImageUtils.comprimirYGuardarImagen(this, uri)
            imageResultCallback?.invoke(path)
        } else {
            imageResultCallback?.invoke(null)
        }
    }

    private fun capturarFotoCamara(onResult: (String?) -> Unit) {
        imageResultCallback = onResult
        try {
            val uri = ImageUtils.createTempCameraUri(this)
            tempCameraUri = uri
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                putExtra("android.intent.extras.CAMERA_FACING", 0) // 0 = BACK
                putExtra("android.intent.extras.CAMERA_FACING_BACK", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 0)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
                putExtra("camerafacing", "back")
                putExtra("previous_mode", "front")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            takePictureLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun seleccionarFotoGaleria(onResult: (String?) -> Unit) {
        imageResultCallback = onResult
        try {
            pickGalleryLauncher.launch("image/*")
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir galería: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarOpcionesFoto(onResult: (String?) -> Unit) {
        val opciones = arrayOf("📷 Tomar Foto (Cámara)", "🖼️ Elegir de Galería")
        AlertDialog.Builder(this)
            .setTitle("Adjuntar Comprobante")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> capturarFotoCamara(onResult)
                    1 -> seleccionarFotoGaleria(onResult)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> onResult(null) }
            .show()
    }

    private fun capturarComprobantesParaSolicitud(solicitudId: String, fotosAcumuladas: String? = null) {
        mostrarOpcionesFoto { path ->
            if (path != null) {
                val fotosActualizadas = ImageUtils.agregarComprobante(fotosAcumuladas, path)
                val listaFotos = ImageUtils.separarRutasComprobantes(fotosActualizadas)

                AlertDialog.Builder(this)
                    .setTitle("📸 Comprobante (${listaFotos.size} foto${if (listaFotos.size > 1) "s" else ""})")
                    .setMessage("Foto adjuntada correctamente.\n\n¿Deseas tomar o adjuntar otra foto adicional o enviar ya a la PC?")
                    .setPositiveButton("✅ Enviar a PC (${listaFotos.size})") { _, _ ->
                        qrValidationHelper.aprobarSolicitud(solicitudId, fotosActualizadas)
                    }
                    .setNeutralButton("➕ Agregar Otra Foto") { _, _ ->
                        capturarComprobantesParaSolicitud(solicitudId, fotosActualizadas)
                    }
                    .setNegativeButton("Cancelar Todo") { _, _ -> }
                    .setCancelable(false)
                    .show()
            } else if (!fotosAcumuladas.isNullOrBlank()) {
                val listaFotos = ImageUtils.separarRutasComprobantes(fotosAcumuladas)
                AlertDialog.Builder(this)
                    .setTitle("📸 Comprobantes Pendientes")
                    .setMessage("¿Deseas enviar a la PC las ${listaFotos.size} foto(s) ya adjuntadas?")
                    .setPositiveButton("Sí, enviar (${listaFotos.size})") { _, _ ->
                        qrValidationHelper.aprobarSolicitud(solicitudId, fotosAcumuladas)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            // INICIALIZAR CANAL DE NOTIFICACIONES Y PERMISOS
            crearCanalNotificaciones()
            solicitarPermisoNotificaciones()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // INICIALIZAR FECHA HOY POR DEFECTO PARA EL REGISTRO
        fechaFiltroSeleccionada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        horaInicioFiltro = null
        horaFinFiltro = null

        // INICIALIZAR FIREBASE Y GESTORES SOLID
        database = FirebaseDatabase.getInstance().reference
        roomAuditManager = RoomAuditManager(this, database)
        productAuditManager = ProductAuditManager(this, database)
        egresosManager = EgresosManager(this, database)
        qrValidationHelper = QrPaymentValidationHelper(this, database) { solicitudId ->
            capturarComprobantesParaSolicitud(solicitudId)
        }
        qrValidationHelper.iniciarEscucha()
        otgScanner = OtgBarcodeScannerHelper(object : IBarcodeScanListener {
            override fun onBarcodeScanned(barcode: String) {
                mostrarDialogoVentaOTGEmergencia(barcode)
            }
        })

        drawerLayout = findViewById(R.id.drawerLayout)
        layoutTablero = findViewById(R.id.layoutTablero)
        layoutRegistro = findViewById(R.id.layoutRegistro)
        layoutReservasGenerales = findViewById(R.id.layoutReservasGenerales)
        layoutVetados = findViewById(R.id.layoutVetados)
        tvTituloToolbar = findViewById(R.id.tvTituloToolbar)
        tvBadgeOffline = findViewById(R.id.tvBadgeOffline)

        tvMinicineOcupadas = findViewById(R.id.tvMinicineOcupadas)
        tvAutitosOcupados = findViewById(R.id.tvAutitosOcupados)
        tvJustDanceOcupados = findViewById(R.id.tvJustDanceOcupados)
        llContenedorReservasDash = findViewById(R.id.llContenedorReservasDash)

        fabOverlay = findViewById(R.id.fabOverlay)
        llFabVenta = findViewById(R.id.llFabVenta)
        llFabReservas = findViewById(R.id.llFabReservas)
        fabMain = findViewById(R.id.fabMain)

        try {
            // INICIALIZAR MONITOREO DE PRESENCIA Y SEGURIDAD
            PresenciaManager.iniciarMonitoreoPresencia(this, "Employ_JustPlay", "Empleado")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ELEMENTOS DE SEGURIDAD Y LOGIN DE PIN
        val layoutLoginPin = findViewById<View>(R.id.layoutLoginPin)
        val tvLoginDispositivoInfo = findViewById<TextView>(R.id.tvLoginDispositivoInfo)
        val tvLoginVigenciaAcceso = findViewById<TextView>(R.id.tvLoginVigenciaAcceso)
        val etLoginPin = findViewById<EditText>(R.id.etLoginPin)
        val tvLoginMensajeError = findViewById<TextView>(R.id.tvLoginMensajeError)
        val btnLoginIngresar = findViewById<Button>(R.id.btnLoginIngresar)

        val layoutBloqueo = findViewById<View>(R.id.layoutBloqueoDispositivo)
        val tvTituloBloqueo = findViewById<TextView>(R.id.tvTituloBloqueo)
        val tvMotivoBloqueo = findViewById<TextView>(R.id.tvMotivoBloqueo)
        val tvDispositivoInfoBloqueo = findViewById<TextView>(R.id.tvDispositivoInfoBloqueo)
        val btnReintentarPin = findViewById<Button>(R.id.btnReintentarPin)

        fun refrescarInfoLogin() {
            val user = PresenciaManager.obtenerUsuarioActualModel()
            tvLoginDispositivoInfo.text = "📱 ${user.nombre} (${user.id})"
            tvLoginVigenciaAcceso.text = "⏳ Acceso: ${user.obtenerTiempoRestanteTexto()}"
            tvDispositivoInfoBloqueo.text = "Dispositivo: ${user.nombre} (${user.id})"
        }

        // Estado inicial de sesión: si ya estaba autenticado, entrar directo
        val userInicial = PresenciaManager.obtenerUsuarioActualModel()
        val estabaAutenticado = PresenciaManager.estaSesionAutenticada(this)

        if (estabaAutenticado && !userInicial.bloqueado && !userInicial.estaExpirado()) {
            sesionIniciada = true
            layoutLoginPin.visibility = View.GONE
            layoutBloqueo.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            if (layoutTablero.visibility == View.VISIBLE && estaConectadoInternet) {
                fabMain.visibility = View.VISIBLE
            }
        } else if (estabaAutenticado && userInicial.bloqueado) {
            sesionIniciada = true
            layoutLoginPin.visibility = View.GONE
            layoutBloqueo.visibility = View.VISIBLE
            tvTituloBloqueo.text = "🚫 Acceso Bloqueado"
            tvMotivoBloqueo.text = userInicial.motivoBloqueo
            fabMain.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else if (estabaAutenticado && userInicial.estaExpirado()) {
            sesionIniciada = true
            layoutLoginPin.visibility = View.GONE
            layoutBloqueo.visibility = View.VISIBLE
            tvTituloBloqueo.text = "⏰ Acceso Expirado"
            tvMotivoBloqueo.text = "Tu tiempo de conexión autorizado (${userInicial.tipoTiempoAcceso}) ha finalizado. Solicita al Administrador reactivar tu sesión."
            fabMain.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else {
            sesionIniciada = false
            layoutLoginPin.visibility = View.VISIBLE
            layoutBloqueo.visibility = View.GONE
            fabMain.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }

        refrescarInfoLogin()

        btnLoginIngresar.setOnClickListener {
            val pinIngresado = etLoginPin.text.toString().trim()
            val user = PresenciaManager.obtenerUsuarioActualModel()
            val pinConfigurado = user.pin.trim().ifEmpty { "0001" }

            // Permitir PIN configurado, PIN por defecto o PIN maestro de emergencia (0001, 1234, 9999)
            val esPinValido = pinIngresado == pinConfigurado || 
                              pinIngresado == "0001" || 
                              pinIngresado == "1234" || 
                              pinIngresado == "9999"

            if (!esPinValido) {
                tvLoginMensajeError.visibility = View.VISIBLE
                tvLoginMensajeError.text = "PIN incorrecto. (PIN inicial por defecto: 0001)"
                return@setOnClickListener
            }

            if (user.bloqueado && pinIngresado != "9999") {
                layoutLoginPin.visibility = View.GONE
                layoutBloqueo.visibility = View.VISIBLE
                tvTituloBloqueo.text = "🚫 Acceso Bloqueado"
                tvMotivoBloqueo.text = user.motivoBloqueo
                return@setOnClickListener
            }

            if (user.estaExpirado() && pinIngresado != "9999") {
                layoutLoginPin.visibility = View.GONE
                layoutBloqueo.visibility = View.VISIBLE
                tvTituloBloqueo.text = "⏰ Acceso Expirado"
                tvMotivoBloqueo.text = "Tu tiempo de conexión autorizado (${user.tipoTiempoAcceso}) ha finalizado. Solicita al Administrador reactivar tu sesión."
                return@setOnClickListener
            }

            // Inicio de sesión exitoso
            sesionIniciada = true
            PresenciaManager.guardarSesionAutenticada(this, true)
            layoutLoginPin.visibility = View.GONE
            layoutBloqueo.visibility = View.GONE
            tvLoginMensajeError.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            navegarAPantalla("Just Play (Tablero)")
            if (estaConectadoInternet) {
                fabMain.visibility = View.VISIBLE
            }
            Toast.makeText(this, "¡Acceso concedido!", Toast.LENGTH_SHORT).show()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(etLoginPin.windowToken, 0)
        }

        btnReintentarPin.setOnClickListener {
            layoutBloqueo.visibility = View.GONE
            layoutLoginPin.visibility = View.VISIBLE
            etLoginPin.text.clear()
            tvLoginMensajeError.visibility = View.GONE
            refrescarInfoLogin()
        }

        // INICIALIZAR MONITOREO DE CAMBIOS EN TIEMPO REAL DEL USUARIO
        PresenciaManager.onEstadoCambioListener = { user ->
            runOnUiThread {
                refrescarInfoLogin()
                if (sesionIniciada) {
                    if (user.bloqueado) {
                        layoutBloqueo.visibility = View.VISIBLE
                        tvTituloBloqueo.text = "🚫 Acceso Bloqueado"
                        tvMotivoBloqueo.text = user.motivoBloqueo
                        fabMain.visibility = View.GONE
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    } else if (user.estaExpirado()) {
                        layoutBloqueo.visibility = View.VISIBLE
                        tvTituloBloqueo.text = "⏰ Acceso Expirado"
                        tvMotivoBloqueo.text = "Tu tiempo de conexión autorizado (${user.tipoTiempoAcceso}) ha finalizado. Solicita al Administrador reactivar tu sesión."
                        fabMain.visibility = View.GONE
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    } else {
                        layoutBloqueo.visibility = View.GONE
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                        if (layoutTablero.visibility == View.VISIBLE && estaConectadoInternet) {
                            fabMain.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        // Monitorear conectividad con Firebase Realtime Database
        database.root.child(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                actualizarEstadoConexion(connected)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Monitorear conectividad a nivel de red Android
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread { actualizarEstadoConexion(true) }
                }
                override fun onLost(network: Network) {
                    runOnUiThread { actualizarEstadoConexion(false) }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fabMain.setOnClickListener {
            if (!estaConectadoInternet) {
                Toast.makeText(this, "⚠️ Modo sin conexión: Estás en modo de solo lectura. Conéctate a internet para registrar ventas o reservas.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            toggleFabMenu()
        }
        fabOverlay.setOnClickListener { toggleFabMenu() }

        findViewById<FloatingActionButton>(R.id.fabVenta).setOnClickListener {
            if (!estaConectadoInternet) {
                Toast.makeText(this, "⚠️ Sin conexión: No se pueden registrar ventas sin internet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleFabMenu()
            mostrarDialogoVentaDirecta()
        }

        findViewById<FloatingActionButton>(R.id.fabReservas).setOnClickListener {
            toggleFabMenu()
            navegarAPantalla("Reservas")
        }

        runnableRelojes = object : Runnable {
            override fun run() {
                actualizarRelojesReservas()
                verificarTiemposSalas()

                // Verificar si el tiempo límite del empleado expiró
                if (sesionIniciada) {
                    val user = PresenciaManager.obtenerUsuarioActualModel()
                    if (user.estaExpirado() || user.bloqueado) {
                        layoutBloqueo.visibility = View.VISIBLE
                        tvTituloBloqueo.text = if (user.bloqueado) "🚫 Acceso Bloqueado" else "⏰ Acceso Expirado"
                        tvMotivoBloqueo.text = if (user.bloqueado) user.motivoBloqueo else "Tu tiempo de conexión autorizado (${user.tipoTiempoAcceso}) ha finalizado."
                        fabMain.visibility = View.GONE
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }
                }

                handlerRelojes.postDelayed(this, 1000)
            }
        }
        handlerRelojes.post(runnableRelojes)

        val btnMenuHamburguesa = findViewById<TextView>(R.id.btnMenuHamburguesa)
        btnMenuHamburguesa.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.menu.add(0, 1, 0, "Just Play (Tablero)").setIcon(android.R.drawable.ic_menu_sort_by_size)
        navigationView.menu.add(0, 2, 1, "Registro").setIcon(android.R.drawable.ic_menu_recent_history)
        navigationView.menu.add(0, 3, 2, "Reservas").setIcon(android.R.drawable.ic_menu_today)
        navigationView.menu.add(0, 4, 3, "Clientes Vetados").setIcon(android.R.drawable.ic_menu_close_clear_cancel)
        navigationView.menu.add(0, 5, 4, "📦 Catálogo de Productos").setIcon(android.R.drawable.ic_menu_agenda)
        navigationView.menu.add(0, 6, 5, "💸 Salidas de Caja / Insumos").setIcon(android.R.drawable.ic_menu_save)
        navigationView.menu.add(0, 7, 6, "🔒 Bloquear Pantalla").setIcon(android.R.drawable.ic_lock_lock)
        navigationView.menu.add(0, 8, 7, "🔄 Buscar Actualizaciones").setIcon(android.R.drawable.ic_popup_sync)

        navigationView.setNavigationItemSelectedListener { item ->
            if (item.itemId == 5 || item.title.toString().contains("Catálogo")) {
                mostrarDialogoCatalogoProductosEmpleado()
            } else if (item.itemId == 6 || item.title.toString().contains("Salidas de Caja")) {
                egresosManager.mostrarDialogoGestionEgresos(
                    esAdmin = false,
                    usuarioNombre = PresenciaManager.obtenerNombreUsuario(this),
                    usuarioRol = "Empleado"
                )
            } else if (item.itemId == 7 || item.title.toString().contains("Bloquear")) {
                sesionIniciada = false
                PresenciaManager.guardarSesionAutenticada(this, false)
                layoutLoginPin.visibility = View.VISIBLE
                etLoginPin.text.clear()
                tvLoginMensajeError.visibility = View.GONE
                fabMain.visibility = View.GONE
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                refrescarInfoLogin()
            } else if (item.itemId == 8 || item.title.toString().contains("Actualizaciones")) {
                AppUpdateManager.verificarActualizacion(this, "Employ_JustPlay", mostrarToastSiNoHay = true)
            } else {
                navegarAPantalla(item.title.toString())
            }
            drawerLayout.closeDrawers()
            true
        }

        // COMPROBADOR AUTOMÁTICO DE ACTUALIZACIONES AL INICIAR (In-app updater)
        try {
            AppUpdateManager.verificarActualizacion(this, "Employ_JustPlay")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val rvRegistro = findViewById<RecyclerView>(R.id.rvRegistro)
        rvRegistro.layoutManager = LinearLayoutManager(this)
        registroAdapter = RegistroAdapter(historialFiltrado) { registroAEliminar ->
            registroAEliminar.eliminado = true
            registroAEliminar.fechaEliminacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            registroAEliminar.eliminadoPor = PresenciaManager.obtenerNombreUsuario(this)
            database.child("auditoria_registros").child(registroAEliminar.idFirebase).setValue(registroAEliminar)
            database.child("registros").child(registroAEliminar.idFirebase).removeValue()
        }
        rvRegistro.adapter = registroAdapter

        configurarAdaptersReservas()

        vetadosAdapter = VetadosAdapter(listaVetadosGlobal,
            onEditar = { vetado ->
                if (!estaConectadoInternet) {
                    Toast.makeText(this, "⚠️ Sin conexión: No se pueden editar clientes sin internet.", Toast.LENGTH_SHORT).show()
                } else {
                    mostrarDialogoVetarCliente(vetado)
                }
            },
            onEliminar = { vetado ->
                if (!estaConectadoInternet) {
                    Toast.makeText(this, "⚠️ Sin conexión: No se pueden eliminar clientes sin internet.", Toast.LENGTH_SHORT).show()
                } else {
                    database.child("vetados").child(vetado.celular).removeValue()
                }
            }
        )
        val rvVetados = findViewById<RecyclerView>(R.id.rvVetados)
        rvVetados.layoutManager = LinearLayoutManager(this)
        rvVetados.adapter = vetadosAdapter

        findViewById<FloatingActionButton>(R.id.fabAgregarVetado).setOnClickListener {
            if (!estaConectadoInternet) {
                Toast.makeText(this, "⚠️ Sin conexión: No se pueden agregar vetados sin internet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            mostrarDialogoVetarCliente(null)
        }
        findViewById<FloatingActionButton>(R.id.fabAgregarReservaPantalla).setOnClickListener {
            if (!estaConectadoInternet) {
                Toast.makeText(this, "⚠️ Sin conexión: No se pueden agregar reservas sin internet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            mostrarDialogoNuevaReserva()
        }

        // CHECKBOXES DE FILTRO TICKEABLES EN REGISTRO
        val checkboxes = listOf(
            findViewById<CheckBox>(R.id.cbFiltroCine),
            findViewById<CheckBox>(R.id.cbFiltroAutitos),
            findViewById<CheckBox>(R.id.cbFiltroDance),
            findViewById<CheckBox>(R.id.cbFiltroMostrador),
            findViewById<CheckBox>(R.id.cbFiltroEfectivo),
            findViewById<CheckBox>(R.id.cbFiltroQR),
            findViewById<CheckBox>(R.id.cbFiltroEgresos)
        )
        checkboxes.forEach { cb ->
            cb?.setOnCheckedChangeListener { _, _ -> aplicarFiltrosRegistro() }
        }

        // BOTONES DE FECHA Y RANGO DE HORAS EN REGISTRO
        findViewById<Button>(R.id.btnFiltroFecha).setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                fechaFiltroSeleccionada = String.format("%02d/%02d/%d", day, month + 1, year)
                aplicarFiltrosRegistro()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        findViewById<Button>(R.id.btnFiltroHoraInicio).setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                horaInicioFiltro = String.format("%02d:%02d", hourOfDay, minute)
                aplicarFiltrosRegistro()
            }, cal.get(Calendar.HOUR_OF_DAY), 0, true).show()
        }

        findViewById<Button>(R.id.btnFiltroHoraFin).setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                horaFinFiltro = String.format("%02d:%02d", hourOfDay, minute)
                aplicarFiltrosRegistro()
            }, cal.get(Calendar.HOUR_OF_DAY), 59, true).show()
        }

        findViewById<Button>(R.id.btnLimpiarHoras).setOnClickListener {
            horaInicioFiltro = null
            horaFinFiltro = null
            aplicarFiltrosRegistro()
        }

        findViewById<Button>(R.id.btnLimpiarFecha).setOnClickListener {
            fechaFiltroSeleccionada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            horaInicioFiltro = null
            horaFinFiltro = null
            aplicarFiltrosRegistro()
        }

        findViewById<Button>(R.id.btnExportar).setOnClickListener {
            if (historialFiltrado.isEmpty()) {
                Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            } else {
                exportarCSV(historialFiltrado)
            }
        }

        // INICIAR LA ESCUCHA DE LA NUBE
        sincronizarConFirebase()
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerRelojes.removeCallbacksAndMessages(null)
        try {
            qrValidationHelper.detenerEscucha()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarmas de Tiempo Just Play"
            val descriptionText = "Notificaciones cuando finaliza el tiempo de una sala o auto"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun verificarTiemposSalas() {
        val todasLasSalas = salasMinicine + salasAutitos + salasJustDance
        val ahora = System.currentTimeMillis()

        for (sala in todasLasSalas) {
            if (sala.estado == EstadoSala.OCUPADA && sala.endTimeMillis != null) {
                if (ahora >= sala.endTimeMillis!!) {
                    if (!salasNotificadasFin.contains(sala.id)) {
                        salasNotificadasFin.add(sala.id)
                        mostrarNotificacionTiempoTerminado(sala)
                    }
                } else {
                    salasNotificadasFin.remove(sala.id)
                }
            } else {
                salasNotificadasFin.remove(sala.id)
            }
        }
    }

    private fun mostrarNotificacionTiempoTerminado(sala: Sala) {
        val clienteInfo = if (!sala.cliente.isNullOrEmpty()) " (${sala.cliente})" else ""
        val mensajeTexto = "¡Se acabó el tiempo en ${sala.nombre}$clienteInfo! Ya es hora de liberar o cobrar."

        // 1. Toast visual en pantalla
        Toast.makeText(this, "⏰ ¡TIEMPO FINALIZADO!\n$mensajeTexto", Toast.LENGTH_LONG).show()

        // 2. Vibración y sonido
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
            } else {
                vibrator.vibrate(800)
            }
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(this, alarmSound)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Notificación emergente del sistema Android (Heads-up)
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                sala.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ ¡Tiempo Finalizado: ${sala.nombre}!")
                .setContentText("Ya es hora de ${sala.nombre}$clienteInfo.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(mensajeTexto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(sala.id, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sincronizarConFirebase() {
        // Habilitar sincronización local offline en disco
        try {
            database.child("salas").keepSynced(true)
            database.child("registros").keepSynced(true)
            database.child("reservas").keepSynced(true)
            database.child("vetados").keepSynced(true)
            database.child("config_precios").keepSynced(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 0. ESCUCHAR CONFIGURACIÓN DE PRECIOS BASE
        database.child("config_precios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    BasePriceConfig.updateFromSnapshot(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 1. ESCUCHAR SALAS EN TIEMPO REAL
        database.child("salas").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempSalas = mutableListOf<Sala>()
                for (snap in snapshot.children) {
                    val s = snap.getValue(Sala::class.java)
                    if (s != null) {
                        s.idFirebase = snap.key ?: ""
                        if (s.id == 0) s.id = snap.key?.toIntOrNull() ?: 0
                        tempSalas.add(s)
                    }
                }

                salasMinicine = tempSalas.filter { it.id < 100 && !it.nombre.contains("Car", ignoreCase = true) && !it.nombre.contains("Dance", ignoreCase = true) }.sortedBy { it.id }
                salasAutitos = tempSalas.filter { it.id in 101..199 || it.nombre.contains("Car", ignoreCase = true) || it.nombre.contains("Auto", ignoreCase = true) }.sortedBy { it.id }
                salasJustDance = tempSalas.filter { it.id >= 200 || it.nombre.contains("Dance", ignoreCase = true) }.sortedBy { it.id }

                configurarRecyclerView(findViewById(R.id.rvSalasMinicine), salasMinicine)
                configurarRecyclerView(findViewById(R.id.rvSalasAutitos), salasAutitos)
                configurarRecyclerView(findViewById(R.id.rvSalasJustDance), salasJustDance)

                actualizarContadorOcupacion()

                for (sala in tempSalas) {
                    if (sala.estado == EstadoSala.OCUPADA && sala.endTimeMillis != null && sala.endTimeMillis!! > System.currentTimeMillis()) {
                        AlarmUtils.programarAlarmaSala(this@MainActivity, sala.id, sala.nombre, sala.cliente, sala.endTimeMillis!!)
                    } else {
                        AlarmUtils.cancelarAlarmaSala(this@MainActivity, sala.id)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. ESCUCHAR HISTORIAL DE VENTAS
        database.child("registros").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historialCompleto.clear()
                for (snap in snapshot.children) {
                    val reg = snap.getValue(RegistroEntry::class.java)
                    if (reg != null) {
                        reg.idFirebase = snap.key ?: ""
                        historialCompleto.add(reg)
                    }
                }
                aplicarFiltrosRegistro()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. ESCUCHAR RESERVAS
        database.child("reservas").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaReservasGlobales.clear()
                reservasMinicineList.clear()
                reservasAutitosList.clear()
                reservasDanceList.clear()

                for (snap in snapshot.children) {
                    val res = snap.getValue(Reserva::class.java)
                    if (res != null) {
                        res.idFirebase = snap.key ?: ""
                        listaReservasGlobales.add(res)
                        when (res.tipoSala) {
                            "Minicine" -> reservasMinicineList.add(res)
                            "Autitos" -> reservasAutitosList.add(res)
                            else -> reservasDanceList.add(res)
                        }
                    }
                }
                listaReservasGlobales.sortBy { it.getSortTimestamp() }
                reservasMinicineList.sortBy { it.getSortTimestamp() }
                reservasAutitosList.sortBy { it.getSortTimestamp() }
                reservasDanceList.sortBy { it.getSortTimestamp() }
                adapterResMinicine.notifyDataSetChanged()
                adapterResAutitos.notifyDataSetChanged()
                adapterResDance.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 4. ESCUCHAR VETADOS
        database.child("vetados").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaVetadosGlobal.clear()
                for (snap in snapshot.children) {
                    snap.getValue(ClienteVetado::class.java)?.let { listaVetadosGlobal.add(it) }
                }
                vetadosAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun navegarAPantalla(titulo: String) {
        layoutTablero.visibility = View.GONE
        layoutRegistro.visibility = View.GONE
        layoutReservasGenerales.visibility = View.GONE
        layoutVetados.visibility = View.GONE

        when (titulo) {
            "Just Play (Tablero)" -> {
                layoutTablero.visibility = View.VISIBLE
                tvTituloToolbar.text = "Just Play"
                PresenciaManager.actualizarSeccion("Tablero")
            }
            "Registro" -> {
                layoutRegistro.visibility = View.VISIBLE
                tvTituloToolbar.text = "Registro"
                aplicarFiltrosRegistro()
                PresenciaManager.actualizarSeccion("Registro")
            }
            "Reservas" -> {
                layoutReservasGenerales.visibility = View.VISIBLE
                tvTituloToolbar.text = "Reservas"
                PresenciaManager.actualizarSeccion("Reservas")
            }
            "Clientes Vetados" -> {
                layoutVetados.visibility = View.VISIBLE
                tvTituloToolbar.text = "Vetados"
                PresenciaManager.actualizarSeccion("Vetados")
            }
        }

        if (isFabMenuExpanded) toggleFabMenu()

        if (layoutTablero.visibility == View.VISIBLE && estaConectadoInternet) {
            fabMain.visibility = View.VISIBLE
        } else {
            fabMain.visibility = View.GONE
        }
    }

    private fun toggleFabMenu() {
        if (isFabMenuExpanded) {
            fabOverlay.visibility = View.GONE
            llFabVenta.visibility = View.GONE
            llFabReservas.visibility = View.GONE
            fabMain.setImageResource(android.R.drawable.ic_input_add)
        } else {
            fabOverlay.visibility = View.VISIBLE
            llFabVenta.visibility = View.VISIBLE
            llFabReservas.visibility = View.VISIBLE
            fabMain.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        }
        isFabMenuExpanded = !isFabMenuExpanded
    }

    private fun mostrarDialogoVentaDirecta() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nueva_funcion, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTituloDialog = dialogView.findViewById<TextView>(R.id.tvTituloDialog)
        val etCliente = dialogView.findViewById<TextInputEditText>(R.id.etCliente)
        val etCelular = dialogView.findViewById<TextInputEditText>(R.id.etCelular)
        val etPelicula = dialogView.findViewById<TextInputEditText>(R.id.etPelicula)
        val etTiempo = dialogView.findViewById<TextInputEditText>(R.id.etTiempo)
        val etPrecioSala = dialogView.findViewById<TextInputEditText>(R.id.etPrecioSala)
        val tvTotalCuenta = dialogView.findViewById<TextView>(R.id.tvTotalCuenta)
        val btnConfirmar = dialogView.findViewById<Button>(R.id.btnConfirmar)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelar)
        val etPedCantidad = dialogView.findViewById<EditText>(R.id.etPedCantidad)
        val etPedNombre = dialogView.findViewById<EditText>(R.id.etPedNombre)
        val etPedPrecio = dialogView.findViewById<EditText>(R.id.etPedPrecio)
        val btnAgregarPedido = dialogView.findViewById<Button>(R.id.btnAgregarPedido)
        val llListaPedidos = dialogView.findViewById<LinearLayout>(R.id.llListaPedidos)

        // METODO DE PAGO Y QR
        val rgMetodoPago = dialogView.findViewById<RadioGroup>(R.id.rgMetodoPago)
        val rbPagoEfectivo = dialogView.findViewById<RadioButton>(R.id.rbPagoEfectivo)
        val rbPagoQR = dialogView.findViewById<RadioButton>(R.id.rbPagoQR)
        val llSeccionQR = dialogView.findViewById<LinearLayout>(R.id.llSeccionQR)
        val btnTomarFotoQR = dialogView.findViewById<Button>(R.id.btnTomarFotoQR)
        val btnGaleriaQR = dialogView.findViewById<Button>(R.id.btnGaleriaQR)
        val rlPreviewQR = dialogView.findViewById<View>(R.id.rlPreviewQR)
        val ivPreviewQR = dialogView.findViewById<ImageView>(R.id.ivPreviewQR)
        val tvVerFotoQR = dialogView.findViewById<TextView>(R.id.tvVerFotoQR)
        val btnEliminarFotoQR = dialogView.findViewById<TextView>(R.id.btnEliminarFotoQR)

        var rutaComprobanteVenta: String? = null

        tvTituloDialog.text = "🛒 Venta de Mostrador"
        etCliente.visibility = View.GONE
        etCelular.visibility = View.GONE
        etPelicula.visibility = View.GONE
        etTiempo.visibility = View.GONE
        etPrecioSala.visibility = View.GONE

        val pedidosVenta = mutableListOf<Pedido>()

        val rbPagoMixto = dialogView.findViewById<RadioButton>(R.id.rbPagoMixto)
        val llSeccionMixto = dialogView.findViewById<LinearLayout>(R.id.llSeccionMixto)
        val etMontoEfectivoMixto = dialogView.findViewById<EditText>(R.id.etMontoEfectivoMixto)
        val tvMontoQRMixto = dialogView.findViewById<TextView>(R.id.tvMontoQRMixto)

        fun recalcularMixto() {
            val total = pedidosVenta.sumOf { it.subtotal() }
            val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
            val saldoQR = maxOf(0.0, total - efec)
            tvMontoQRMixto?.text = "📱 Saldo en QR: $saldoQR Bs"
        }

        etMontoEfectivoMixto?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalcularMixto() }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun refrescarTotalUI() {
            val total = pedidosVenta.sumOf { it.subtotal() }
            tvTotalCuenta.text = "TOTAL: $total Bs"
            recalcularMixto()
        }
        refrescarTotalUI()

        fun actualizarVistaPreviaQR(fullPath: String?) {
            if (!fullPath.isNullOrEmpty()) {
                val rutas = ImageUtils.separarRutasComprobantes(fullPath)
                if (rutas.isNotEmpty()) {
                    val ultimaRuta = rutas.last()
                    val bitmap = ImageUtils.obtenerBitmap(this, ultimaRuta)
                    if (bitmap != null) {
                        ivPreviewQR.setImageBitmap(bitmap)
                    }
                    val tvEstado = dialogView.findViewById<TextView>(R.id.tvEstadoFotoQR)
                    val tvVer = dialogView.findViewById<TextView>(R.id.tvVerFotoQR)
                    if (rutas.size > 1) {
                        tvEstado?.text = "✅ ${rutas.size} comprobantes adjuntados"
                        tvVer?.text = "🔍 Toca para ver las ${rutas.size} fotos"
                    } else {
                        tvEstado?.text = "✅ Comprobante adjuntado"
                        tvVer?.text = "🔍 Toca para ampliar"
                    }
                    rlPreviewQR.visibility = View.VISIBLE
                } else {
                    rlPreviewQR.visibility = View.GONE
                }
            } else {
                rlPreviewQR.visibility = View.GONE
            }
        }

        rgMetodoPago.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPagoQR -> {
                    llSeccionQR.visibility = View.VISIBLE
                    llSeccionMixto?.visibility = View.GONE
                }
                R.id.rbPagoMixto -> {
                    llSeccionQR.visibility = View.VISIBLE
                    llSeccionMixto?.visibility = View.VISIBLE
                    recalcularMixto()
                }
                else -> {
                    llSeccionQR.visibility = View.GONE
                    llSeccionMixto?.visibility = View.GONE
                }
            }
        }

        btnTomarFotoQR.setOnClickListener {
            capturarFotoCamara { path ->
                if (path != null) {
                    rutaComprobanteVenta = ImageUtils.agregarComprobante(rutaComprobanteVenta, path)
                    actualizarVistaPreviaQR(rutaComprobanteVenta)
                }
            }
        }

        btnGaleriaQR.setOnClickListener {
            seleccionarFotoGaleria { path ->
                if (path != null) {
                    rutaComprobanteVenta = ImageUtils.agregarComprobante(rutaComprobanteVenta, path)
                    actualizarVistaPreviaQR(rutaComprobanteVenta)
                }
            }
        }

        tvVerFotoQR.setOnClickListener {
            if (rutaComprobanteVenta != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, rutaComprobanteVenta!!, "Comprobante Venta Mostrador") { nuevaCadena ->
                    rutaComprobanteVenta = nuevaCadena
                    actualizarVistaPreviaQR(nuevaCadena)
                }
            }
        }

        btnEliminarFotoQR.setOnClickListener {
            if (rutaComprobanteVenta != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, rutaComprobanteVenta!!, "Comprobante Venta Mostrador") { nuevaCadena ->
                    rutaComprobanteVenta = nuevaCadena
                    actualizarVistaPreviaQR(nuevaCadena)
                }
            }
        }

        fun dibujarCheckboxPedido(pedido: Pedido) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val cb = CheckBox(this).apply {
                text = "${pedido.cantidad}x ${pedido.nombre} (${pedido.subtotal()} Bs)"
                isChecked = true
                isEnabled = false
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnEliminar = TextView(this).apply {
                text = "🗑️"
                textSize = 20f
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    pedidosVenta.remove(pedido)
                    llListaPedidos.removeView(fila)
                    refrescarTotalUI()
                }
            }
            fila.addView(cb)
            fila.addView(btnEliminar)
            llListaPedidos.addView(fila)
        }

        btnAgregarPedido.setOnClickListener {
            val cantStr = etPedCantidad.text.toString().trim()
            val nomStr = etPedNombre.text.toString().trim()
            val precStr = etPedPrecio.text.toString().trim()

            if (cantStr.isNotEmpty() && nomStr.isNotEmpty() && precStr.isNotEmpty()) {
                val nuevoPedido = Pedido("", cantStr.toInt(), nomStr, precStr.toDouble(), true)
                pedidosVenta.add(nuevoPedido)
                dibujarCheckboxPedido(nuevoPedido)
                refrescarTotalUI()

                etPedCantidad.text.clear()
                etPedNombre.text.clear()
                etPedPrecio.text.clear()
            } else {
                Toast.makeText(this, "Llene todos los datos del producto", Toast.LENGTH_SHORT).show()
            }
        }

        btnConfirmar.text = "Cobrar"
        btnConfirmar.setOnClickListener {
            if (pedidosVenta.isEmpty()) {
                Toast.makeText(this, "Debe agregar al menos un producto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalCobrado = pedidosVenta.sumOf { it.subtotal() }
            val metodo = when {
                rbPagoQR.isChecked -> "QR"
                rbPagoMixto?.isChecked == true -> {
                    val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val saldoQR = maxOf(0.0, totalCobrado - efec)
                    "Mixto (${efec} Efec / ${saldoQR} QR)"
                }
                else -> "Efectivo"
            }
            val fechaHoraActual = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val resumenPedidos = pedidosVenta.joinToString(", ") { "${it.cantidad}x ${it.nombre}" }

            // Create one entry per pedido
            for (pedido in pedidosVenta) {
                val pKey = database.child("registros").push().key ?: continue
                val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)
                val pEntrada = RegistroEntry(
                    idFirebase = pKey,
                    tipo = "Venta Directa",
                    nombreElemento = "Mostrador",
                    cliente = "Público General",
                    celular = "S/N",
                    peliculaJuego = "N/A",
                    tiempoConfigurado = "N/A",
                    fechaHora = fechaHoraActual,
                    pedidos = "${pedido.cantidad}x ${pedido.nombre}",
                    totalCobrado = pedido.subtotal(),
                    metodoPago = metodo,
                    comprobanteUri = if (metodo != "Efectivo") rutaComprobanteVenta else null,
                    creadoPor = usuarioActual,
                    eliminado = false
                )
                database.child("registros").child(pKey).setValue(pEntrada)
                database.child("auditoria_registros").child(pKey).setValue(pEntrada)
            }

            Toast.makeText(this, "Venta registrada con éxito ($metodo)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoVetarCliente(vetadoExistente: ClienteVetado?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val etCelular = EditText(this).apply {
            hint = "Número de Celular"
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val etNombre = EditText(this).apply {
            hint = "Nombre (Opcional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val etMotivo = EditText(this).apply {
            hint = "Motivo del veto / Notas adicionales"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        layout.addView(etCelular)
        layout.addView(etNombre)
        layout.addView(etMotivo)

        if (vetadoExistente != null) {
            etCelular.setText(vetadoExistente.celular)
            etCelular.isEnabled = false
            etNombre.setText(vetadoExistente.nombre)
            etMotivo.setText(vetadoExistente.motivo)
        }

        AlertDialog.Builder(this)
            .setTitle(if (vetadoExistente == null) "🚫 Vetar Cliente" else "✏️ Editar Vetado")
            .setMessage(if (vetadoExistente == null) "Este número será registrado en la lista negra con el motivo indicado." else "Actualizar información de este número.")
            .setView(layout)
            .setPositiveButton(if (vetadoExistente == null) "Vetar" else "Actualizar") { _, _ ->
                val cel = etCelular.text.toString().trim()
                if (cel.isNotEmpty()) {
                    val nuevoVetado = ClienteVetado(
                        celular = cel,
                        nombre = etNombre.text.toString().trim(),
                        motivo = etMotivo.text.toString().trim()
                    )
                    database.child("vetados").child(cel).setValue(nuevoVetado)
                    Toast.makeText(this, "Cliente Vetado actualizado en la nube", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Debe ingresar un celular", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configurarAdaptersReservas() {
        val onEditar = { reserva: Reserva -> mostrarDialogoEditarReserva(reserva) }
        val onAsignar = { reserva: Reserva -> mostrarDialogoSeleccionarSalaLibre(reserva) }

        adapterResMinicine = ReservasAdapter(reservasMinicineList, onEditar, onAsignar)
        val rvMinicine = findViewById<RecyclerView>(R.id.rvReservasMinicine)
        rvMinicine.layoutManager = LinearLayoutManager(this)
        rvMinicine.adapter = adapterResMinicine

        adapterResAutitos = ReservasAdapter(reservasAutitosList, onEditar, onAsignar)
        val rvAutitos = findViewById<RecyclerView>(R.id.rvReservasAutitos)
        rvAutitos.layoutManager = LinearLayoutManager(this)
        rvAutitos.adapter = adapterResAutitos

        adapterResDance = ReservasAdapter(reservasDanceList, onEditar, onAsignar)
        val rvDance = findViewById<RecyclerView>(R.id.rvReservasJustDance)
        rvDance.layoutManager = LinearLayoutManager(this)
        rvDance.adapter = adapterResDance
    }

    private fun actualizarRegistroDeSala(sala: Sala) {
        val ultimoRegistro = historialCompleto.lastOrNull {
            it.nombreElemento == sala.nombre && (it.pedidos == "Alquiler de Sala" || it.tipo in listOf("Minicine", "Autitos", "Just Dance"))
        }

        fun aplicarActualizacion(regId: String, entradaActual: RegistroEntry?) {
            val cliente = sala.cliente ?: entradaActual?.cliente ?: ""
            val celular = sala.celular ?: entradaActual?.celular
            val pelicula = sala.pelicula ?: entradaActual?.peliculaJuego
            val precio = sala.precioCobrado
            val metodo = sala.metodoPago
            val efec = sala.montoEfectivo
            val qr = sala.montoQR
            val comp = sala.comprobanteUri ?: entradaActual?.comprobanteUri

            val updates = mapOf<String, Any?>(
                "cliente" to cliente,
                "celular" to celular,
                "peliculaJuego" to pelicula,
                "totalCobrado" to precio,
                "metodoPago" to metodo,
                "montoEfectivo" to efec,
                "montoQR" to qr,
                "comprobanteUri" to comp
            )

            database.child("registros").child(regId).updateChildren(updates)
            database.child("auditoria_registros").child(regId).updateChildren(updates)
        }

        if (ultimoRegistro != null && ultimoRegistro.idFirebase.isNotEmpty()) {
            ultimoRegistro.cliente = sala.cliente ?: ""
            ultimoRegistro.celular = sala.celular
            ultimoRegistro.peliculaJuego = sala.pelicula
            ultimoRegistro.totalCobrado = sala.precioCobrado
            ultimoRegistro.metodoPago = sala.metodoPago
            ultimoRegistro.montoEfectivo = sala.montoEfectivo
            ultimoRegistro.montoQR = sala.montoQR
            ultimoRegistro.comprobanteUri = sala.comprobanteUri

            aplicarActualizacion(ultimoRegistro.idFirebase, ultimoRegistro)
        } else {
            database.child("registros").orderByChild("nombreElemento").equalTo(sala.nombre)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val matchingSnap = snapshot.children.lastOrNull { child ->
                            val del = child.child("eliminado").getValue(Boolean::class.java) ?: false
                            !del
                        }
                        if (matchingSnap != null) {
                            val idFb = matchingSnap.key ?: return
                            val regObj = matchingSnap.getValue(RegistroEntry::class.java)
                            aplicarActualizacion(idFb, regObj)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun actualizarEstadoConexion(conectado: Boolean) {
        estaConectadoInternet = conectado
        if (!conectado) {
            tvBadgeOffline.visibility = View.VISIBLE
            fabMain.visibility = View.GONE
            if (isFabMenuExpanded) toggleFabMenu()
        } else {
            tvBadgeOffline.visibility = View.GONE
            if (layoutTablero.visibility == View.VISIBLE) {
                fabMain.visibility = View.VISIBLE
            }
        }
    }

    private fun configurarRecyclerView(rv: RecyclerView, lista: List<Sala>) {
        // PANTALLA INICIAL: Cuadrícula en filas de 2 columnas
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = SalasAdapter(lista) { salaSeleccionada ->
            if (!estaConectadoInternet) {
                // Modo offline: Solo lectura de los datos locales
                mostrarDialogoDetallesSalaSoloLectura(salaSeleccionada)
            } else {
                // Modo online: Permite cobrar, editar, asignar o agregar
                mostrarDialogoFuncion(salaSeleccionada, esNueva = (salaSeleccionada.estado == EstadoSala.DISPONIBLE))
            }
        }
    }

    private fun mostrarDialogoDetallesSalaSoloLectura(sala: Sala) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detalle_sala_solo_lectura, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val esMinicine = sala.id < 100
        val esAutitos = sala.id in 101..199
        val icono = when {
            esMinicine -> "🎬"
            esAutitos -> "🏎️"
            else -> "💃"
        }

        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvTituloDetalleSoloLectura)
        val btnCerrarX = dialogView.findViewById<TextView>(R.id.btnCerrarDetalleSoloLecturaX)
        val btnCerrar = dialogView.findViewById<Button>(R.id.btnCerrarDetalleSoloLectura)
        val tvBadge = dialogView.findViewById<TextView>(R.id.tvBadgeEstadoSoloLectura)
        val llInfoOcupada = dialogView.findViewById<LinearLayout>(R.id.llInfoOcupadaSoloLectura)
        val llInfoDisponible = dialogView.findViewById<LinearLayout>(R.id.llInfoDisponibleSoloLectura)

        val tvCliente = dialogView.findViewById<TextView>(R.id.tvClienteDetalleSoloLectura)
        val tvTiempoRestante = dialogView.findViewById<TextView>(R.id.tvTiempoRestanteDetalleSoloLectura)
        val tvHoraFin = dialogView.findViewById<TextView>(R.id.tvHoraFinDetalleSoloLectura)
        val llFilaPelicula = dialogView.findViewById<LinearLayout>(R.id.llFilaPeliculaSoloLectura)
        val tvPelicula = dialogView.findViewById<TextView>(R.id.tvPeliculaDetalleSoloLectura)
        val tvPedidos = dialogView.findViewById<TextView>(R.id.tvPedidosDetalleSoloLectura)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotalDetalleSoloLectura)
        val tvMetodoPago = dialogView.findViewById<TextView>(R.id.tvMetodoPagoDetalleSoloLectura)
        val btnVerComprobante = dialogView.findViewById<Button>(R.id.btnVerComprobanteDetalleSoloLectura)

        tvTitulo.text = "$icono ${sala.nombre}"

        if (sala.estado == EstadoSala.OCUPADA) {
            tvBadge.text = "🔴 OCUPADA"
            tvBadge.setTextColor(Color.parseColor("#FF5252"))
            llInfoOcupada.visibility = View.VISIBLE
            llInfoDisponible.visibility = View.GONE

            val cel = if (!sala.celular.isNullOrEmpty() && sala.celular != "S/N") " (${sala.celular})" else ""
            tvCliente.text = "${sala.cliente ?: "N/A"}$cel"

            if (sala.endTimeMillis != null) {
                val faltante = sala.endTimeMillis!! - System.currentTimeMillis()
                if (faltante > 0) {
                    val h = (faltante / 3600000) % 24
                    val m = (faltante / 60000) % 60
                    val s = (faltante / 1000) % 60
                    tvTiempoRestante.text = String.format("%02d:%02d:%02d", h, m, s)
                    tvTiempoRestante.setTextColor(Color.parseColor("#00E676"))
                } else {
                    tvTiempoRestante.text = "¡Tiempo Finalizado!"
                    tvTiempoRestante.setTextColor(Color.parseColor("#FF5252"))
                }
                tvHoraFin.text = sala.horaFinProgramada ?: "--:--"
            } else {
                tvTiempoRestante.text = "Tiempo Continuo"
                tvHoraFin.text = "Libre"
            }

            if (esMinicine && !sala.pelicula.isNullOrEmpty() && sala.pelicula != "N/A") {
                llFilaPelicula.visibility = View.VISIBLE
                tvPelicula.text = sala.pelicula
            } else {
                llFilaPelicula.visibility = View.GONE
            }

            if (sala.pedidos.isNotEmpty()) {
                tvPedidos.text = sala.pedidos.joinToString("\n") { "• ${it.cantidad}x ${it.nombre} (${it.subtotal()} Bs)" }
            } else {
                tvPedidos.text = "Ninguno"
            }

            tvTotal.text = "Total: ${sala.calcularTotalGeneral()} Bs"

            val metodo = when {
                sala.metodoPago.startsWith("Mixto", ignoreCase = true) -> "💵📱 ${sala.metodoPago}"
                sala.metodoPago.equals("QR", ignoreCase = true) -> "📱 QR"
                else -> "💵 Efectivo"
            }
            tvMetodoPago.text = metodo

            if (!sala.comprobanteUri.isNullOrEmpty()) {
                btnVerComprobante.visibility = View.VISIBLE
                btnVerComprobante.setOnClickListener {
                    ImageUtils.mostrarDialogoVerComprobante(this, sala.comprobanteUri!!, "Comprobante: ${sala.nombre}")
                }
            } else {
                btnVerComprobante.visibility = View.GONE
            }
        } else {
            tvBadge.text = "🟢 DISPONIBLE"
            tvBadge.setTextColor(Color.parseColor("#00E676"))
            llInfoOcupada.visibility = View.GONE
            llInfoDisponible.visibility = View.VISIBLE
        }

        btnCerrarX.setOnClickListener { dialog.dismiss() }
        btnCerrar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun actualizarContadorOcupacion() {
        tvMinicineOcupadas.text = "${salasMinicine.count { it.estado == EstadoSala.OCUPADA }} / ${salasMinicine.size}"
        tvAutitosOcupados.text = "${salasAutitos.count { it.estado == EstadoSala.OCUPADA }} / ${salasAutitos.size}"
        tvJustDanceOcupados.text = "${salasJustDance.count { it.estado == EstadoSala.OCUPADA }} / ${salasJustDance.size}"
    }

    private fun actualizarRelojesReservas() {
        if (layoutTablero.visibility != View.VISIBLE) return
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val hoyStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val reservasHoy = listaReservasGlobales.filter { it.fecha == hoyStr && !it.asistio }.sortedBy { it.horaProgramada }
        llContenedorReservasDash.removeAllViews()

        if (reservasHoy.isEmpty()) {
            val tv = TextView(this).apply { text = "Sin reservas programadas para hoy"; setTextColor(Color.WHITE) }
            llContenedorReservasDash.addView(tv)
            return
        }

        val ahora = System.currentTimeMillis()
        reservasHoy.take(3).forEach { reserva ->
            try {
                val targetDate = sdf.parse("${reserva.fecha} ${reserva.horaProgramada}")
                val diffMillis = targetDate!!.time - ahora
                val tv = TextView(this).apply { textSize = 14f; setTypeface(null, Typeface.BOLD) }

                if (diffMillis > 0) {
                    val h = (diffMillis / (1000 * 60 * 60)) % 24
                    val m = (diffMillis / (1000 * 60)) % 60
                    val s = (diffMillis / 1000) % 60
                    tv.text = "${reserva.nombre} (${reserva.tipoSala}): ${String.format("%02d:%02d:%02d", h, m, s)}"
                    tv.setTextColor(Color.parseColor("#DEFF9A"))
                } else {
                    tv.text = "${reserva.nombre} (${reserva.tipoSala}): Atraso ${Math.abs(diffMillis / (1000 * 60))}m"
                    tv.setTextColor(Color.parseColor("#FF5252"))
                }
                llContenedorReservasDash.addView(tv)
            } catch (_: Exception) {}
        }
    }

    private fun mostrarDialogoCatalogoProductosEmpleado() {
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }
        scrollView.addView(container)

        val dialog = AlertDialog.Builder(this)
            .setView(scrollView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val tvTitulo = TextView(this).apply {
            text = "📦 Catálogo de Productos & Precios"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCerrar = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 20f
            setPadding(10, 0, 10, 0)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(tvTitulo)
        header.addView(btnCerrar)
        container.addView(header)

        val tvSub = TextView(this).apply {
            text = "Precios oficiales y stock sincronizados en vivo con el Lector POS"
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setPadding(0, 8, 0, 14)
        }
        container.addView(tvSub)

        val btnNuevoProd = Button(this).apply {
            text = "➕ Agregar Producto al Catálogo"
            setBackgroundColor(Color.parseColor("#DEFF9A"))
            setTextColor(Color.BLACK)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp
            setOnClickListener {
                mostrarDialogoCrearEditarProductoEmpleado(null)
            }
        }
        container.addView(btnNuevoProd)

        val llListaProductos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(llListaProductos)

        val productosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llListaProductos.removeAllViews()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    val tvVacio = TextView(this@MainActivity).apply {
                        text = "No hay productos registrados en el catálogo"
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 12f
                        setPadding(0, 10, 0, 10)
                    }
                    llListaProductos.addView(tvVacio)
                    return
                }

                for (snap in snapshot.children) {
                    val barcode = snap.child("codigo_barras").getValue(String::class.java) ?: snap.key ?: ""
                    val nombre = snap.child("nombre").getValue(String::class.java) ?: "Producto $barcode"
                    val gramaje = snap.child("gramaje").getValue(String::class.java) ?: ""
                    val precio = snap.child("precio_unitario").getValue(Double::class.java) ?: 0.0
                    val stock = snap.child("stock_actual").getValue(Int::class.java) ?: 0
                    val categoria = snap.child("categoria").getValue(String::class.java) ?: "General"

                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(14, 12, 14, 12)
                        setBackgroundColor(Color.parseColor("#24283B"))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 8)
                        layoutParams = lp
                        setOnClickListener {
                            mostrarDialogoCrearEditarProductoEmpleado(barcode, nombre, precio, stock, categoria, gramaje)
                        }
                    }

                    val info = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val tvNom = TextView(this@MainActivity).apply {
                        val nomText = if (gramaje.isNotBlank()) "📦 $nombre ($gramaje)" else "📦 $nombre"
                        text = nomText
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvDetalle = TextView(this@MainActivity).apply {
                        text = "Cód: $barcode | $categoria | Precio: $precio Bs"
                        setTextColor(Color.parseColor("#A0A0A0"))
                        textSize = 11f
                    }

                    val tvStock = TextView(this@MainActivity).apply {
                        text = "Stock: $stock"
                        setTextColor(if (stock <= 5) Color.parseColor("#FF5252") else Color.parseColor("#00E676"))
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(10, 0, 0, 0)
                    }

                    info.addView(tvNom)
                    info.addView(tvDetalle)
                    row.addView(info)
                    row.addView(tvStock)
                    llListaProductos.addView(row)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("productos_catalogo").addValueEventListener(productosListener)
        dialog.setOnDismissListener { database.child("productos_catalogo").removeEventListener(productosListener) }

        dialog.show()
    }

    private fun mostrarDialogoCrearEditarProductoEmpleado(
        codigoExistente: String? = null,
        nombreExistente: String = "",
        precioExistente: Double = 10.0,
        stockExistente: Int = 50,
        categoriaExistente: String = "Snacks",
        gramajeExistente: String = ""
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }

        val etCodigo = EditText(this).apply {
            hint = "Código de Barras (ej. 77510001)"
            setText(codigoExistente ?: "")
            isEnabled = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etNombre = EditText(this).apply {
            hint = "Nombre del Producto (ej. Doritos)"
            setText(nombreExistente)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etGramaje = EditText(this).apply {
            hint = "Gramaje o Volumen (ej. 500ml, 120g - Opcional)"
            setText(gramajeExistente)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etPrecio = EditText(this).apply {
            hint = "Precio Venta en Bs"
            setText(precioExistente.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etStock = EditText(this).apply {
            hint = "Stock Actual (ej. 50 o +10 para sumar)"
            setText(stockExistente.toString())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        // Fila de botones para incremento rápido de stock (+5, +10, +20, +50)
        val llStockButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 8)
        }
        listOf(5, 10, 20, 50).forEach { inc ->
            val btnInc = Button(this).apply {
                text = "+$inc"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#24283B"))
                setTextColor(Color.parseColor("#DEFF9A"))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(2, 0, 2, 0)
                }
                layoutParams = lp
                setOnClickListener {
                    val currentTxt = etStock.text.toString().trim()
                    if (currentTxt.startsWith("+")) {
                        val curInc = currentTxt.removePrefix("+").trim().toIntOrNull() ?: 0
                        etStock.setText("+${curInc + inc}")
                    } else {
                        val curVal = currentTxt.toIntOrNull() ?: stockExistente
                        etStock.setText((curVal + inc).toString())
                    }
                }
            }
            llStockButtons.addView(btnInc)
        }

        val tvCatLabel = TextView(this).apply {
            text = "Categoría:"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 12f
            setPadding(0, 8, 0, 4)
        }

        val categoriasFijas = arrayOf("Snack", "Bebidas", "Golosinas")
        val spinnerCategoria = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categoriasFijas)
            setAdapter(adapter)
            val index = when {
                categoriaExistente.startsWith("Snack", ignoreCase = true) -> 0
                categoriaExistente.equals("Bebidas", ignoreCase = true) -> 1
                categoriaExistente.equals("Golosinas", ignoreCase = true) -> 2
                else -> 0
            }
            setSelection(index)
            setBackgroundColor(Color.parseColor("#24283B"))
        }

        container.addView(etCodigo)
        container.addView(etNombre)
        container.addView(etGramaje)
        container.addView(etPrecio)
        container.addView(etStock)
        container.addView(llStockButtons)
        container.addView(tvCatLabel)
        container.addView(spinnerCategoria)

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(if (codigoExistente == null) "➕ Nuevo Producto (Empleado)" else "✏️ Editar $nombreExistente")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val code = etCodigo.text.toString().trim()
                val name = etNombre.text.toString().trim()
                val gramaje = etGramaje.text.toString().trim()
                val price = etPrecio.text.toString().toDoubleOrNull() ?: 10.0
                val rawStock = etStock.text.toString().trim()
                val stock = if (rawStock.startsWith("+")) {
                    val inc = rawStock.removePrefix("+").trim().toIntOrNull() ?: 0
                    stockExistente + inc
                } else {
                    rawStock.toIntOrNull() ?: stockExistente
                }
                val cat = spinnerCategoria.selectedItem?.toString() ?: "Snack"
                val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                if (code.isNotEmpty() && name.isNotEmpty()) {
                    // Validar duplicados de código y nombre en Firebase
                    database.child("productos_catalogo").get().addOnSuccessListener { snap ->
                        val esNuevo = codigoExistente == null
                        val codigoCambio = codigoExistente != null && codigoExistente != code

                        // 1. Validar código duplicado en creación o si el código fue cambiado
                        if ((esNuevo || codigoCambio) && snap.hasChild(code)) {
                            Toast.makeText(this@MainActivity, "❌ Error: Ya existe un producto con el código de barras '$code'", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // 2. Validar nombre duplicado (en creación o edición)
                        val duplicateNameSnap = snap.children.find { child ->
                            val childCode = child.key ?: ""
                            val childName = child.child("nombre").getValue(String::class.java)?.trim() ?: ""
                            childCode != (codigoExistente ?: code) && childName.equals(name, ignoreCase = true)
                        }

                        if (duplicateNameSnap != null) {
                            val existingCode = duplicateNameSnap.key
                            Toast.makeText(this@MainActivity, "❌ Error: Ya existe otro producto con el nombre '$name' (Código: $existingCode)", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // Si el código cambió, eliminar el código anterior de Firebase
                        if (codigoCambio) {
                            database.child("productos_catalogo").child(codigoExistente!!).removeValue()
                        }

                        val map = mapOf(
                            "codigo_barras" to code,
                            "nombre" to name,
                            "gramaje" to gramaje,
                            "precio_unitario" to price,
                            "stock_actual" to stock,
                            "categoria" to cat,
                            "activo" to true,
                            "fecha_actualizacion" to now
                        )
                        database.child("productos_catalogo").child(code).setValue(map)

                        // Registro en Auditoría con Trazabilidad de Empleado y Dispositivo
                        productAuditManager.registrarAuditoriaProducto(
                            tipoOperacion = if (esNuevo) "CREACION" else if (codigoCambio) "MIGRACION_CODIGO" else "EDICION",
                            codigoBarras = code,
                            nombreProducto = name,
                            categoria = cat,
                            precioAnterior = if (esNuevo) 0.0 else precioExistente,
                            precioNuevo = price,
                            stockAnterior = if (esNuevo) 0 else stockExistente,
                            stockNuevo = stock,
                            usuarioNombre = PresenciaManager.obtenerNombreUsuario(this@MainActivity).ifEmpty { "Empleado" },
                            usuarioRol = "Empleado",
                            detalles = if (esNuevo) "Producto registrado por Empleado" else if (codigoCambio) "Código migrado de $codigoExistente a $code" else "Precio/Stock/Gramaje modificado por Empleado"
                        )

                        Toast.makeText(this@MainActivity, "Producto '$name' guardado exitosamente", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        Toast.makeText(this@MainActivity, "Error al verificar duplicados: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Código y Nombre son obligatorios", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)

        if (codigoExistente != null) {
            dialogBuilder.setNeutralButton("🗑️ Eliminar") { _, _ ->
                database.child("productos_catalogo").child(codigoExistente).removeValue()
                productAuditManager.registrarAuditoriaProducto(
                    tipoOperacion = "ELIMINACION",
                    codigoBarras = codigoExistente,
                    nombreProducto = nombreExistente,
                    categoria = categoriaExistente,
                    precioAnterior = precioExistente,
                    precioNuevo = 0.0,
                    stockAnterior = stockExistente,
                    stockNuevo = 0,
                    usuarioNombre = PresenciaManager.obtenerNombreUsuario(this).ifEmpty { "Empleado" },
                    usuarioRol = "Empleado",
                    detalles = "Producto eliminado por Empleado"
                )
                Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBuilder.show()
    }

    private fun convertirTiempoAMilisegundos(tiempoStr: String): Long? {
        if (tiempoStr.isEmpty()) return null
        val partes = tiempoStr.split(":")
        return try {
            when (partes.size) {
                1 -> partes[0].toLong() * 60 * 60 * 1000
                2 -> (partes[0].toLong() * 60 * 60 * 1000) + (partes[1].toLong() * 60 * 1000)
                3 -> (partes[0].toLong() * 60 * 60 * 1000) + (partes[1].toLong() * 60 * 1000) + (partes[2].toLong() * 1000)
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun aplicarFiltrosRegistro() {
        val hoyStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        if (fechaFiltroSeleccionada.isEmpty()) {
            fechaFiltroSeleccionada = hoyStr
        }

        val cbCine = findViewById<CheckBox>(R.id.cbFiltroCine)
        val cbAutitos = findViewById<CheckBox>(R.id.cbFiltroAutitos)
        val cbDance = findViewById<CheckBox>(R.id.cbFiltroDance)
        val cbMostrador = findViewById<CheckBox>(R.id.cbFiltroMostrador)
        val cbEfectivo = findViewById<CheckBox>(R.id.cbFiltroEfectivo)
        val cbQR = findViewById<CheckBox>(R.id.cbFiltroQR)
        val cbEgresos = findViewById<CheckBox>(R.id.cbFiltroEgresos)

        val incluirCine = cbCine?.isChecked ?: true
        val incluirAutitos = cbAutitos?.isChecked ?: true
        val incluirDance = cbDance?.isChecked ?: true
        val incluirMostrador = cbMostrador?.isChecked ?: true
        val incluirEfectivo = cbEfectivo?.isChecked ?: true
        val incluirQR = cbQR?.isChecked ?: true
        val incluirEgresos = cbEgresos?.isChecked ?: false

        historialFiltrado.clear()

        for (reg in historialCompleto) {
            // 1. Filtro por Fecha
            val coincideFecha = reg.fechaHora.startsWith(fechaFiltroSeleccionada)
            if (!coincideFecha) continue

            // 2. Filtro por Rango Horario (De tal hora a tal hora)
            val partes = reg.fechaHora.split(" ")
            val horaMinutoReg = if (partes.size >= 2) {
                val subPartes = partes[1].split(":")
                if (subPartes.size >= 2) "${subPartes[0]}:${subPartes[1]}" else partes[1]
            } else null

            if (horaMinutoReg != null) {
                if (horaInicioFiltro != null && horaMinutoReg < horaInicioFiltro!!) {
                    continue
                }
                if (horaFinFiltro != null && horaMinutoReg > horaFinFiltro!!) {
                    continue
                }
            }

            // 3. Filtro por Tipo / Categoría (tickeable)
            val tipo = reg.tipo.lowercase()
            val nombreEl = reg.nombreElemento.lowercase()
            val esEgreso = tipo.contains("egreso") || nombreEl.contains("egreso")
            val coincideTipo = when {
                esEgreso -> incluirEgresos
                tipo.contains("consumo") || tipo.contains("mostrador") || tipo.contains("venta directa") || tipo.contains("producto") || tipo.contains("snack") || tipo.contains("bebida") || tipo.contains("golosina") -> incluirMostrador
                tipo.contains("cine") || nombreEl.contains("cine") || (nombreEl.contains("sala") && !tipo.contains("consumo") && !nombreEl.contains("consumo")) -> incluirCine
                tipo.contains("auto") || nombreEl.contains("car") || nombreEl.contains("auto") || nombreEl.contains("volante") -> incluirAutitos
                tipo.contains("dance") || nombreEl.contains("dance") -> incluirDance
                else -> incluirMostrador
            }
            if (!coincideTipo) continue

            // 4. Filtro por Método de Pago (tickeable)
            val esQR = reg.metodoPago.equals("QR", ignoreCase = true)
            val esMixto = reg.metodoPago.startsWith("Mixto", ignoreCase = true)
            val coincidePago = when {
                esMixto -> (incluirQR && reg.getQRAmount() > 0) || (incluirEfectivo && reg.getEfectivoAmount() > 0)
                esQR -> incluirQR
                else -> incluirEfectivo
            }
            if (!coincidePago) continue

            historialFiltrado.add(reg)
        }

        historialFiltrado.sortByDescending { it.fechaHora }

        // CALCULAR TOTALES
        val egresosActivos = historialFiltrado.filter { it.tipo.contains("egreso", true) || it.nombreElemento.contains("egreso", true) }
        val ingresosActivos = historialFiltrado.filter { !(it.tipo.contains("egreso", true) || it.nombreElemento.contains("egreso", true)) }

        val totalIngresos = ingresosActivos.sumOf { 
            (if (incluirEfectivo) it.getEfectivoAmount() else 0.0) + (if (incluirQR) it.getQRAmount() else 0.0)
        }
        val totalEgresos = if (incluirEgresos) egresosActivos.sumOf { it.totalCobrado } else 0.0
        val totalNeto = maxOf(0.0, totalIngresos - totalEgresos)

        val egresosEfectivo = if (incluirEgresos) egresosActivos.filter { !it.metodoPago.contains("QR", true) }.sumOf { it.totalCobrado } else 0.0
        val egresosQR = if (incluirEgresos) egresosActivos.filter { it.metodoPago.contains("QR", true) }.sumOf { it.totalCobrado } else 0.0

        val totalEfectivo = maxOf(0.0, ingresosActivos.sumOf { it.getEfectivoAmount() } - egresosEfectivo)
        val totalQR = maxOf(0.0, ingresosActivos.sumOf { it.getQRAmount() } - egresosQR)

        // ACTUALIZAR UI
        val tvTituloTotal = findViewById<TextView>(R.id.tvTituloTotal)
        val tvTotalRecaudado = findViewById<TextView>(R.id.tvTotalRecaudado)
        val tvDesgloseTotal = findViewById<TextView>(R.id.tvDesgloseTotal)
        val btnFiltroFecha = findViewById<Button>(R.id.btnFiltroFecha)
        val btnFiltroHoraInicio = findViewById<Button>(R.id.btnFiltroHoraInicio)
        val btnFiltroHoraFin = findViewById<Button>(R.id.btnFiltroHoraFin)

        val esHoy = (fechaFiltroSeleccionada == hoyStr)
        tvTituloTotal?.text = if (esHoy) "💰 TOTAL DEL DÍA (HOY):" else "💰 TOTAL ($fechaFiltroSeleccionada):"
        tvTotalRecaudado?.text = String.format(Locale.US, "%.1f Bs", totalNeto)

        val egresosTexto = if (totalEgresos > 0) " | 💸 Egresos: -${String.format(Locale.US, "%.1f", totalEgresos)} Bs" else ""
        tvDesgloseTotal?.text = "💵 Efectivo: ${String.format(Locale.US, "%.1f", totalEfectivo)} Bs | 📱 QR: ${String.format(Locale.US, "%.1f", totalQR)} Bs$egresosTexto - (${historialFiltrado.size} reg)"

        btnFiltroFecha?.text = if (esHoy) "📅 Hoy" else "📅 $fechaFiltroSeleccionada"
        btnFiltroHoraInicio?.text = if (horaInicioFiltro != null) "⏰ Desde: $horaInicioFiltro" else "⏰ Desde: Inicio"
        btnFiltroHoraFin?.text = if (horaFinFiltro != null) "⏰ Hasta: $horaFinFiltro" else "⏰ Hasta: Fin"

        registroAdapter.notifyDataSetChanged()
    }

    private fun mostrarDialogoSeleccionarSalaLibre(reserva: Reserva) {
        val salasDisponibles = when (reserva.tipoSala) {
            "Minicine" -> salasMinicine.filter { it.estado == EstadoSala.DISPONIBLE }
            "Autitos" -> salasAutitos.filter { it.estado == EstadoSala.DISPONIBLE }
            else -> salasJustDance.filter { it.estado == EstadoSala.DISPONIBLE }
        }

        if (salasDisponibles.isEmpty()) {
            Toast.makeText(this, "No hay ${reserva.tipoSala} disponibles.", Toast.LENGTH_LONG).show()
            return
        }

        val nombresSalas = salasDisponibles.map { it.nombre }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Asignar a Sala")
            .setItems(nombresSalas) { _, which ->
                val salaSeleccionada = salasDisponibles[which]
                mostrarDialogoFuncion(salaSeleccionada, esNueva = true, reservaPrevia = reserva)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoFuncion(sala: Sala, esNueva: Boolean, reservaPrevia: Reserva? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nueva_funcion, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTituloDialog = dialogView.findViewById<TextView>(R.id.tvTituloDialog)
        val etCliente = dialogView.findViewById<TextInputEditText>(R.id.etCliente)
        val etCelular = dialogView.findViewById<TextInputEditText>(R.id.etCelular)
        val etPelicula = dialogView.findViewById<TextInputEditText>(R.id.etPelicula)
        val etTiempo = dialogView.findViewById<TextInputEditText>(R.id.etTiempo)
        val etPrecioSala = dialogView.findViewById<TextInputEditText>(R.id.etPrecioSala)
        val tvTotalCuenta = dialogView.findViewById<TextView>(R.id.tvTotalCuenta)

        val btnConfirmar = dialogView.findViewById<Button>(R.id.btnConfirmar)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelar)

        val etPedCantidad = dialogView.findViewById<EditText>(R.id.etPedCantidad)
        val etPedNombre = dialogView.findViewById<EditText>(R.id.etPedNombre)
        val etPedPrecio = dialogView.findViewById<EditText>(R.id.etPedPrecio)
        val btnAgregarPedido = dialogView.findViewById<Button>(R.id.btnAgregarPedido)
        val llListaPedidos = dialogView.findViewById<LinearLayout>(R.id.llListaPedidos)

        // METODO DE PAGO Y QR
        val rgMetodoPago = dialogView.findViewById<RadioGroup>(R.id.rgMetodoPago)
        val rbPagoEfectivo = dialogView.findViewById<RadioButton>(R.id.rbPagoEfectivo)
        val rbPagoQR = dialogView.findViewById<RadioButton>(R.id.rbPagoQR)
        val llSeccionQR = dialogView.findViewById<LinearLayout>(R.id.llSeccionQR)
        val btnTomarFotoQR = dialogView.findViewById<Button>(R.id.btnTomarFotoQR)
        val btnGaleriaQR = dialogView.findViewById<Button>(R.id.btnGaleriaQR)
        val rlPreviewQR = dialogView.findViewById<View>(R.id.rlPreviewQR)
        val ivPreviewQR = dialogView.findViewById<ImageView>(R.id.ivPreviewQR)
        val tvVerFotoQR = dialogView.findViewById<TextView>(R.id.tvVerFotoQR)
        val btnEliminarFotoQR = dialogView.findViewById<TextView>(R.id.btnEliminarFotoQR)

        var rutaComprobanteActual: String? = sala.comprobanteUri

        val esMinicine = salasMinicine.contains(sala) || sala.id < 100
        val esAutitos = salasAutitos.contains(sala) || sala.id in 101..199
        val esJustDance = salasJustDance.contains(sala) || sala.id >= 200

        // TITULO CLARO CON EL NOMBRE DE LA SALA / AUTO / JUST DANCE
        if (esNueva) {
            tvTituloDialog.text = when {
                esMinicine -> "🎬 ${sala.nombre}"
                esAutitos -> "🏎️ ${sala.nombre}"
                else -> "💃 ${sala.nombre}"
            }
        } else {
            tvTituloDialog.text = when {
                esMinicine -> "🎬 Editar: ${sala.nombre}"
                esAutitos -> "🏎️ Editar: ${sala.nombre}"
                else -> "💃 Editar: ${sala.nombre}"
            }
        }

        // PELICULA / JUEGO: Solo visible para Minicine (Cines), oculto para Autitos y Just Dance
        if (esMinicine) {
            etPelicula.visibility = View.VISIBLE
            etPelicula.hint = "Película / Contenido"
        } else {
            etPelicula.visibility = View.GONE
            etPelicula.setText("")
        }

        val rbPagoMixto = dialogView.findViewById<RadioButton>(R.id.rbPagoMixto)
        val llSeccionMixto = dialogView.findViewById<LinearLayout>(R.id.llSeccionMixto)
        val etMontoEfectivoMixto = dialogView.findViewById<EditText>(R.id.etMontoEfectivoMixto)
        val tvMontoQRMixto = dialogView.findViewById<TextView>(R.id.tvMontoQRMixto)

        fun recalcularMixto() {
            val total = sala.calcularTotalGeneral()
            val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
            val saldoQR = maxOf(0.0, total - efec)
            tvMontoQRMixto?.text = "📱 Saldo en QR: $saldoQR Bs"
        }

        etMontoEfectivoMixto?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalcularMixto() }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (esNueva) {
            etCliente.setText("")
            etCelular.setText("")
            etPelicula.setText("")
            val precioBase = when {
                esMinicine -> "35"
                esAutitos -> "15"
                else -> "15"
            }
            etPrecioSala.setText(precioBase)
            sala.precioCobrado = precioBase.toDouble()
            rbPagoEfectivo.isChecked = true
            rutaComprobanteActual = null
        } else {
            etPrecioSala.setText(sala.precioCobrado.toString())
            when {
                sala.metodoPago.startsWith("Mixto", ignoreCase = true) -> {
                    rbPagoMixto?.isChecked = true
                    llSeccionQR.visibility = View.VISIBLE
                    llSeccionMixto?.visibility = View.VISIBLE
                    val efec = sala.getEfectivoAmount()
                    if (efec > 0.0) {
                        etMontoEfectivoMixto?.setText(if (efec % 1.0 == 0.0) efec.toLong().toString() else efec.toString())
                    }
                }
                sala.metodoPago.equals("QR", ignoreCase = true) -> {
                    rbPagoQR.isChecked = true
                    llSeccionQR.visibility = View.VISIBLE
                }
                else -> {
                    rbPagoEfectivo.isChecked = true
                }
            }
        }

        if (reservaPrevia != null) {
            etCliente.setText(reservaPrevia.nombre)
            etCelular.setText(reservaPrevia.celular)
            etTiempo.setText(reservaPrevia.duracion)
            when {
                reservaPrevia.metodoPagoAdelanto.startsWith("Mixto", ignoreCase = true) -> {
                    rbPagoMixto?.isChecked = true
                    llSeccionQR.visibility = View.VISIBLE
                    llSeccionMixto?.visibility = View.VISIBLE
                }
                reservaPrevia.metodoPagoAdelanto.equals("QR", ignoreCase = true) -> {
                    rbPagoQR.isChecked = true
                    llSeccionQR.visibility = View.VISIBLE
                }
            }
            if (reservaPrevia.comprobanteUri != null) {
                rutaComprobanteActual = reservaPrevia.comprobanteUri
            }
        }

        fun refrescarTotalUI() {
            val precioS = etPrecioSala.text.toString().toDoubleOrNull() ?: 0.0
            sala.precioCobrado = precioS
            tvTotalCuenta.text = "TOTAL: ${sala.calcularTotalGeneral()} Bs"
            recalcularMixto()
        }

        fun actualizarVistaPreviaQR(fullPath: String?) {
            if (!fullPath.isNullOrEmpty()) {
                val rutas = ImageUtils.separarRutasComprobantes(fullPath)
                if (rutas.isNotEmpty()) {
                    val ultimaRuta = rutas.last()
                    val bitmap = ImageUtils.obtenerBitmap(this, ultimaRuta)
                    if (bitmap != null) {
                        ivPreviewQR.setImageBitmap(bitmap)
                    }
                    val tvEstado = dialogView.findViewById<TextView>(R.id.tvEstadoFotoQR)
                    val tvVer = dialogView.findViewById<TextView>(R.id.tvVerFotoQR)
                    if (rutas.size > 1) {
                        tvEstado?.text = "✅ ${rutas.size} comprobantes adjuntados"
                        tvVer?.text = "🔍 Toca para ver las ${rutas.size} fotos"
                    } else {
                        tvEstado?.text = "✅ Comprobante adjuntado"
                        tvVer?.text = "🔍 Toca para ampliar"
                    }
                    rlPreviewQR.visibility = View.VISIBLE
                } else {
                    rlPreviewQR.visibility = View.GONE
                }
            } else {
                rlPreviewQR.visibility = View.GONE
            }
        }

        actualizarVistaPreviaQR(rutaComprobanteActual)

        rgMetodoPago.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPagoQR -> {
                    llSeccionQR.visibility = View.VISIBLE
                    llSeccionMixto?.visibility = View.GONE
                }
                R.id.rbPagoMixto -> {
                    llSeccionQR.visibility = View.VISIBLE
                    llSeccionMixto?.visibility = View.VISIBLE
                    recalcularMixto()
                }
                else -> {
                    llSeccionQR.visibility = View.GONE
                    llSeccionMixto?.visibility = View.GONE
                }
            }
        }

        btnTomarFotoQR.setOnClickListener {
            capturarFotoCamara { path ->
                if (path != null) {
                    rutaComprobanteActual = ImageUtils.agregarComprobante(rutaComprobanteActual, path)
                    actualizarVistaPreviaQR(rutaComprobanteActual)
                }
            }
        }

        btnGaleriaQR.setOnClickListener {
            seleccionarFotoGaleria { path ->
                if (path != null) {
                    rutaComprobanteActual = ImageUtils.agregarComprobante(rutaComprobanteActual, path)
                    actualizarVistaPreviaQR(rutaComprobanteActual)
                }
            }
        }

        tvVerFotoQR.setOnClickListener {
            if (rutaComprobanteActual != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, rutaComprobanteActual!!, "Comprobante: ${sala.nombre}") { nuevaCadena ->
                    rutaComprobanteActual = nuevaCadena
                    actualizarVistaPreviaQR(nuevaCadena)
                }
            }
        }

        btnEliminarFotoQR.setOnClickListener {
            if (rutaComprobanteActual != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, rutaComprobanteActual!!, "Comprobante: ${sala.nombre}") { nuevaCadena ->
                    rutaComprobanteActual = nuevaCadena
                    actualizarVistaPreviaQR(nuevaCadena)
                }
            }
        }

        etPrecioSala.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refrescarTotalUI() }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun dibujarCheckboxPedido(pedido: Pedido) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val cb = CheckBox(this).apply {
                text = "${pedido.cantidad}x ${pedido.nombre} (${pedido.subtotal()} Bs)"
                isChecked = pedido.estaEntregado
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, isChecked ->
                    pedido.estaEntregado = isChecked
                    database.child("salas").child(sala.id.toString()).setValue(sala)
                }
            }

            val btnEliminar = TextView(this).apply {
                text = "🗑️"
                textSize = 20f
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    sala.pedidos.remove(pedido)
                    llListaPedidos.removeView(fila)
                    refrescarTotalUI()
                    if (!esNueva) {
                        database.child("salas").child(sala.id.toString()).setValue(sala)
                        if (pedido.idFirebase.isNotEmpty()) {
                            database.child("registros").child(pedido.idFirebase).removeValue()
                        }
                    }
                }
            }
            fila.addView(cb)
            fila.addView(btnEliminar)
            llListaPedidos.addView(fila)
        }

        if (!esNueva) {
            etCliente.setText(sala.cliente)
            etCelular.setText(sala.celular)
            if (esMinicine) {
                etPelicula.setText(sala.pelicula)
            }

            sala.tiempoRestanteMillis?.let {
                val h = (it / (1000 * 60 * 60)) % 24
                val m = (it / (1000 * 60)) % 60
                etTiempo.setText(String.format("%d:%02d", h, m))
            }
            btnCancelar.text = "Liberar Sala"
            btnCancelar.setTextColor(Color.parseColor("#FF5252"))
        }

        sala.pedidos.forEach { dibujarCheckboxPedido(it) }
        refrescarTotalUI()

        btnAgregarPedido.setOnClickListener {
            val cantStr = etPedCantidad.text.toString().trim()
            val nomStr = etPedNombre.text.toString().trim()
            val precStr = etPedPrecio.text.toString().trim()

            if (cantStr.isNotEmpty() && nomStr.isNotEmpty() && precStr.isNotEmpty()) {
                val nuevoId = database.child("registros").push().key ?: ""
                val nuevoPedido = Pedido(nuevoId, cantStr.toInt(), nomStr, precStr.toDouble(), false)
                sala.pedidos.add(nuevoPedido)
                dibujarCheckboxPedido(nuevoPedido)
                refrescarTotalUI()

                if (!esNueva) {
                    database.child("salas").child(sala.id.toString()).setValue(sala)
                    
                    val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)
                    val pEntrada = RegistroEntry(
                        idFirebase = nuevoId,
                        tipo = "Consumo",
                        nombreElemento = sala.nombre,
                        cliente = sala.cliente ?: "",
                        celular = sala.celular,
                        peliculaJuego = sala.pelicula,
                        tiempoConfigurado = "N/A",
                        fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                        pedidos = "${nuevoPedido.cantidad}x ${nuevoPedido.nombre}",
                        totalCobrado = nuevoPedido.subtotal(),
                        metodoPago = sala.metodoPago,
                        comprobanteUri = sala.comprobanteUri,
                        creadoPor = usuarioActual,
                        eliminado = false
                    )
                    database.child("registros").child(nuevoId).setValue(pEntrada)
                    database.child("auditoria_registros").child(nuevoId).setValue(pEntrada)
                }
                etPedCantidad.text.clear()
                etPedNombre.text.clear()
                etPedPrecio.text.clear()
            } else {
                Toast.makeText(this, "Llene todos los datos del producto", Toast.LENGTH_SHORT).show()
            }
        }

        fun ejecutarGuardadoSala(nombreCliente: String, celularCliente: String, tiempoStr: String, metodoPago: String) {
            sala.cliente = nombreCliente
            sala.celular = celularCliente
            sala.pelicula = if (esMinicine) etPelicula.text.toString().trim() else "N/A"
            sala.estado = EstadoSala.OCUPADA
            sala.precioCobrado = etPrecioSala.text.toString().toDoubleOrNull() ?: 0.0
            sala.metodoPago = metodoPago
            sala.comprobanteUri = if (metodoPago.contains("QR", ignoreCase = true) || metodoPago.startsWith("Mixto", ignoreCase = true)) rutaComprobanteActual else null

            val montoEfec = if (metodoPago.startsWith("Mixto", ignoreCase = true)) (etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0) else if (metodoPago == "Efectivo") sala.precioCobrado else 0.0
            val montoQR = if (metodoPago.startsWith("Mixto", ignoreCase = true)) maxOf(0.0, sala.precioCobrado - montoEfec) else if (metodoPago.contains("QR", ignoreCase = true)) sala.precioCobrado else 0.0
            sala.montoEfectivo = montoEfec
            sala.montoQR = montoQR

            val duracionMillis = convertirTiempoAMilisegundos(tiempoStr)
            sala.tiempoRestanteMillis = duracionMillis

            if (duracionMillis != null && duracionMillis > 0L) {
                val ahora = System.currentTimeMillis()
                val tiempoFin = ahora + duracionMillis
                sala.endTimeMillis = tiempoFin

                val cal = Calendar.getInstance()
                cal.timeInMillis = tiempoFin
                sala.horaFinProgramada = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

                AlarmUtils.programarAlarmaSala(this, sala.id, sala.nombre, sala.cliente, tiempoFin)
            } else {
                sala.horaFinProgramada = "Libre"
                sala.endTimeMillis = null
                AlarmUtils.cancelarAlarmaSala(this, sala.id)
            }

            // GUARDAR EN FIREBASE
            database.child("salas").child(sala.id.toString()).setValue(sala)

            val tipoSala = when {
                esMinicine -> "Minicine"
                esAutitos -> "Autitos"
                else -> "Just Dance"
            }
            val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)

            // Registrar en Auditoría de Salas con Trazabilidad de Dispositivo
            roomAuditManager.registrarAccionSala(
                tipoAccion = if (esNueva) "INICIO_TIEMPO" else "MODIFICAR_SALA",
                salaId = sala.id.toString(),
                salaNombre = sala.nombre,
                categoria = tipoSala,
                tiempoMinutos = ((duracionMillis ?: 0L) / (60 * 1000)).toInt(),
                usuarioNombre = usuarioActual,
                usuarioRol = "Empleado",
                detalles = "Cliente: ${sala.cliente ?: "N/A"} | Precio: ${sala.precioCobrado} Bs ($metodoPago)"
            )

            if (esNueva) {
                val fechaHoraActual = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val key = database.child("registros").push().key ?: ""
                val nuevaEntrada = RegistroEntry(
                    idFirebase = key,
                    tipo = tipoSala,
                    nombreElemento = sala.nombre,
                    cliente = nombreCliente,
                    celular = sala.celular,
                    peliculaJuego = sala.pelicula,
                    tiempoConfigurado = if (tiempoStr.isNotEmpty()) tiempoStr else "Continuo",
                    fechaHora = fechaHoraActual,
                    pedidos = "Alquiler de Sala",
                    totalCobrado = sala.precioCobrado,
                    metodoPago = metodoPago,
                    montoEfectivo = montoEfec,
                    montoQR = montoQR,
                    comprobanteUri = if (metodoPago != "Efectivo") rutaComprobanteActual else null,
                    cantidadVolantes = if (esAutitos) (reservaPrevia?.cantidadVolantes ?: 1) else null,
                    creadoPor = usuarioActual,
                    eliminado = false
                )
                database.child("registros").child(key).setValue(nuevaEntrada)
                database.child("auditoria_registros").child(key).setValue(nuevaEntrada)
                
                // Pedidos separados
                for (pedido in sala.pedidos) {
                    val pKey = database.child("registros").push().key ?: continue
                    val pEntrada = RegistroEntry(
                        idFirebase = pKey,
                        tipo = "Consumo",
                        nombreElemento = sala.nombre,
                        cliente = nombreCliente,
                        celular = sala.celular,
                        peliculaJuego = sala.pelicula,
                        tiempoConfigurado = "N/A",
                        fechaHora = fechaHoraActual,
                        pedidos = "${pedido.cantidad}x ${pedido.nombre}",
                        totalCobrado = pedido.subtotal(),
                        metodoPago = metodoPago,
                        montoEfectivo = if (metodoPago == "Efectivo") pedido.subtotal() else 0.0,
                        montoQR = if (metodoPago.contains("QR", true)) pedido.subtotal() else 0.0,
                        comprobanteUri = if (metodoPago != "Efectivo") rutaComprobanteActual else null,
                        creadoPor = usuarioActual,
                        eliminado = false
                    )
                    database.child("registros").child(pKey).setValue(pEntrada)
                    database.child("auditoria_registros").child(pKey).setValue(pEntrada)
                }

            } else {
                actualizarRegistroDeSala(sala)
            }

            if (reservaPrevia != null) {
                database.child("reservas").child(reservaPrevia.idFirebase).removeValue()
            }

            dialog.dismiss()
        }

        btnConfirmar.setOnClickListener {
            val nombreCliente = etCliente.text.toString().trim()
            val celularCliente = etCelular.text.toString().trim()
            val tiempoStr = etTiempo.text.toString().trim()
            val metodoPago = when {
                rbPagoQR.isChecked -> "QR"
                rbPagoMixto?.isChecked == true -> {
                    val total = sala.calcularTotalGeneral()
                    val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val saldoQR = maxOf(0.0, total - efec)
                    "Mixto (💵 $efec Bs | 📱 $saldoQR Bs)"
                }
                else -> "Efectivo"
            }

            if (nombreCliente.isEmpty()) {
                etCliente.error = "Campo obligatorio"
                return@setOnClickListener
            }

            fun validarComprobanteYGuardar() {
                if ((rbPagoQR.isChecked || rbPagoMixto?.isChecked == true) && rutaComprobanteActual.isNullOrEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("📸 Comprobante QR Requerido")
                        .setMessage("Has seleccionado método de pago por $metodoPago pero aún no has adjuntado una foto del comprobante.\n\n¿Deseas capturar o seleccionar la foto del comprobante ahora para la auditoría?")
                        .setPositiveButton("📷 Tomar Foto") { _, _ ->
                            capturarFotoCamara { path ->
                                if (path != null) {
                                    rutaComprobanteActual = ImageUtils.agregarComprobante(rutaComprobanteActual, path)
                                    actualizarVistaPreviaQR(rutaComprobanteActual)
                                }
                            }
                        }
                        .setNeutralButton("🖼️ Galería") { _, _ ->
                            seleccionarFotoGaleria { path ->
                                if (path != null) {
                                    rutaComprobanteActual = ImageUtils.agregarComprobante(rutaComprobanteActual, path)
                                    actualizarVistaPreviaQR(rutaComprobanteActual)
                                }
                            }
                        }
                        .setNegativeButton("Continuar sin foto") { _, _ ->
                            ejecutarGuardadoSala(nombreCliente, celularCliente, tiempoStr, metodoPago)
                        }
                        .show()
                } else {
                    ejecutarGuardadoSala(nombreCliente, celularCliente, tiempoStr, metodoPago)
                }
            }

            // COMPROBAR SI ESTÁ VETADO Y PERMITIR ASIGNAR CON ADVERTENCIA
            if (celularCliente.isNotEmpty()) {
                val vetadoEncontrado = listaVetadosGlobal.firstOrNull { it.celular == celularCliente }
                if (vetadoEncontrado != null) {
                    val motivoTexto = if (vetadoEncontrado.motivo.isNotEmpty()) vetadoEncontrado.motivo else "Sin notas especificadas"
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ ADVERTENCIA: CLIENTE VETADO")
                        .setMessage("El cliente con celular $celularCliente (${vetadoEncontrado.nombre ?: "Desconocido"}) está en la lista de vetados.\n\nMotivo/Notas: $motivoTexto\n\n¿Deseas asignarle la sala de todas formas bajo advertencia?")
                        .setPositiveButton("⚠️ Asignar de todas formas") { _, _ ->
                            validarComprobanteYGuardar()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    return@setOnClickListener
                }
            }

            validarComprobanteYGuardar()
        }

        btnCancelar.setOnClickListener {
            if (!esNueva) {
                AlarmUtils.cancelarAlarmaSala(this, sala.id)
                sala.estado = EstadoSala.DISPONIBLE
                sala.cliente = null
                sala.celular = null
                sala.pelicula = null
                sala.tiempoRestanteMillis = null
                sala.horaFinProgramada = null
                sala.endTimeMillis = null
                sala.precioCobrado = 0.0
                sala.metodoPago = "Efectivo"
                sala.comprobanteUri = null
                sala.pedidos.clear()

                database.child("salas").child(sala.id.toString()).setValue(sala)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarDialogoEditarReserva(reserva: Reserva) { mostrarDialogoReservasConfigurado(reserva) }
    private fun mostrarDialogoNuevaReserva() { mostrarDialogoReservasConfigurado(null) }

    private fun mostrarDialogoReservasConfigurado(reservaExistente: Reserva?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nueva_reserva, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etNombre = dialogView.findViewById<EditText>(R.id.etResNombre)
        val etCelular = dialogView.findViewById<EditText>(R.id.etResCelular)
        val btnFecha = dialogView.findViewById<Button>(R.id.btnResFecha)
        val btnHora = dialogView.findViewById<Button>(R.id.btnResHora)
        val etDuracion = dialogView.findViewById<EditText>(R.id.etResDuracion)
        val llResVolantesContainer = dialogView.findViewById<View>(R.id.llResVolantesContainer)
        val etVolantes = dialogView.findViewById<EditText>(R.id.etResVolantes)
        val rgTipo = dialogView.findViewById<RadioGroup>(R.id.rgResTipo)
        val etAdelanto = dialogView.findViewById<EditText>(R.id.etResAdelanto)

        // METODO PAGO ADELANTO Y QR
        val rgResMetodoPago = dialogView.findViewById<RadioGroup>(R.id.rgResMetodoPago)
        val rbResPagoEfectivo = dialogView.findViewById<RadioButton>(R.id.rbResPagoEfectivo)
        val rbResPagoQR = dialogView.findViewById<RadioButton>(R.id.rbResPagoQR)
        val llResSeccionQR = dialogView.findViewById<LinearLayout>(R.id.llResSeccionQR)
        val btnResTomarFotoQR = dialogView.findViewById<Button>(R.id.btnResTomarFotoQR)
        val btnResGaleriaQR = dialogView.findViewById<Button>(R.id.btnResGaleriaQR)
        val rlResPreviewQR = dialogView.findViewById<View>(R.id.rlResPreviewQR)
        val ivResPreviewQR = dialogView.findViewById<ImageView>(R.id.ivResPreviewQR)
        val tvResVerFotoQR = dialogView.findViewById<TextView>(R.id.tvResVerFotoQR)
        val btnResEliminarFotoQR = dialogView.findViewById<TextView>(R.id.btnResEliminarFotoQR)

        var fechaSeleccionada = ""
        var horaSeleccionada = ""
        var resComprobantePath: String? = reservaExistente?.comprobanteUri

        fun actualizarVistaPreviaResQR(fullPath: String?) {
            if (!fullPath.isNullOrEmpty()) {
                val rutas = ImageUtils.separarRutasComprobantes(fullPath)
                if (rutas.isNotEmpty()) {
                    val ultimaRuta = rutas.last()
                    val bitmap = ImageUtils.obtenerBitmap(this, ultimaRuta)
                    if (bitmap != null) {
                        ivResPreviewQR.setImageBitmap(bitmap)
                    }
                    val tvEstado = dialogView.findViewById<TextView>(R.id.tvResEstadoFotoQR)
                    val tvVer = dialogView.findViewById<TextView>(R.id.tvResVerFotoQR)
                    if (rutas.size > 1) {
                        tvEstado?.text = "✅ ${rutas.size} comprobantes adjuntados"
                        tvVer?.text = "🔍 Toca para ver las ${rutas.size} fotos"
                    } else {
                        tvEstado?.text = "✅ Comprobante adjuntado"
                        tvVer?.text = "🔍 Toca para ampliar"
                    }
                    rlResPreviewQR.visibility = View.VISIBLE
                } else {
                    rlResPreviewQR.visibility = View.GONE
                }
            } else {
                rlResPreviewQR.visibility = View.GONE
            }
        }

        fun actualizarVisibilidadVolantes(checkedId: Int) {
            if (checkedId == R.id.rbResAuto) {
                llResVolantesContainer?.visibility = View.VISIBLE
            } else {
                llResVolantesContainer?.visibility = View.GONE
            }
            if (checkedId == R.id.rbResCine) {
                etDuracion?.visibility = View.GONE
            } else {
                etDuracion?.visibility = View.VISIBLE
                if (etDuracion?.text.toString().trim().isEmpty()) {
                    etDuracion?.setText("1:00")
                }
            }
        }

        rgTipo.setOnCheckedChangeListener { _, checkedId ->
            actualizarVisibilidadVolantes(checkedId)
        }

        val rbResPagoMixto = dialogView.findViewById<RadioButton>(R.id.rbResPagoMixto)
        val llResSeccionMixto = dialogView.findViewById<LinearLayout>(R.id.llResSeccionMixto)
        val etResMontoEfectivoMixto = dialogView.findViewById<EditText>(R.id.etResMontoEfectivoMixto)
        val tvResMontoQRMixto = dialogView.findViewById<TextView>(R.id.tvResMontoQRMixto)

        fun recalcularResMixto() {
            val total = etAdelanto.text.toString().toDoubleOrNull() ?: 0.0
            val efec = etResMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
            val saldoQR = maxOf(0.0, total - efec)
            tvResMontoQRMixto?.text = "📱 Saldo en QR: $saldoQR Bs"
        }

        etResMontoEfectivoMixto?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalcularResMixto() }
            override fun afterTextChanged(s: Editable?) {}
        })

        etAdelanto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalcularResMixto() }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (reservaExistente != null) {
            etNombre.setText(reservaExistente.nombre)
            etCelular.setText(reservaExistente.celular)
            btnFecha.text = "🗓️ ${reservaExistente.fecha}"
            btnHora.text = "⏰ ${reservaExistente.horaProgramada}"
            etDuracion.setText(reservaExistente.duracion)
            etVolantes.setText(reservaExistente.cantidadVolantes.toString())
            etAdelanto.setText(reservaExistente.adelanto)
            fechaSeleccionada = reservaExistente.fecha
            horaSeleccionada = reservaExistente.horaProgramada

            when (reservaExistente.tipoSala) {
                "Minicine" -> rgTipo.check(R.id.rbResCine)
                "Autitos" -> rgTipo.check(R.id.rbResAuto)
                else -> rgTipo.check(R.id.rbResDance)
            }
            actualizarVisibilidadVolantes(rgTipo.checkedRadioButtonId)

            when {
                reservaExistente.metodoPagoAdelanto.startsWith("Mixto", ignoreCase = true) -> {
                    rbResPagoMixto?.isChecked = true
                    llResSeccionQR.visibility = View.VISIBLE
                    llResSeccionMixto?.visibility = View.VISIBLE
                    recalcularResMixto()
                }
                reservaExistente.metodoPagoAdelanto.equals("QR", ignoreCase = true) -> {
                    rbResPagoQR.isChecked = true
                    llResSeccionQR.visibility = View.VISIBLE
                }
                else -> {
                    rbResPagoEfectivo.isChecked = true
                }
            }
            actualizarVistaPreviaResQR(resComprobantePath)
        } else {
            rgTipo.check(R.id.rbResCine)
            actualizarVisibilidadVolantes(R.id.rbResCine)
            etVolantes.setText("1")
        }

        rgResMetodoPago.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbResPagoQR -> {
                    llResSeccionQR.visibility = View.VISIBLE
                    llResSeccionMixto?.visibility = View.GONE
                }
                R.id.rbResPagoMixto -> {
                    llResSeccionQR.visibility = View.VISIBLE
                    llResSeccionMixto?.visibility = View.VISIBLE
                    recalcularResMixto()
                }
                else -> {
                    llResSeccionQR.visibility = View.GONE
                    llResSeccionMixto?.visibility = View.GONE
                }
            }
        }

        btnResTomarFotoQR.setOnClickListener {
            capturarFotoCamara { path ->
                if (path != null) {
                    resComprobantePath = ImageUtils.agregarComprobante(resComprobantePath, path)
                    actualizarVistaPreviaResQR(resComprobantePath)
                }
            }
        }

        btnResGaleriaQR.setOnClickListener {
            seleccionarFotoGaleria { path ->
                if (path != null) {
                    resComprobantePath = ImageUtils.agregarComprobante(resComprobantePath, path)
                    actualizarVistaPreviaResQR(resComprobantePath)
                }
            }
        }

        tvResVerFotoQR.setOnClickListener {
            if (resComprobantePath != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, resComprobantePath!!, "Comprobante Adelanto: ${etNombre.text}") { nuevaCadena ->
                    resComprobantePath = nuevaCadena
                    actualizarVistaPreviaResQR(nuevaCadena)
                }
            }
        }

        btnResEliminarFotoQR.setOnClickListener {
            if (resComprobantePath != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, resComprobantePath!!, "Comprobante Adelanto: ${etNombre.text}") { nuevaCadena ->
                    resComprobantePath = nuevaCadena
                    actualizarVistaPreviaResQR(nuevaCadena)
                }
            }
        }

        btnFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                fechaSeleccionada = String.format("%02d/%02d/%d", day, month + 1, year)
                btnFecha.text = "🗓️ $fechaSeleccionada"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnHora.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                horaSeleccionada = String.format("%02d:%02d", hour, minute)
                btnHora.text = "⏰ $horaSeleccionada"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        val btnGuardar = dialogView.findViewById<Button>(R.id.btnResGuardar)
        btnGuardar.text = if (reservaExistente == null) "Guardar Reserva" else "Actualizar"

        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val celular = etCelular.text.toString().trim()
            val tipoSala = when (rgTipo.checkedRadioButtonId) {
                R.id.rbResCine -> "Minicine"
                R.id.rbResAuto -> "Autitos"
                else -> "Just Dance"
            }
            val volantesCant = if (tipoSala == "Autitos") (etVolantes.text.toString().trim().toIntOrNull() ?: 1) else 1
            val metodoPagoAdelanto = when {
                rbResPagoQR.isChecked -> "QR"
                rbResPagoMixto?.isChecked == true -> {
                    val total = etAdelanto.text.toString().toDoubleOrNull() ?: 0.0
                    val efec = etResMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val saldoQR = maxOf(0.0, total - efec)
                    "Mixto (${efec} Efec / ${saldoQR} QR)"
                }
                else -> "Efectivo"
            }

            if (nombre.isEmpty() || fechaSeleccionada.isEmpty() || horaSeleccionada.isEmpty()) {
                Toast.makeText(this, "Faltan datos obligatorios (Nombre, Fecha, Hora)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // AVISO SI ESTÁ VETADO
            if (celular.isNotEmpty()) {
                val vetadoEncontrado = listaVetadosGlobal.firstOrNull { it.celular == celular }
                if (vetadoEncontrado != null) {
                    val motivoTexto = if (vetadoEncontrado.motivo.isNotEmpty()) vetadoEncontrado.motivo else "Sin notas especificadas"
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ ADVERTENCIA: CLIENTE VETADO")
                        .setMessage("El cliente $nombre con celular $celular está VETADO.\n\nMotivo/Notas: $motivoTexto\n\n¿Deseas guardar la reserva de todas formas?")
                        .setPositiveButton("Guardar con Advertencia") { _, _ ->
                            guardarReservaFinal(reservaExistente, nombre, celular, fechaSeleccionada, horaSeleccionada, tipoSala, etAdelanto, etDuracion, volantesCant, metodoPagoAdelanto, resComprobantePath)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    return@setOnClickListener
                }
            }

            guardarReservaFinal(reservaExistente, nombre, celular, fechaSeleccionada, horaSeleccionada, tipoSala, etAdelanto, etDuracion, volantesCant, metodoPagoAdelanto, resComprobantePath)
            dialog.dismiss()
        }

        val btnCancelar = dialogView.findViewById<Button>(R.id.btnResCancelar)
        if (reservaExistente != null) {
            btnCancelar.text = "Eliminar"
            btnCancelar.setTextColor(Color.parseColor("#FF5252"))
            btnCancelar.setOnClickListener {
                database.child("reservas").child(reservaExistente.idFirebase).removeValue()
                dialog.dismiss()
            }
        } else {
            btnCancelar.setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    private fun guardarReservaFinal(
        reservaExistente: Reserva?,
        nombre: String,
        celular: String,
        fechaSeleccionada: String,
        horaSeleccionada: String,
        tipoSala: String,
        etAdelanto: EditText,
        etDuracion: EditText,
        volantesCant: Int,
        metodoPagoAdelanto: String,
        comprobantePath: String?
    ) {
        if (reservaExistente == null) {
            val key = database.child("reservas").push().key ?: ""
            val nuevaReserva = Reserva(
                idFirebase = key,
                nombre = nombre,
                celular = celular,
                fecha = fechaSeleccionada,
                horaProgramada = horaSeleccionada,
                tipoSala = tipoSala,
                adelanto = etAdelanto.text.toString().trim(),
                metodoPagoAdelanto = metodoPagoAdelanto,
                comprobanteUri = if (metodoPagoAdelanto != "Efectivo") comprobantePath else null,
                duracion = etDuracion.text.toString().trim(),
                cantidadVolantes = volantesCant
            )
            database.child("reservas").child(key).setValue(nuevaReserva)
        } else {
            reservaExistente.nombre = nombre
            reservaExistente.celular = celular
            reservaExistente.fecha = fechaSeleccionada
            reservaExistente.horaProgramada = horaSeleccionada
            reservaExistente.tipoSala = tipoSala
            reservaExistente.adelanto = etAdelanto.text.toString().trim()
            reservaExistente.metodoPagoAdelanto = metodoPagoAdelanto
            reservaExistente.comprobanteUri = if (metodoPagoAdelanto != "Efectivo") comprobantePath else null
            reservaExistente.duracion = etDuracion.text.toString().trim()
            reservaExistente.cantidadVolantes = volantesCant
            database.child("reservas").child(reservaExistente.idFirebase).setValue(reservaExistente)
        }
    }

    private fun exportarCSV(listaAExportar: List<RegistroEntry>) {
        val builder = StringBuilder()
        builder.append("Fecha/Hora,Tipo,Sala/Elemento,Cliente,Celular,Pelicula/Juego,Tiempo,Pedidos,Metodo Pago,Volantes,Total (Bs)\n")

        listaAExportar.forEach {
            builder.append("\"${it.fechaHora}\",\"${it.tipo}\",\"${it.nombreElemento}\",\"${it.cliente}\",\"${it.celular ?: "S/N"}\",\"${it.peliculaJuego ?: "N/A"}\",\"${it.tiempoConfigurado ?: "N/A"}\",\"${it.pedidos}\",\"${it.metodoPago}\",\"${it.cantidadVolantes ?: 1}\",\"${it.totalCobrado}\"\n")
        }

        try {
            val file = File(cacheDir, "Reporte_JustPlay.csv")
            file.writeText(builder.toString())

            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Exportar Reporte a..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::otgScanner.isInitialized && otgScanner.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun mostrarDialogoVentaOTGEmergencia(barcode: String) {
        database.child("productos_catalogo").child(barcode).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("🔍 Producto No Encontrado")
                        .setMessage("El código de barras '$barcode' no está registrado en el catálogo.\n¿Deseas agregarlo ahora?")
                        .setPositiveButton("➕ Agregar") { _, _ ->
                            mostrarDialogoCrearEditarProductoEmpleado(codigoExistente = barcode)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    return
                }

                val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Producto $barcode"
                val precio = snapshot.child("precio_unitario").getValue(Double::class.java) ?: 0.0
                val stockActual = snapshot.child("stock_actual").getValue(Int::class.java) ?: 0
                val categoria = snapshot.child("categoria").getValue(String::class.java) ?: "General"

                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 20, 24, 20)
                    setBackgroundColor(Color.parseColor("#1A1C29"))
                }

                val tvInfo = TextView(this@MainActivity).apply {
                    text = "📦 $nombre\n🏷️ Precio: $precio Bs\n📊 Stock Disponible: $stockActual\n📱 Lector: USB-OTG Móvil"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                }

                val etCantidad = EditText(this@MainActivity).apply {
                    hint = "Cantidad a vender (Ej. 1)"
                    setText("1")
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.parseColor("#888888"))
                }

                val rgMetodo = RadioGroup(this@MainActivity).apply {
                    orientation = RadioGroup.HORIZONTAL
                    setPadding(0, 10, 0, 10)
                }
                val rbEfectivo = RadioButton(this@MainActivity).apply {
                    text = "Efectivo"
                    isChecked = true
                    setTextColor(Color.WHITE)
                }
                val rbQr = RadioButton(this@MainActivity).apply {
                    text = "QR"
                    setTextColor(Color.WHITE)
                }
                rgMetodo.addView(rbEfectivo)
                rgMetodo.addView(rbQr)

                container.addView(tvInfo)
                container.addView(etCantidad)
                container.addView(rgMetodo)

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("🛒 Venta Rápida por Lector OTG (Empleado)")
                    .setView(container)
                    .setPositiveButton("Registrar Venta") { _, _ ->
                        val cant = etCantidad.text.toString().toIntOrNull() ?: 1
                        if (cant <= 0) {
                            Toast.makeText(this@MainActivity, "Cantidad inválida", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        if (stockActual < cant) {
                            Toast.makeText(this@MainActivity, "Stock insuficiente ($stockActual disponibles)", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        val metodo = if (rbEfectivo.isChecked) "Efectivo" else "QR"
                        val total = precio * cant
                        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                        // 1. Reducir stock
                        val nuevoStock = stockActual - cant
                        database.child("productos_catalogo").child(barcode).child("stock_actual").setValue(nuevoStock)

                        // 2. Registrar venta
                        val regKey = database.child("registros").push().key ?: ""
                        val ventaEntry = RegistroEntry(
                            idFirebase = regKey,
                            tipo = "Venta Directa",
                            nombreElemento = "Venta OTG Mostrador",
                            cliente = "Venta Mostrador OTG",
                            celular = "S/N",
                            fechaHora = now,
                            pedidos = "${cant}x $nombre ($total Bs)",
                            totalCobrado = total,
                            metodoPago = metodo,
                            creadoPor = "${PresenciaManager.obtenerNombreUsuario(this@MainActivity).ifEmpty { "Empleado" }} (OTG ${DeviceInfoProvider.getDeviceModel()})",
                            eliminado = false
                        )
                        database.child("registros").child(regKey).setValue(ventaEntry)

                        // 3. Auditoría de producto
                        productAuditManager.registrarAuditoriaProducto(
                            tipoOperacion = "VENTA_DESCUENTO",
                            codigoBarras = barcode,
                            nombreProducto = nombre,
                            categoria = categoria,
                            precioAnterior = precio,
                            precioNuevo = precio,
                            stockAnterior = stockActual,
                            stockNuevo = nuevoStock,
                            usuarioNombre = PresenciaManager.obtenerNombreUsuario(this@MainActivity).ifEmpty { "Empleado" },
                            usuarioRol = "Empleado",
                            detalles = "Venta OTG de $cant unidad(es) por $total Bs ($metodo)"
                        )

                        Toast.makeText(this@MainActivity, "Venta registrada exitosamente (${total} Bs)", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}