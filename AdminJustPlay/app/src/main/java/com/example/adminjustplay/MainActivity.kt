package com.example.adminjustplay

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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var layoutTablero: View
    private lateinit var layoutRegistro: View
    private lateinit var layoutReservasGenerales: View
    private lateinit var layoutVetados: View
    private lateinit var layoutUsuariosOnline: View
    private lateinit var tvTituloToolbar: TextView
    private lateinit var tvBadgeOffline: TextView
    private lateinit var tvBadgeUsuariosOnline: TextView

    private var estaConectadoInternet: Boolean = true

    private lateinit var tvMinicineOcupadas: TextView
    private lateinit var tvAutitosOcupados: TextView
    private lateinit var tvJustDanceOcupados: TextView

    private val handlerRelojes = Handler(Looper.getMainLooper())
    private lateinit var runnableRelojes: Runnable

    private var salasMinicine: List<Sala> = emptyList()
    private var salasAutitos: List<Sala> = emptyList()
    private var salasJustDance: List<Sala> = emptyList()

    // REGISTRO Y AUDITORIA
    private val historialCompleto = mutableListOf<RegistroEntry>()
    private val historialFiltrado = mutableListOf<RegistroEntry>()
    private lateinit var registroAdapter: RegistroAdminAdapter
    private var modoAuditoria: String = "ACTIVOS" // "ACTIVOS", "BORRADOS", "TODOS"

    // RESERVAS
    private val listaReservasGlobales = mutableListOf<Reserva>()
    private val reservasMinicineList = mutableListOf<Reserva>()
    private val reservasAutitosList = mutableListOf<Reserva>()
    private val reservasDanceList = mutableListOf<Reserva>()

    private lateinit var adapterResMinicine: ReservasAdapter
    private lateinit var adapterResAutitos: ReservasAdapter
    private lateinit var adapterResDance: ReservasAdapter

    // VETADOS
    private val listaVetadosGlobal = mutableListOf<ClienteVetado>()
    private lateinit var vetadosAdapter: VetadosAdapter

    // GESTIÓN DE TERMINALES POS Y AUDITORÍAS (SOLID: Single Responsibility)
    private lateinit var posManager: PosManagementManager
    private lateinit var roomAuditManager: RoomAuditManager
    private lateinit var productAuditManager: ProductAuditManager
    private lateinit var egresosManager: EgresosManager
    private lateinit var qrValidationHelper: QrPaymentValidationHelper
    private lateinit var otgScanner: OtgBarcodeScannerHelper

    // USUARIOS EN LINEA
    private val listaUsuariosOnline = mutableListOf<UsuarioPresencia>()
    private lateinit var usuariosOnlineAdapter: UsuariosOnlineAdapter

    // ESTADO FILTROS REGISTRO
    private var fechaFiltroSeleccionada: String = ""
    private var horaInicioFiltro: String? = null
    private var horaFinFiltro: String? = null

    // NOTIFICACIONES DE TIEMPO
    private val CHANNEL_ID = "justplay_admin_timer_channel"
    private val salasNotificadasFin = mutableSetOf<Int>()

    // REFERENCIA A FIREBASE
    private lateinit var database: DatabaseReference

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
                putExtra("android.intent.extras.CAMERA_FACING", 0)
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

        crearCanalNotificaciones()
        solicitarPermisoNotificaciones()

        fechaFiltroSeleccionada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        horaInicioFiltro = null
        horaFinFiltro = null

        // INICIALIZAR FIREBASE Y GESTORES SOLID
        database = FirebaseDatabase.getInstance().reference
        posManager = PosManagementManager(this, database)
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
        layoutUsuariosOnline = findViewById(R.id.layoutUsuariosOnline)
        tvTituloToolbar = findViewById(R.id.tvTituloToolbar)
        tvBadgeOffline = findViewById(R.id.tvBadgeOffline)
        tvBadgeUsuariosOnline = findViewById(R.id.tvBadgeUsuariosOnline)

        tvMinicineOcupadas = findViewById(R.id.tvMinicineOcupadas)
        tvAutitosOcupados = findViewById(R.id.tvAutitosOcupados)
        tvJustDanceOcupados = findViewById(R.id.tvJustDanceOcupados)

        // PRESENCIA AUTOMÁTICA DEL ADMINISTRADOR
        PresenciaManager.iniciarMonitoreoPresencia(this, "AdminJustPlay", "Administrador")

        tvBadgeUsuariosOnline.setOnClickListener {
            navegarAPantalla("👥 Usuarios en Línea")
        }

        // Monitorear conectividad con Firebase Realtime Database
        database.root.child(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                actualizarEstadoConexion(connected)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Monitorear conectividad de red
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

        fabOverlay = findViewById(R.id.fabOverlay)
        llFabVenta = findViewById(R.id.llFabVenta)
        llFabReservas = findViewById(R.id.llFabReservas)
        fabMain = findViewById(R.id.fabMain)

        fabMain.setOnClickListener {
            if (!estaConectadoInternet) {
                Toast.makeText(this, "⚠️ Modo sin conexión: Estás en modo de solo lectura.", Toast.LENGTH_LONG).show()
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
                handlerRelojes.postDelayed(this, 1000)
            }
        }
        handlerRelojes.post(runnableRelojes)

        val btnMenuHamburguesa = findViewById<TextView>(R.id.btnMenuHamburguesa)
        btnMenuHamburguesa.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.menu.add(0, 1, 0, "Just Play (Tablero)").setIcon(android.R.drawable.ic_menu_sort_by_size)
        navigationView.menu.add(0, 2, 1, "Registro y Auditoría").setIcon(android.R.drawable.ic_menu_recent_history)
        navigationView.menu.add(0, 3, 2, "Reservas").setIcon(android.R.drawable.ic_menu_today)
        navigationView.menu.add(0, 4, 3, "Clientes Vetados").setIcon(android.R.drawable.ic_menu_close_clear_cancel)
        navigationView.menu.add(0, 5, 4, "👥 Usuarios en Línea").setIcon(android.R.drawable.ic_menu_myplaces)
        navigationView.menu.add(0, 6, 5, "🏗️ Aumentar / Gestionar Salas").setIcon(android.R.drawable.ic_input_add)
        navigationView.menu.add(0, 7, 6, "🔄 Buscar Actualizaciones").setIcon(android.R.drawable.ic_popup_sync)
        navigationView.menu.add(0, 8, 7, "🖥️ Terminales POS y Catálogo").setIcon(android.R.drawable.ic_menu_manage)
        navigationView.menu.add(0, 9, 8, "🍿 Auditoría Minicine").setIcon(android.R.drawable.ic_menu_view)
        navigationView.menu.add(0, 10, 9, "🏎️ Auditoría Volantes").setIcon(android.R.drawable.ic_menu_view)
        navigationView.menu.add(0, 11, 10, "💃 Auditoría Just Dance").setIcon(android.R.drawable.ic_menu_view)
        navigationView.menu.add(0, 12, 11, "📦 Auditoría Productos / Catálogo").setIcon(android.R.drawable.ic_menu_agenda)
        navigationView.menu.add(0, 13, 12, "💸 Control de Egresos y Gastos").setIcon(android.R.drawable.ic_menu_save)
        navigationView.menu.add(0, 14, 13, "🛡️ Historial Global de Salas").setIcon(android.R.drawable.ic_menu_info_details)
        navigationView.menu.add(0, 15, 14, "💲 Precios Base de Salas").setIcon(android.R.drawable.ic_menu_edit)

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                6 -> mostrarDialogoAdministrarSalas()
                7 -> AppUpdateManager.verificarActualizacion(this, "AdminJustPlay", mostrarToastSiNoHay = true)
                8 -> posManager.mostrarDialogoGestionPOS()
                9 -> roomAuditManager.mostrarDialogoHistorialAuditoriaPorArea("CINE", "🍿 Auditoría Minicine")
                10 -> roomAuditManager.mostrarDialogoHistorialAuditoriaPorArea("AUTITOS", "🏎️ Auditoría Volantes")
                11 -> roomAuditManager.mostrarDialogoHistorialAuditoriaPorArea("JUST_DANCE", "💃 Auditoría Just Dance")
                12 -> productAuditManager.mostrarDialogoHistorialAuditoriaProductos()
                13 -> egresosManager.mostrarDialogoGestionEgresos()
                14 -> roomAuditManager.mostrarDialogoHistorialAuditoriaSalas()
                15 -> posManager.mostrarDialogoPreciosBase()
                else -> {
                    val title = item.title.toString()
                    when {
                        title.contains("Gestionar Salas") -> mostrarDialogoAdministrarSalas()
                        title.contains("Actualizaciones") -> AppUpdateManager.verificarActualizacion(this, "AdminJustPlay", mostrarToastSiNoHay = true)
                        title.contains("Terminales POS") -> posManager.mostrarDialogoGestionPOS()
                        title.contains("Auditoría Minicine") -> roomAuditManager.mostrarDialogoHistorialAuditoriaPorArea("CINE", "🍿 Auditoría Minicine")
                        title.contains("Auditoría Volantes") -> roomAuditManager.mostrarDialogoHistorialAuditoriaPorArea("AUTITOS", "🏎️ Auditoría Volantes")
                        title.contains("Auditoría Just Dance") -> roomAuditManager.mostrarDialogoHistorialAuditoriaPorArea("JUST_DANCE", "💃 Auditoría Just Dance")
                        title.contains("Auditoría Productos") -> productAuditManager.mostrarDialogoHistorialAuditoriaProductos()
                        title.contains("Egresos") -> egresosManager.mostrarDialogoGestionEgresos()
                        title.contains("Global de Salas") || title.contains("Trazabilidad") -> roomAuditManager.mostrarDialogoHistorialAuditoriaSalas()
                        title.contains("Precios Base") -> posManager.mostrarDialogoPreciosBase()
                        else -> navegarAPantalla(title)
                    }
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        // COMPROBADOR AUTOMÁTICO DE ACTUALIZACIONES AL INICIAR (In-app updater)
        try {
            AppUpdateManager.verificarActualizacion(this, "AdminJustPlay")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // CONFIGURAR RECYCLERVIEW DE REGISTRO & AUDITORIA
        val rvRegistro = findViewById<RecyclerView>(R.id.rvRegistro)
        rvRegistro.layoutManager = LinearLayoutManager(this)
        registroAdapter = RegistroAdminAdapter(
            historialFiltrado,
            onEliminarRegistro = { reg -> eliminarRegistro(reg) },
            onRestaurarRegistro = { reg -> restaurarRegistro(reg) }
        )
        rvRegistro.adapter = registroAdapter

        // PESTAÑAS DE FILTRO DE AUDITORIA
        val btnAuditoriaActivos = findViewById<Button>(R.id.btnFiltroAuditoriaActivos)
        val btnAuditoriaBorrados = findViewById<Button>(R.id.btnFiltroAuditoriaBorrados)
        val btnAuditoriaTodos = findViewById<Button>(R.id.btnFiltroAuditoriaTodos)

        fun actualizarBotonesAuditoria() {
            btnAuditoriaActivos.setBackgroundColor(if (modoAuditoria == "ACTIVOS") Color.parseColor("#DEFF9A") else Color.parseColor("#24283B"))
            btnAuditoriaActivos.setTextColor(if (modoAuditoria == "ACTIVOS") Color.BLACK else Color.WHITE)

            btnAuditoriaBorrados.setBackgroundColor(if (modoAuditoria == "BORRADOS") Color.parseColor("#FF5252") else Color.parseColor("#24283B"))
            btnAuditoriaBorrados.setTextColor(if (modoAuditoria == "BORRADOS") Color.WHITE else Color.parseColor("#FF5252"))

            btnAuditoriaTodos.setBackgroundColor(if (modoAuditoria == "TODOS") Color.parseColor("#DEFF9A") else Color.parseColor("#24283B"))
            btnAuditoriaTodos.setTextColor(if (modoAuditoria == "TODOS") Color.BLACK else Color.WHITE)
        }

        btnAuditoriaActivos.setOnClickListener {
            modoAuditoria = "ACTIVOS"
            actualizarBotonesAuditoria()
            aplicarFiltrosRegistro()
        }

        btnAuditoriaBorrados.setOnClickListener {
            modoAuditoria = "BORRADOS"
            actualizarBotonesAuditoria()
            aplicarFiltrosRegistro()
        }

        btnAuditoriaTodos.setOnClickListener {
            modoAuditoria = "TODOS"
            actualizarBotonesAuditoria()
            aplicarFiltrosRegistro()
        }

        configurarAdaptersReservas()

        // CONFIGURAR VETADOS
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

        // CONFIGURAR USUARIOS EN LINEA
        val rvUsuariosOnline = findViewById<RecyclerView>(R.id.rvUsuariosOnline)
        rvUsuariosOnline.layoutManager = LinearLayoutManager(this)
        usuariosOnlineAdapter = UsuariosOnlineAdapter(
            listaUsuariosOnline,
            onGestionarUsuario = { usuarioSeleccionado ->
                mostrarDialogoGestionarUsuario(usuarioSeleccionado)
            },
            onToggleBloqueo = { usuario ->
                val nuevoEstado = !usuario.bloqueado
                val motivo = if (nuevoEstado) "Desactivado por el Administrador" else ""
                val updates = mapOf(
                    "bloqueado" to nuevoEstado,
                    "motivoBloqueo" to motivo
                )
                database.child("presencia_usuarios").child(usuario.id).updateChildren(updates)
                    .addOnSuccessListener {
                        val msg = if (nuevoEstado) "🚫 Dispositivo '${usuario.nombre}' desactivado" else "✅ Dispositivo '${usuario.nombre}' activado"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al actualizar estado en Firebase", Toast.LENGTH_SHORT).show()
                    }
            }
        )
        rvUsuariosOnline.adapter = usuariosOnlineAdapter

        findViewById<Button>(R.id.btnCrearNuevoUsuario).setOnClickListener {
            if (!estaConectadoInternet) {
                Toast.makeText(this, "⚠️ Sin conexión: Se requiere internet para registrar usuarios.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            mostrarDialogoCrearNuevoUsuario()
        }

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

    private fun mostrarDialogoGestionarUsuario(usuario: UsuarioPresencia) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gestionar_usuario, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnCerrarGestion)
        val tvInfoHardware = dialogView.findViewById<TextView>(R.id.tvInfoDispositivoHardware)
        val etNombre = dialogView.findViewById<EditText>(R.id.etGestionNombre)
        val etPin = dialogView.findViewById<EditText>(R.id.etGestionPin)
        val rbEmpleado = dialogView.findViewById<RadioButton>(R.id.rbGestionRolEmpleado)
        val rbAdmin = dialogView.findViewById<RadioButton>(R.id.rbGestionRolAdmin)

        val rbTiempoIndefinido = dialogView.findViewById<RadioButton>(R.id.rbTiempoIndefinido)
        val rbTiempo1Hora = dialogView.findViewById<RadioButton>(R.id.rbTiempo1Hora)
        val rbTiempo8Horas = dialogView.findViewById<RadioButton>(R.id.rbTiempo8Horas)
        val rbTiempo1Dia = dialogView.findViewById<RadioButton>(R.id.rbTiempo1Dia)
        val tvFechaFinCalculada = dialogView.findViewById<TextView>(R.id.tvFechaFinCalculada)

        val cbBloquear = dialogView.findViewById<CheckBox>(R.id.cbBloquearDispositivo)
        val etMotivoBloqueo = dialogView.findViewById<EditText>(R.id.etMotivoBloqueoAdmin)
        val btnDesvincular = dialogView.findViewById<Button>(R.id.btnEliminarDispositivoLista)
        val btnGuardar = dialogView.findViewById<Button>(R.id.btnGuardarGestionUsuario)

        val dispNombre = if (usuario.dispositivo.isNotBlank()) usuario.dispositivo else "Android"
        tvInfoHardware.text = "📱 $dispNombre • ID: ${usuario.id} (${usuario.app})"
        etNombre.setText(usuario.nombre)
        etPin.setText(if (usuario.pin.isNotBlank()) usuario.pin else "0001")

        if (usuario.rol.equals("Administrador", ignoreCase = true) || usuario.rol.equals("Admin", ignoreCase = true)) {
            rbAdmin.isChecked = true
        } else {
            rbEmpleado.isChecked = true
        }

        when (usuario.tipoTiempoAcceso) {
            "1 Hora" -> rbTiempo1Hora.isChecked = true
            "8 Horas (Turno)" -> rbTiempo8Horas.isChecked = true
            "1 Día" -> rbTiempo1Dia.isChecked = true
            else -> rbTiempoIndefinido.isChecked = true
        }

        fun actualizarTextoFechaFin() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val ahora = System.currentTimeMillis()
            when {
                rbTiempo1Hora.isChecked -> {
                    val fin = ahora + 1 * 60 * 60 * 1000L
                    tvFechaFinCalculada.text = "Vigencia: Hasta ${sdf.format(Date(fin))} (1 hora)"
                }
                rbTiempo8Horas.isChecked -> {
                    val fin = ahora + 8 * 60 * 60 * 1000L
                    tvFechaFinCalculada.text = "Vigencia: Hasta ${sdf.format(Date(fin))} (Turno 8h)"
                }
                rbTiempo1Dia.isChecked -> {
                    val fin = ahora + 24 * 60 * 60 * 1000L
                    tvFechaFinCalculada.text = "Vigencia: Hasta ${sdf.format(Date(fin))} (24 horas)"
                }
                else -> {
                    tvFechaFinCalculada.text = "Vigencia: Permanente (Hasta que lo desactives)"
                }
            }
        }

        actualizarTextoFechaFin()
        rbTiempoIndefinido.setOnClickListener { actualizarTextoFechaFin() }
        rbTiempo1Hora.setOnClickListener { actualizarTextoFechaFin() }
        rbTiempo8Horas.setOnClickListener { actualizarTextoFechaFin() }
        rbTiempo1Dia.setOnClickListener { actualizarTextoFechaFin() }

        cbBloquear.isChecked = usuario.bloqueado
        etMotivoBloqueo.setText(if (usuario.motivoBloqueo.isNotBlank()) usuario.motivoBloqueo else "Acceso restringido por el Administrador")
        etMotivoBloqueo.visibility = if (usuario.bloqueado) View.VISIBLE else View.GONE

        cbBloquear.setOnCheckedChangeListener { _, isChecked ->
            etMotivoBloqueo.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnCerrar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val nuevoNombre = etNombre.text.toString().trim()
            val nuevoPin = etPin.text.toString().trim()
            val nuevoRol = if (rbAdmin.isChecked) "Administrador" else "Empleado"
            val estaBloqueado = cbBloquear.isChecked
            val motivo = etMotivoBloqueo.text.toString().trim()

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val ahora = System.currentTimeMillis()

            val (nuevoTiempoMillis, nuevoTipoTiempo, nuevaFechaFin) = when {
                rbTiempo1Hora.isChecked -> {
                    val fin = ahora + 1 * 60 * 60 * 1000L
                    Triple(fin, "1 Hora", sdf.format(Date(fin)))
                }
                rbTiempo8Horas.isChecked -> {
                    val fin = ahora + 8 * 60 * 60 * 1000L
                    Triple(fin, "8 Horas (Turno)", sdf.format(Date(fin)))
                }
                rbTiempo1Dia.isChecked -> {
                    val fin = ahora + 24 * 60 * 60 * 1000L
                    Triple(fin, "1 Día", sdf.format(Date(fin)))
                }
                else -> {
                    Triple(0L, "Indefinido", "Sin límite")
                }
            }

            val actualizaciones = mapOf(
                "nombre" to (if (nuevoNombre.isNotEmpty()) nuevoNombre else "Dispositivo ${usuario.id}"),
                "pin" to (if (nuevoPin.isNotEmpty()) nuevoPin else "0001"),
                "rol" to nuevoRol,
                "bloqueado" to estaBloqueado,
                "motivoBloqueo" to (if (motivo.isNotEmpty()) motivo else "Acceso restringido por el Administrador"),
                "tiempoLimiteMillis" to nuevoTiempoMillis,
                "tipoTiempoAcceso" to nuevoTipoTiempo,
                "fechaFinAccesoTexto" to nuevaFechaFin
            )

            database.child("presencia_usuarios").child(usuario.id).updateChildren(actualizaciones)
                .addOnSuccessListener {
                    Toast.makeText(this, "Permisos de dispositivo guardados correctamente", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al guardar en Firebase", Toast.LENGTH_SHORT).show()
                }
        }

        btnDesvincular.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Desvincular Dispositivo")
                .setMessage("¿Deseas quitar '${usuario.nombre}' de la lista de dispositivos?")
                .setPositiveButton("Sí, eliminar") { _, _ ->
                    database.child("presencia_usuarios").child(usuario.id).removeValue()
                    dialog.dismiss()
                    Toast.makeText(this, "Dispositivo desvinculado", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        dialog.show()
    }

    private fun mostrarDialogoCrearNuevoUsuario() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }

        val etNombre = EditText(this).apply {
            hint = "Nombre del Empleado o Usuario"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etPin = EditText(this).apply {
            hint = "PIN de Acceso (4 dígitos, ej. 1234)"
            setText("0001")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val tvRol = TextView(this).apply {
            text = "Rol asignado:"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 12f
            setPadding(0, 10, 0, 4)
        }

        val roles = arrayOf("Empleado", "Administrador")
        val spinnerRol = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, roles)
            setAdapter(adapter)
            setBackgroundColor(Color.parseColor("#24283B"))
        }

        container.addView(etNombre)
        container.addView(etPin)
        container.addView(tvRol)
        container.addView(spinnerRol)

        AlertDialog.Builder(this)
            .setTitle("➕ Registrar Nuevo Usuario")
            .setView(container)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = etNombre.text.toString().trim()
                val pin = etPin.text.toString().trim().ifEmpty { "0001" }
                val rol = spinnerRol.selectedItem?.toString() ?: "Empleado"

                if (nombre.isEmpty()) {
                    Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newId = UUID.randomUUID().toString().substring(0, 8)
                val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                val usuarioNuevo = mapOf(
                    "id" to newId,
                    "nombre" to nombre,
                    "pin" to pin,
                    "rol" to rol,
                    "app" to "Employ_JustPlay",
                    "dispositivo" to "Asignado manualmente",
                    "enLinea" to false,
                    "ultimaConexion" to 0L,
                    "ultimaConexionTexto" to "Nunca (Pre-registrado)",
                    "seccionActual" to "Ninguna",
                    "bloqueado" to false,
                    "motivoBloqueo" to "",
                    "tiempoLimiteMillis" to 0L,
                    "tipoTiempoAcceso" to "Indefinido",
                    "fechaFinAccesoTexto" to "Sin límite",
                    "fechaCreacion" to fecha
                )

                database.child("presencia_usuarios").child(newId).setValue(usuarioNuevo)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ Usuario '$nombre' registrado exitosamente (PIN: $pin)", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "❌ Error al crear usuario: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarmas de Tiempo Just Play Admin"
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

        Toast.makeText(this, "⏰ ¡TIEMPO FINALIZADO!\n$mensajeTexto", Toast.LENGTH_LONG).show()

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
        try {
            database.child("salas").keepSynced(true)
            database.child("registros").keepSynced(true)
            database.child("auditoria_registros").keepSynced(true)
            database.child("reservas").keepSynced(true)
            database.child("vetados").keepSynced(true)
            database.child("presencia_usuarios").keepSynced(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. ESCUCHAR SALAS
        database.child("salas").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempSalas = mutableListOf<Sala>()
                for (snap in snapshot.children) {
                    val s = snap.getValue(Sala::class.java)
                    if (s != null) {
                        s.idFirebase = snap.key ?: ""
                        if (s.id == 0) {
                            s.id = snap.key?.toIntOrNull() ?: 0
                        }
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

        // 2. ESCUCHAR AUDITORIA Y REGISTROS HISTORICOS (INMUTABLE)
        database.child("auditoria_registros").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historialCompleto.clear()
                for (snap in snapshot.children) {
                    val reg = snap.getValue(RegistroEntry::class.java)
                    if (reg != null) {
                        reg.idFirebase = snap.key ?: ""
                        historialCompleto.add(reg)
                    }
                }
                if (historialCompleto.isEmpty()) {
                    cargarRegistrosLegacy()
                } else {
                    aplicarFiltrosRegistro()
                }
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

        // 5. ESCUCHAR USUARIOS EN LINEA EN TIEMPO REAL
        database.child("presencia_usuarios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaUsuariosOnline.clear()
                var conectadosContador = 0

                for (snap in snapshot.children) {
                    val user = snap.getValue(UsuarioPresencia::class.java)
                    if (user != null) {
                        user.id = snap.key ?: ""
                        listaUsuariosOnline.add(user)
                        if (user.enLinea) conectadosContador++
                    }
                }

                listaUsuariosOnline.sortByDescending { it.ultimaConexion }
                listaUsuariosOnline.sortByDescending { it.enLinea }

                usuariosOnlineAdapter.notifyDataSetChanged()

                tvBadgeUsuariosOnline.text = if (conectadosContador > 0) "🟢 $conectadosContador en línea" else "⚪ 0 en línea"
                tvBadgeUsuariosOnline.setBackgroundColor(
                    if (conectadosContador > 0) Color.parseColor("#1B3E2B") else Color.parseColor("#2A2C39")
                )
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 6. ESCUCHAR CONFIGURACIÓN DE PRECIOS BASE
        database.child("config_precios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    BasePriceConfig.updateFromSnapshot(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun cargarRegistrosLegacy() {
        database.child("registros").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (snap in snapshot.children) {
                    val reg = snap.getValue(RegistroEntry::class.java)
                    if (reg != null) {
                        reg.idFirebase = snap.key ?: ""
                        historialCompleto.add(reg)
                        database.child("auditoria_registros").child(reg.idFirebase).setValue(reg)
                    }
                }
                aplicarFiltrosRegistro()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun eliminarRegistro(registro: RegistroEntry) {
        if (!estaConectadoInternet) {
            Toast.makeText(this, "⚠️ Sin conexión: No se pueden eliminar registros sin internet.", Toast.LENGTH_SHORT).show()
            return
        }

        val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)
        val fechaEliminacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        registro.eliminado = true
        registro.fechaEliminacion = fechaEliminacion
        registro.eliminadoPor = usuarioActual

        database.child("auditoria_registros").child(registro.idFirebase).setValue(registro)
        database.child("registros").child(registro.idFirebase).removeValue()

        Toast.makeText(this, "Registro enviado a la papelera de auditoría", Toast.LENGTH_SHORT).show()
    }

    private fun restaurarRegistro(registro: RegistroEntry) {
        if (!estaConectadoInternet) {
            Toast.makeText(this, "⚠️ Sin conexión: No se puede restaurar sin internet.", Toast.LENGTH_SHORT).show()
            return
        }

        registro.eliminado = false
        registro.fechaEliminacion = null
        registro.eliminadoPor = null

        database.child("auditoria_registros").child(registro.idFirebase).setValue(registro)
        database.child("registros").child(registro.idFirebase).setValue(registro)

        Toast.makeText(this, "✅ Registro restaurado exitosamente a la lista activa", Toast.LENGTH_SHORT).show()
    }

    private fun navegarAPantalla(titulo: String) {
        layoutTablero.visibility = View.GONE
        layoutRegistro.visibility = View.GONE
        layoutReservasGenerales.visibility = View.GONE
        layoutVetados.visibility = View.GONE
        layoutUsuariosOnline.visibility = View.GONE

        when (titulo) {
            "Just Play (Tablero)" -> {
                layoutTablero.visibility = View.VISIBLE
                tvTituloToolbar.text = "Admin Just Play"
                PresenciaManager.actualizarSeccion("Tablero")
            }
            "Registro", "Registro y Auditoría" -> {
                layoutRegistro.visibility = View.VISIBLE
                tvTituloToolbar.text = "Registro & Auditoría"
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
            "👥 Usuarios en Línea" -> {
                layoutUsuariosOnline.visibility = View.VISIBLE
                tvTituloToolbar.text = "Usuarios en Línea"
                PresenciaManager.actualizarSeccion("Usuarios en Línea")
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

        val rgMetodoPago = dialogView.findViewById<RadioGroup>(R.id.rgMetodoPago)
        val rbPagoEfectivo = dialogView.findViewById<RadioButton>(R.id.rbPagoEfectivo)
        val rbPagoQR = dialogView.findViewById<RadioButton>(R.id.rbPagoQR)
        val rbPagoMixto = dialogView.findViewById<RadioButton>(R.id.rbPagoMixto)
        val llSeccionQR = dialogView.findViewById<LinearLayout>(R.id.llSeccionQR)
        val llSeccionMixto = dialogView.findViewById<LinearLayout>(R.id.llSeccionMixto)
        val etMontoEfectivoMixto = dialogView.findViewById<EditText>(R.id.etMontoEfectivoMixto)
        val tvMontoQRMixto = dialogView.findViewById<TextView>(R.id.tvMontoQRMixto)

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
            rutaComprobanteVenta = null
            actualizarVistaPreviaQR(null)
        }

        fun redibujarPedidos() {
            llListaPedidos.removeAllViews()
            for (pedido in pedidosVenta) {
                val itemView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
                val tv = itemView.findViewById<TextView>(android.R.id.text1)
                tv.text = "${pedido.cantidad}x ${pedido.nombre} - ${pedido.subtotal()} Bs"
                tv.setTextColor(Color.WHITE)
                tv.textSize = 14f
                itemView.setOnClickListener {
                    pedidosVenta.remove(pedido)
                    redibujarPedidos()
                    refrescarTotalUI()
                }
                llListaPedidos.addView(itemView)
            }
        }

        btnAgregarPedido.setOnClickListener {
            val cant = etPedCantidad.text.toString().toIntOrNull() ?: 1
            val nom = etPedNombre.text.toString().trim()
            val pre = etPedPrecio.text.toString().toDoubleOrNull() ?: 0.0

            if (nom.isNotEmpty() && pre > 0) {
                val nuevoP = Pedido(database.child("registros").push().key ?: "", cant, nom, pre, true)
                pedidosVenta.add(nuevoP)
                etPedCantidad.text.clear()
                etPedNombre.text.clear()
                etPedPrecio.text.clear()
                redibujarPedidos()
                refrescarTotalUI()
            } else {
                Toast.makeText(this, "Ingresa nombre y precio válido", Toast.LENGTH_SHORT).show()
            }
        }

        btnConfirmar.setOnClickListener {
            if (pedidosVenta.isEmpty()) {
                Toast.makeText(this, "Añade al menos un producto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val metodoSeleccionado = when (rgMetodoPago.checkedRadioButtonId) {
                R.id.rbPagoQR -> "QR"
                R.id.rbPagoMixto -> {
                    val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val total = pedidosVenta.sumOf { it.subtotal() }
                    val qr = maxOf(0.0, total - efec)
                    "Mixto (💵 $efec Bs | 📱 $qr Bs)"
                }
                else -> "Efectivo"
            }

            val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)

            for (p in pedidosVenta) {
                val pKey = database.child("registros").push().key ?: continue
                val pEntrada = RegistroEntry(
                    idFirebase = pKey,
                    tipo = "Consumo",
                    nombreElemento = "Consumo Mostrador",
                    cliente = "Venta Mostrador",
                    fechaHora = fechaHora,
                    pedidos = "${p.cantidad}x ${p.nombre} (${p.subtotal()} Bs)",
                    totalCobrado = p.subtotal(),
                    metodoPago = metodoSeleccionado,
                    comprobanteUri = rutaComprobanteVenta,
                    creadoPor = usuarioActual,
                    eliminado = false
                )
                database.child("registros").child(pKey).setValue(pEntrada)
                database.child("auditoria_registros").child(pKey).setValue(pEntrada)
            }

            Toast.makeText(this, "Venta registrada con éxito", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoVetarCliente(vetadoExistente: ClienteVetado?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_nueva_reserva, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etNombre = view.findViewById<EditText>(R.id.etResNombre)
        val etCelular = view.findViewById<EditText>(R.id.etResCelular)
        val etMotivo = view.findViewById<EditText>(R.id.etResDuracion)
        val btnGuardar = view.findViewById<Button>(R.id.btnResGuardar)
        val btnCancelar = view.findViewById<Button>(R.id.btnResCancelar)

        view.findViewById<View>(R.id.btnResFecha)?.visibility = View.GONE
        view.findViewById<View>(R.id.btnResHora)?.visibility = View.GONE
        view.findViewById<View>(R.id.rgResTipo)?.visibility = View.GONE
        view.findViewById<View>(R.id.etResAdelanto)?.visibility = View.GONE
        view.findViewById<View>(R.id.rgResMetodoPago)?.visibility = View.GONE

        etMotivo.hint = "Motivo del veto o notas de advertencia"
        etMotivo.inputType = InputType.TYPE_CLASS_TEXT

        if (vetadoExistente != null) {
            etNombre.setText(vetadoExistente.nombre ?: "")
            etCelular.setText(vetadoExistente.celular)
            etCelular.isEnabled = false
            etMotivo.setText(vetadoExistente.motivo)
            btnGuardar.text = "Actualizar Vetado"
        } else {
            btnGuardar.text = "Vetar Cliente"
        }

        btnGuardar.setOnClickListener {
            val cel = etCelular.text.toString().trim()
            val nom = etNombre.text.toString().trim()
            val mot = etMotivo.text.toString().trim()

            if (cel.isEmpty()) {
                Toast.makeText(this, "El número de celular es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoVetado = ClienteVetado(cel, if (nom.isNotEmpty()) nom else null, mot)
            database.child("vetados").child(cel).setValue(nuevoVetado)
            Toast.makeText(this, "Lista de vetados actualizada", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun configurarAdaptersReservas() {
        adapterResMinicine = ReservasAdapter(reservasMinicineList,
            onEditarReserva = { mostrarDialogoEditarReserva(it) },
            onAsignarSala = { mostrarDialogoSeleccionarSalaLibre(it) }
        )
        findViewById<RecyclerView>(R.id.rvReservasMinicine).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterResMinicine
        }

        adapterResAutitos = ReservasAdapter(reservasAutitosList,
            onEditarReserva = { mostrarDialogoEditarReserva(it) },
            onAsignarSala = { mostrarDialogoSeleccionarSalaLibre(it) }
        )
        findViewById<RecyclerView>(R.id.rvReservasAutitos).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterResAutitos
        }

        adapterResDance = ReservasAdapter(reservasDanceList,
            onEditarReserva = { mostrarDialogoEditarReserva(it) },
            onAsignarSala = { mostrarDialogoSeleccionarSalaLibre(it) }
        )
        findViewById<RecyclerView>(R.id.rvReservasJustDance).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterResDance
        }
    }

    private fun actualizarEstadoConexion(conectado: Boolean) {
        estaConectadoInternet = conectado
        tvBadgeOffline.visibility = if (conectado) View.GONE else View.VISIBLE
        if (layoutTablero.visibility == View.VISIBLE && conectado) {
            fabMain.visibility = View.VISIBLE
        } else {
            fabMain.visibility = View.GONE
        }
    }

    private fun configurarRecyclerView(rv: RecyclerView, lista: List<Sala>) {
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = SalasAdapter(
            listaSalas = lista,
            onClickSala = { salaSeleccionada ->
                if (!estaConectadoInternet) {
                    mostrarDialogoDetallesSalaSoloLectura(salaSeleccionada)
                    return@SalasAdapter
                }

                if (salaSeleccionada.estado == EstadoSala.DISPONIBLE) {
                    mostrarDialogoFuncion(salaSeleccionada, esNueva = true)
                } else {
                    mostrarDialogoFuncion(salaSeleccionada, esNueva = false)
                }
            },
            onLongClickSala = { salaSeleccionada ->
                mostrarDialogoConfirmarEliminarSalaDirecto(salaSeleccionada)
            }
        )
    }

    private fun eliminarSalaDeFirebase(sala: Sala) {
        val clave = if (sala.idFirebase.isNotEmpty()) sala.idFirebase else sala.id.toString()
        database.child("salas").child(clave).removeValue()
        if (sala.id > 0) {
            database.child("salas").child(sala.id.toString()).removeValue()
        }
        if (sala.nombre.isNotEmpty()) {
            database.child("salas").child(sala.nombre).removeValue()
        }

        // Registrar en Auditoría de Salas con Trazabilidad de Dispositivo
        val catSala = if (sala.id < 100) "Minicine" else if (sala.id in 101..199) "Autitos" else "Just Dance"
        roomAuditManager.registrarAccionSala(
            tipoAccion = "ELIMINACION_SALA",
            salaId = sala.id.toString(),
            salaNombre = sala.nombre,
            categoria = catSala,
            usuarioNombre = "Admin",
            usuarioRol = "Administrador",
            detalles = "Sala eliminada permanentemente"
        )
        database.child("salas").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (snap in snapshot.children) {
                    val s = snap.getValue(Sala::class.java)
                    val k = snap.key ?: ""
                    if ((sala.id > 0 && s?.id == sala.id) || 
                        (sala.nombre.isNotEmpty() && s?.nombre.equals(sala.nombre, ignoreCase = true)) || 
                        k == sala.id.toString() || 
                        k == sala.nombre ||
                        k == sala.idFirebase) {
                        snap.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        Toast.makeText(this, "'${sala.nombre}' eliminada permanentemente", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarDialogoConfirmarEliminarSalaDirecto(sala: Sala) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Eliminar ${sala.nombre}")
            .setMessage("¿Deseas eliminar permanentemente '${sala.nombre}' (ID: ${sala.id}) del sistema y de Firebase?")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                eliminarSalaDeFirebase(sala)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoAdministrarSalas() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_administrar_salas, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnCerrarGestionSalas)
        val etNumeroCine = dialogView.findViewById<EditText>(R.id.etNumeroNuevaSalaCine)
        val btnAgregarCine = dialogView.findViewById<Button>(R.id.btnAgregarSalaCine)

        val etNumeroAutito = dialogView.findViewById<EditText>(R.id.etNumeroNuevoAutito)
        val btnAgregarAutito = dialogView.findViewById<Button>(R.id.btnAgregarAutito)

        val etNumeroDance = dialogView.findViewById<EditText>(R.id.etNumeroNuevoDance)
        val btnAgregarDance = dialogView.findViewById<Button>(R.id.btnAgregarDance)

        val btnEliminarTodosAutitos = dialogView.findViewById<Button>(R.id.btnEliminarTodosAutitos)
        val btnEliminarTodasSalas = dialogView.findViewById<Button>(R.id.btnEliminarTodasSalas)

        val llContenedorSalas = dialogView.findViewById<LinearLayout>(R.id.llContenedorSalasEliminar)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        btnEliminarTodosAutitos?.setOnClickListener {
            if (salasAutitos.isEmpty()) {
                Toast.makeText(this, "No hay autitos en el sistema", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("🗑️ Eliminar TODOS los Autitos")
                .setMessage("¿Estás seguro de que deseas eliminar permanentemente TODOS los ${salasAutitos.size} autitos de Firebase?")
                .setPositiveButton("Sí, eliminar todos") { _, _ ->
                    database.child("salas").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            var eliminados = 0
                            for (snap in snapshot.children) {
                                val s = snap.getValue(Sala::class.java)
                                val idNum = snap.key?.toIntOrNull() ?: s?.id ?: 0
                                val nombre = s?.nombre ?: ""
                                if (idNum in 101..199 || nombre.contains("Car", ignoreCase = true) || nombre.contains("Auto", ignoreCase = true)) {
                                    snap.ref.removeValue()
                                    eliminados++
                                }
                            }
                            Toast.makeText(this@MainActivity, "Se eliminaron $eliminados autitos exitosamente", Toast.LENGTH_SHORT).show()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        btnEliminarTodasSalas?.setOnClickListener {
            if (salasMinicine.isEmpty()) {
                Toast.makeText(this, "No hay salas de minicine en el sistema", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("🗑️ Eliminar TODO Minicine")
                .setMessage("¿Estás seguro de que deseas eliminar permanentemente TODAS las ${salasMinicine.size} salas de minicine de Firebase?")
                .setPositiveButton("Sí, eliminar todas") { _, _ ->
                    database.child("salas").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            var eliminados = 0
                            for (snap in snapshot.children) {
                                val s = snap.getValue(Sala::class.java)
                                val idNum = snap.key?.toIntOrNull() ?: s?.id ?: 0
                                val nombre = s?.nombre ?: ""
                                if (idNum in 1..99 || nombre.contains("Sala", ignoreCase = true)) {
                                    snap.ref.removeValue()
                                    eliminados++
                                }
                            }
                            Toast.makeText(this@MainActivity, "Se eliminaron $eliminados salas de minicine", Toast.LENGTH_SHORT).show()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        val salasListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llContenedorSalas.removeAllViews()
                val listaActual = mutableListOf<Sala>()
                for (snap in snapshot.children) {
                    val s = snap.getValue(Sala::class.java)
                    if (s != null) {
                        s.idFirebase = snap.key ?: ""
                        if (s.id == 0) s.id = snap.key?.toIntOrNull() ?: 0
                        listaActual.add(s)
                    }
                }
                listaActual.sortBy { it.id }

                if (listaActual.isEmpty()) {
                    val tvVacio = TextView(this@MainActivity).apply {
                        text = "No hay salas registradas"
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 13f
                        setPadding(0, 10, 0, 10)
                    }
                    llContenedorSalas.addView(tvVacio)
                    return
                }

                for (sala in listaActual) {
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(12, 10, 12, 10)
                        setBackgroundColor(Color.parseColor("#24283B"))
                        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        params.setMargins(0, 0, 0, 6)
                        layoutParams = params
                    }

                    val icono = when {
                        sala.id >= 200 || sala.nombre.contains("Dance", ignoreCase = true) -> "💃"
                        sala.id in 101..199 || sala.nombre.contains("Car", ignoreCase = true) || sala.nombre.contains("Auto", ignoreCase = true) -> "🏎️"
                        else -> "🎬"
                    }

                    val tvNombre = TextView(this@MainActivity).apply {
                        text = "$icono ${sala.nombre} (ID: ${sala.id})"
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val btnEliminar = Button(this@MainActivity).apply {
                        text = "🗑️ Quitar"
                        setTextColor(Color.parseColor("#FF5252"))
                        setBackgroundColor(Color.parseColor("#1A1C29"))
                        textSize = 11f
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Eliminar ${sala.nombre}")
                                .setMessage("¿Estás seguro de que deseas eliminar permanentemente '${sala.nombre}'?")
                                .setPositiveButton("Sí, eliminar") { _, _ ->
                                    val clave = if (sala.idFirebase.isNotEmpty()) sala.idFirebase else sala.id.toString()
                                    database.child("salas").child(clave).removeValue()
                                    database.child("salas").child(sala.id.toString()).removeValue()
                                    if (sala.nombre.isNotEmpty()) {
                                        database.child("salas").child(sala.nombre).removeValue()
                                    }
                                    Toast.makeText(this@MainActivity, "'${sala.nombre}' eliminada", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    }

                    row.addView(tvNombre)
                    row.addView(btnEliminar)
                    llContenedorSalas.addView(row)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("salas").addValueEventListener(salasListener)
        dialog.setOnDismissListener { database.child("salas").removeEventListener(salasListener) }

        btnAgregarCine.setOnClickListener {
            val numStr = etNumeroCine.text.toString().trim()
            val num = numStr.toIntOrNull()
            if (num == null || num <= 0 || num >= 100) {
                Toast.makeText(this, "Ingresa un número de sala válido (1 - 99)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (salasMinicine.any { it.id == num }) {
                Toast.makeText(this, "La Sala $num ya existe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevaSala = Sala(id = num, nombre = "Sala $num", estado = EstadoSala.DISPONIBLE)
            database.child("salas").child(num.toString()).setValue(nuevaSala)
                .addOnSuccessListener {
                    Toast.makeText(this, "Sala $num agregada exitosamente", Toast.LENGTH_SHORT).show()
                    etNumeroCine.text.clear()
                }
        }

        btnAgregarAutito.setOnClickListener {
            val numStr = etNumeroAutito.text.toString().trim()
            val num = numStr.toIntOrNull()
            if (num == null || num <= 0 || num > 999) {
                Toast.makeText(this, "Ingresa un número de volante válido (Ej. 1, 2, 3...)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val idAutito = 100 + num
            if (salasAutitos.any { it.id == idAutito }) {
                Toast.makeText(this, "El Car $num ya existe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoAutito = Sala(id = idAutito, nombre = "Car $num", estado = EstadoSala.DISPONIBLE)
            database.child("salas").child(idAutito.toString()).setValue(nuevoAutito)
                .addOnSuccessListener {
                    Toast.makeText(this, "Car $num agregado exitosamente", Toast.LENGTH_SHORT).show()
                    etNumeroAutito.text.clear()
                }
        }

        btnAgregarDance.setOnClickListener {
            val numStr = etNumeroDance.text.toString().trim()
            val num = numStr.toIntOrNull()
            if (num == null || num <= 0) {
                Toast.makeText(this, "Ingresa un número de puesto válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val idDance = 200 + num
            if (salasJustDance.any { it.id == idDance }) {
                Toast.makeText(this, "Just Dance $num ya existe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoDance = Sala(id = idDance, nombre = "Just Dance $num", estado = EstadoSala.DISPONIBLE)
            database.child("salas").child(idDance.toString()).setValue(nuevoDance)
                .addOnSuccessListener {
                    Toast.makeText(this, "Just Dance $num agregado exitosamente", Toast.LENGTH_SHORT).show()
                    etNumeroDance.text.clear()
                }
        }

        dialog.show()
    }

    private fun mostrarDialogoDetallesSalaSoloLectura(sala: Sala) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detalle_sala_solo_lectura, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvTituloDetalleSoloLectura)
        val tvBadgeEstado = dialogView.findViewById<TextView>(R.id.tvBadgeEstadoSoloLectura)
        val btnCerrarX = dialogView.findViewById<TextView>(R.id.btnCerrarDetalleSoloLecturaX)
        val btnCerrar = dialogView.findViewById<Button>(R.id.btnCerrarDetalleSoloLectura)

        val llInfoOcupada = dialogView.findViewById<LinearLayout>(R.id.llInfoOcupadaSoloLectura)
        val llInfoDisponible = dialogView.findViewById<LinearLayout>(R.id.llInfoDisponibleSoloLectura)

        val tvCliente = dialogView.findViewById<TextView>(R.id.tvClienteDetalleSoloLectura)
        val tvTiempoRestante = dialogView.findViewById<TextView>(R.id.tvTiempoRestanteDetalleSoloLectura)
        val tvHoraFin = dialogView.findViewById<TextView>(R.id.tvHoraFinDetalleSoloLectura)
        val tvPelicula = dialogView.findViewById<TextView>(R.id.tvPeliculaDetalleSoloLectura)
        val tvPedidos = dialogView.findViewById<TextView>(R.id.tvPedidosDetalleSoloLectura)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotalDetalleSoloLectura)
        val tvMetodoPago = dialogView.findViewById<TextView>(R.id.tvMetodoPagoDetalleSoloLectura)
        val btnVerComprobante = dialogView.findViewById<Button>(R.id.btnVerComprobanteDetalleSoloLectura)

        val tipoNombre = when {
            sala.id < 100 -> "🎬 Minicine - ${sala.nombre}"
            sala.id in 101..199 -> "🏎️ Simulador - ${sala.nombre}"
            else -> "💃 ${sala.nombre}"
        }
        tvTitulo.text = tipoNombre

        when (sala.estado) {
            EstadoSala.DISPONIBLE -> {
                tvBadgeEstado.text = "🟢 DISPONIBLE"
                tvBadgeEstado.setTextColor(Color.parseColor("#00E676"))
                llInfoOcupada.visibility = View.GONE
                llInfoDisponible.visibility = View.VISIBLE
            }
            EstadoSala.RESERVADA -> {
                tvBadgeEstado.text = "🟡 RESERVADA (${sala.horaReserva ?: "--:--"})"
                tvBadgeEstado.setTextColor(Color.parseColor("#FFC107"))
                llInfoOcupada.visibility = View.VISIBLE
                llInfoDisponible.visibility = View.GONE

                tvCliente.text = sala.cliente ?: "Cliente no especificado"
                tvTiempoRestante.text = "Programada para: ${sala.horaReserva ?: "--:--"}"
                tvHoraFin.text = "--:--"
                tvPelicula.text = sala.pelicula ?: "Por definir"
                tvPedidos.text = "Sin consumos previos"
                tvTotal.text = "Total: ${sala.calcularTotalGeneral()} Bs"
                tvMetodoPago.text = sala.metodoPago
            }
            EstadoSala.OCUPADA -> {
                tvBadgeEstado.text = "🔴 OCUPADA"
                tvBadgeEstado.setTextColor(Color.parseColor("#FF5252"))
                llInfoOcupada.visibility = View.VISIBLE
                llInfoDisponible.visibility = View.GONE

                val celTexto = if (!sala.celular.isNullOrEmpty()) " (${sala.celular})" else ""
                tvCliente.text = "${sala.cliente ?: "Cliente"}$celTexto"

                if (sala.endTimeMillis != null) {
                    val faltante = sala.endTimeMillis!! - System.currentTimeMillis()
                    if (faltante > 0) {
                        val h = (faltante / (1000 * 60 * 60)) % 24
                        val m = (faltante / (1000 * 60)) % 60
                        val s = (faltante / 1000) % 60
                        tvTiempoRestante.text = String.format("%02d:%02d:%02d", h, m, s)
                        tvTiempoRestante.setTextColor(Color.parseColor("#DEFF9A"))
                    } else {
                        tvTiempoRestante.text = "⏰ ¡TIEMPO CONCLUIDO!"
                        tvTiempoRestante.setTextColor(Color.parseColor("#FF5252"))
                    }
                } else {
                    tvTiempoRestante.text = "En curso"
                }

                tvHoraFin.text = sala.horaFinProgramada ?: "--:--"
                tvPelicula.text = sala.pelicula ?: "No especificada"

                val resumenPedidos = if (sala.pedidos.isNotEmpty()) {
                    sala.pedidos.joinToString("\n") { "• ${it.cantidad}x ${it.nombre} (${it.subtotal()} Bs)" }
                } else {
                    "Ninguno"
                }
                tvPedidos.text = resumenPedidos

                tvTotal.text = "Total: ${sala.calcularTotalGeneral()} Bs"
                tvMetodoPago.text = sala.metodoPago

                if (!sala.comprobanteUri.isNullOrEmpty()) {
                    btnVerComprobante.visibility = View.VISIBLE
                    btnVerComprobante.setOnClickListener {
                        ImageUtils.mostrarDialogoVerComprobante(this, sala.comprobanteUri!!, "Comprobante: ${sala.nombre}")
                    }
                } else {
                    btnVerComprobante.visibility = View.GONE
                }
            }
        }

        btnCerrarX.setOnClickListener { dialog.dismiss() }
        btnCerrar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun actualizarContadorOcupacion() {
        val oCine = salasMinicine.count { it.estado == EstadoSala.OCUPADA }
        tvMinicineOcupadas.text = "$oCine / ${salasMinicine.size}"

        val oAutos = salasAutitos.count { it.estado == EstadoSala.OCUPADA }
        tvAutitosOcupados.text = "$oAutos / ${salasAutitos.size}"

        val oDance = salasJustDance.count { it.estado == EstadoSala.OCUPADA }
        tvJustDanceOcupados.text = "$oDance / ${salasJustDance.size}"
    }

    private fun actualizarRelojesReservas() {
        // En el modo Administrador, las reservas se gestionan exclusivamente desde la sección dedicada
    }

    private fun convertirTiempoAMilisegundos(tiempoStr: String): Long? {
        val partes = tiempoStr.trim().split(":")
        return if (partes.size == 2) {
            val horas = partes[0].toLongOrNull() ?: return null
            val minutos = partes[1].toLongOrNull() ?: return null
            (horas * 60 + minutos) * 60 * 1000L
        } else {
            val minutos = tiempoStr.trim().toLongOrNull() ?: return null
            minutos * 60 * 1000L
        }
    }

    private fun aplicarFiltrosRegistro() {
        val cbCine = findViewById<CheckBox>(R.id.cbFiltroCine)?.isChecked ?: true
        val cbAutitos = findViewById<CheckBox>(R.id.cbFiltroAutitos)?.isChecked ?: true
        val cbDance = findViewById<CheckBox>(R.id.cbFiltroDance)?.isChecked ?: true
        val cbMostrador = findViewById<CheckBox>(R.id.cbFiltroMostrador)?.isChecked ?: true
        val cbEfectivo = findViewById<CheckBox>(R.id.cbFiltroEfectivo)?.isChecked ?: true
        val cbQR = findViewById<CheckBox>(R.id.cbFiltroQR)?.isChecked ?: true
        val cbEgresos = findViewById<CheckBox>(R.id.cbFiltroEgresos)?.isChecked ?: false

        historialFiltrado.clear()

        for (reg in historialCompleto) {
            when (modoAuditoria) {
                "ACTIVOS" -> if (reg.eliminado) continue
                "BORRADOS" -> if (!reg.eliminado) continue
                "TODOS" -> { }
            }

            if (fechaFiltroSeleccionada.isNotEmpty()) {
                if (!reg.fechaHora.startsWith(fechaFiltroSeleccionada)) continue
            }

            if (horaInicioFiltro != null || horaFinFiltro != null) {
                val horaRegistro = if (reg.fechaHora.length >= 16) reg.fechaHora.substring(11, 16) else ""
                if (horaRegistro.isNotEmpty()) {
                    if (horaInicioFiltro != null && horaRegistro < horaInicioFiltro!!) continue
                    if (horaFinFiltro != null && horaRegistro > horaFinFiltro!!) continue
                }
            }

            val tipo = reg.tipo.lowercase()
            val nombreEl = reg.nombreElemento.lowercase()
            val esEgreso = tipo.contains("egreso") || nombreEl.contains("egreso")
            val pasaTipo = when {
                esEgreso -> cbEgresos
                tipo.contains("consumo") || tipo.contains("mostrador") || tipo.contains("venta directa") || tipo.contains("producto") || tipo.contains("snack") || tipo.contains("bebida") || tipo.contains("golosina") -> cbMostrador
                tipo.contains("cine") || nombreEl.contains("cine") || (nombreEl.contains("sala") && !tipo.contains("consumo") && !nombreEl.contains("consumo")) -> cbCine
                tipo.contains("auto") || nombreEl.contains("car") || nombreEl.contains("auto") || nombreEl.contains("volante") -> cbAutitos
                tipo.contains("dance") || nombreEl.contains("dance") -> cbDance
                else -> cbMostrador
            }
            if (!pasaTipo) continue

            val metodo = reg.metodoPago.lowercase()
            val pasaPago = when {
                metodo.startsWith("mixto") -> (cbQR && reg.getQRAmount() > 0) || (cbEfectivo && reg.getEfectivoAmount() > 0)
                metodo.contains("qr") -> cbQR
                else -> cbEfectivo
            }
            if (!pasaPago) continue

            historialFiltrado.add(reg)
        }

        historialFiltrado.sortByDescending { it.fechaHora }
        registroAdapter.notifyDataSetChanged()

        val activos = historialFiltrado.filter { !it.eliminado }
        val borrados = historialFiltrado.filter { it.eliminado }

        val egresosActivos = activos.filter { it.tipo.contains("egreso", true) || it.nombreElemento.contains("egreso", true) }
        val ingresosActivos = activos.filter { !(it.tipo.contains("egreso", true) || it.nombreElemento.contains("egreso", true)) }

        val totalIngresos = ingresosActivos.sumOf { 
            (if (cbEfectivo) it.getEfectivoAmount() else 0.0) + (if (cbQR) it.getQRAmount() else 0.0)
        }
        val totalEgresos = if (cbEgresos) egresosActivos.sumOf { it.totalCobrado } else 0.0
        val totalNeto = maxOf(0.0, totalIngresos - totalEgresos)

        val totalBorrados = borrados.sumOf { it.totalCobrado }

        val egresosEfectivo = if (cbEgresos) egresosActivos.filter { !it.metodoPago.contains("QR", true) }.sumOf { it.totalCobrado } else 0.0
        val egresosQR = if (cbEgresos) egresosActivos.filter { it.metodoPago.contains("QR", true) }.sumOf { it.totalCobrado } else 0.0

        val totalEfectivo = maxOf(0.0, ingresosActivos.sumOf { it.getEfectivoAmount() } - egresosEfectivo)
        val totalQR = maxOf(0.0, ingresosActivos.sumOf { it.getQRAmount() } - egresosQR)

        val tvTituloTotal = findViewById<TextView>(R.id.tvTituloTotal)
        val tvTotalRecaudado = findViewById<TextView>(R.id.tvTotalRecaudado)
        val tvDesgloseTotal = findViewById<TextView>(R.id.tvDesgloseTotal)

        tvTituloTotal?.text = when (modoAuditoria) {
            "BORRADOS" -> "🗑️ TOTAL BORRADO / ANULADO:"
            "TODOS" -> "📋 TOTAL REGISTROS (ACTIVOS):"
            else -> "💰 TOTAL DEL DÍA (HOY):"
        }

        tvTotalRecaudado?.text = if (modoAuditoria == "BORRADOS") "$totalBorrados Bs" else "$totalNeto Bs"
        tvTotalRecaudado?.setTextColor(
            if (modoAuditoria == "BORRADOS") Color.parseColor("#FF5252") else Color.parseColor("#00E676")
        )

        val egresosTexto = if (totalEgresos > 0) " | 💸 Egresos: -$totalEgresos Bs" else ""
        val borradosTexto = if (totalBorrados > 0) " | 🗑️ Borrados: $totalBorrados Bs" else ""
        tvDesgloseTotal?.text = "💵 Efectivo: $totalEfectivo Bs | 📱 QR: $totalQR Bs$egresosTexto$borradosTexto - (${historialFiltrado.size} reg)"

        findViewById<Button>(R.id.btnFiltroFecha)?.text = if (fechaFiltroSeleccionada.isNotEmpty()) "📅 $fechaFiltroSeleccionada" else "📅 Todas"
        findViewById<Button>(R.id.btnFiltroHoraInicio)?.text = if (horaInicioFiltro != null) "⏰ Desde: $horaInicioFiltro" else "⏰ Desde: Inicio"
        findViewById<Button>(R.id.btnFiltroHoraFin)?.text = if (horaFinFiltro != null) "⏰ Hasta: $horaFinFiltro" else "⏰ Hasta: Fin"
    }

    private fun mostrarDialogoSeleccionarSalaLibre(reserva: Reserva) {
        val salasLibres = when (reserva.tipoSala) {
            "Minicine" -> salasMinicine.filter { it.estado == EstadoSala.DISPONIBLE }
            "Autitos" -> salasAutitos.filter { it.estado == EstadoSala.DISPONIBLE }
            else -> salasJustDance.filter { it.estado == EstadoSala.DISPONIBLE }
        }

        if (salasLibres.isEmpty()) {
            Toast.makeText(this, "No hay salas libres disponibles para ${reserva.tipoSala}", Toast.LENGTH_LONG).show()
            return
        }

        val nombresSalas = salasLibres.map { it.nombre }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Asignar ${reserva.nombre} a una Sala Libre")
            .setItems(nombresSalas) { _, which ->
                val salaElegida = salasLibres[which]
                mostrarDialogoFuncion(salaElegida, esNueva = true, reservaPrevia = reserva)
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

        val rgMetodoPago = dialogView.findViewById<RadioGroup>(R.id.rgMetodoPago)
        val rbPagoEfectivo = dialogView.findViewById<RadioButton>(R.id.rbPagoEfectivo)
        val rbPagoQR = dialogView.findViewById<RadioButton>(R.id.rbPagoQR)
        val rbPagoMixto = dialogView.findViewById<RadioButton>(R.id.rbPagoMixto)
        val llSeccionQR = dialogView.findViewById<LinearLayout>(R.id.llSeccionQR)
        val llSeccionMixto = dialogView.findViewById<LinearLayout>(R.id.llSeccionMixto)
        val etMontoEfectivoMixto = dialogView.findViewById<EditText>(R.id.etMontoEfectivoMixto)
        val tvMontoQRMixto = dialogView.findViewById<TextView>(R.id.tvMontoQRMixto)

        val btnTomarFotoQR = dialogView.findViewById<Button>(R.id.btnTomarFotoQR)
        val btnGaleriaQR = dialogView.findViewById<Button>(R.id.btnGaleriaQR)
        val rlPreviewQR = dialogView.findViewById<View>(R.id.rlPreviewQR)
        val ivPreviewQR = dialogView.findViewById<ImageView>(R.id.ivPreviewQR)
        val tvVerFotoQR = dialogView.findViewById<TextView>(R.id.tvVerFotoQR)
        val btnEliminarFotoQR = dialogView.findViewById<TextView>(R.id.btnEliminarFotoQR)

        var rutaComprobanteActual: String? = sala.comprobanteUri

        if (reservaPrevia != null) {
            etCliente.setText(reservaPrevia.nombre)
            etCelular.setText(reservaPrevia.celular)
            etTiempo.setText(reservaPrevia.duracion)
            if (reservaPrevia.adelanto.isNotEmpty()) {
                etPrecioSala.setText(reservaPrevia.adelanto)
            }
            if (reservaPrevia.comprobanteUri != null) {
                rutaComprobanteActual = reservaPrevia.comprobanteUri
            }
        }

        fun recalcularMixto() {
            val pSala = etPrecioSala.text.toString().toDoubleOrNull() ?: 0.0
            val pPedidos = sala.pedidos.sumOf { it.subtotal() }
            val total = pSala + pPedidos
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
            val pSala = etPrecioSala.text.toString().toDoubleOrNull() ?: 0.0
            val pPedidos = sala.pedidos.sumOf { it.subtotal() }
            val total = pSala + pPedidos
            tvTotalCuenta.text = "TOTAL: $total Bs"
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
            rutaComprobanteActual = null
            actualizarVistaPreviaQR(null)
        }

        fun redibujarPedidos() {
            llListaPedidos.removeAllViews()
            for (pedido in sala.pedidos) {
                val itemView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
                val tv = itemView.findViewById<TextView>(android.R.id.text1)
                tv.text = "${pedido.cantidad}x ${pedido.nombre} - ${pedido.subtotal()} Bs"
                tv.setTextColor(Color.WHITE)
                tv.textSize = 14f
                itemView.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Eliminar Consumo")
                        .setMessage("¿Deseas quitar '${pedido.nombre}'?")
                        .setPositiveButton("Sí") { _, _ ->
                            sala.pedidos.remove(pedido)
                            database.child("salas").child(sala.id.toString()).setValue(sala)
                            if (pedido.idFirebase.isNotEmpty()) {
                                database.child("registros").child(pedido.idFirebase).removeValue()
                            }
                            redibujarPedidos()
                            refrescarTotalUI()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
                llListaPedidos.addView(itemView)
            }
        }

        btnAgregarPedido.setOnClickListener {
            val cant = etPedCantidad.text.toString().toIntOrNull() ?: 1
            val nom = etPedNombre.text.toString().trim()
            val pre = etPedPrecio.text.toString().toDoubleOrNull() ?: 0.0

            if (nom.isNotEmpty() && pre > 0) {
                val nuevoId = database.child("registros").push().key ?: ""
                val nuevoP = Pedido(nuevoId, cant, nom, pre, true)
                sala.pedidos.add(nuevoP)
                etPedCantidad.text.clear()
                etPedNombre.text.clear()
                etPedPrecio.text.clear()
                redibujarPedidos()
                refrescarTotalUI()

                if (!esNueva) {
                    database.child("salas").child(sala.id.toString()).setValue(sala)
                    val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)

                    val pEntrada = RegistroEntry(
                        idFirebase = nuevoId,
                        tipo = "Consumo",
                        nombreElemento = "Consumo ${sala.nombre}",
                        cliente = sala.cliente ?: "Cliente",
                        celular = sala.celular,
                        fechaHora = fechaHora,
                        pedidos = "${cant}x $nom (${cant * pre} Bs)",
                        totalCobrado = cant * pre,
                        metodoPago = sala.metodoPago,
                        comprobanteUri = rutaComprobanteActual,
                        creadoPor = usuarioActual,
                        eliminado = false
                    )
                    database.child("registros").child(nuevoId).setValue(pEntrada)
                    database.child("auditoria_registros").child(nuevoId).setValue(pEntrada)
                }
            } else {
                Toast.makeText(this, "Ingresa nombre y precio válido", Toast.LENGTH_SHORT).show()
            }
        }

        val btnGuardarCambios = dialogView.findViewById<Button>(R.id.btnGuardarCambios)

        if (esNueva) {
            tvTituloDialog.text = "🎬 Iniciar en ${sala.nombre}"
            btnConfirmar.text = "Iniciar Función"
            btnGuardarCambios?.visibility = View.GONE
            sala.pedidos.clear()
            redibujarPedidos()
            refrescarTotalUI()
        } else {
            tvTituloDialog.text = "⚙️ Gestionar ${sala.nombre}"
            btnConfirmar.text = "Liberar Sala / Cobrar"
            btnGuardarCambios?.visibility = View.VISIBLE
            etCliente.setText(sala.cliente)
            etCelular.setText(sala.celular)
            etPelicula.setText(sala.pelicula)
            etPrecioSala.setText(sala.precioCobrado.toString())
            actualizarVistaPreviaQR(rutaComprobanteActual)

            when {
                sala.metodoPago.startsWith("Mixto", ignoreCase = true) -> {
                    rbPagoMixto.isChecked = true
                    val efec = sala.getEfectivoAmount()
                    if (efec > 0.0) {
                        etMontoEfectivoMixto?.setText(if (efec % 1.0 == 0.0) efec.toLong().toString() else efec.toString())
                    }
                }
                sala.metodoPago.equals("QR", ignoreCase = true) -> rbPagoQR.isChecked = true
                else -> rbPagoEfectivo.isChecked = true
            }

            redibujarPedidos()
            refrescarTotalUI()
        }

        btnGuardarCambios?.setOnClickListener {
            val cliente = etCliente.text.toString().trim()
            val celular = etCelular.text.toString().trim()
            val pelicula = etPelicula.text.toString().trim()
            val tiempoStr = etTiempo.text.toString().trim()
            val precio = etPrecioSala.text.toString().toDoubleOrNull() ?: sala.precioCobrado

            val metodo = when (rgMetodoPago.checkedRadioButtonId) {
                R.id.rbPagoQR -> "QR"
                R.id.rbPagoMixto -> {
                    val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val total = precio + sala.pedidos.sumOf { it.subtotal() }
                    val qr = maxOf(0.0, total - efec)
                    "Mixto (💵 $efec Bs | 📱 $qr Bs)"
                }
                else -> "Efectivo"
            }

            val montoEfec = if (metodo.startsWith("Mixto", true)) (etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0) else if (metodo == "Efectivo") precio else 0.0
            val montoQR = if (metodo.startsWith("Mixto", true)) maxOf(0.0, precio - montoEfec) else if (metodo.contains("QR", true)) precio else 0.0

            sala.cliente = cliente
            sala.celular = celular
            sala.pelicula = pelicula
            sala.precioCobrado = precio
            sala.metodoPago = metodo
            sala.montoEfectivo = montoEfec
            sala.montoQR = montoQR
            sala.comprobanteUri = if (metodo.contains("QR", true) || metodo.startsWith("Mixto", true)) rutaComprobanteActual else null

            database.child("salas").child(sala.id.toString()).setValue(sala)
            actualizarRegistroDeSala(sala)

            val catSala = if (sala.id < 100) "Minicine" else if (sala.id in 101..199) "Autitos" else "Just Dance"
            roomAuditManager.registrarAccionSala(
                tipoAccion = "EDICION_PARAMETROS",
                salaId = sala.id.toString(),
                salaNombre = sala.nombre,
                categoria = catSala,
                tiempoMinutos = 0,
                usuarioNombre = PresenciaManager.obtenerNombreUsuario(this),
                usuarioRol = "Administrador",
                detalles = "Parámetros actualizados: $cliente | $precio Bs ($metodo)"
            )

            Toast.makeText(this, "✅ Cambios guardados y sincronizados en registros", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        etPrecioSala.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refrescarTotalUI() }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnConfirmar.setOnClickListener {
            val cliente = etCliente.text.toString().trim()
            val celular = etCelular.text.toString().trim()
            val pelicula = etPelicula.text.toString().trim()
            val tiempoStr = etTiempo.text.toString().trim()
            val precio = etPrecioSala.text.toString().toDoubleOrNull() ?: 0.0

            val metodo = when (rgMetodoPago.checkedRadioButtonId) {
                R.id.rbPagoQR -> "QR"
                R.id.rbPagoMixto -> {
                    val efec = etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val total = precio + sala.pedidos.sumOf { it.subtotal() }
                    val qr = maxOf(0.0, total - efec)
                    "Mixto (💵 $efec Bs | 📱 $qr Bs)"
                }
                else -> "Efectivo"
            }

            val usuarioActual = PresenciaManager.obtenerNombreUsuario(this)

            if (esNueva) {
                if (cliente.isEmpty()) {
                    Toast.makeText(this, "El nombre del cliente es obligatorio", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                fun ejecutarGuardadoAdmin() {
                    val duracionMillis = convertirTiempoAMilisegundos(tiempoStr) ?: (60 * 60 * 1000L)
                    val cal = Calendar.getInstance()
                    val horaInicio = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                    cal.add(Calendar.MILLISECOND, duracionMillis.toInt())
                    val horaFin = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

                    val montoEfec = if (metodo.startsWith("Mixto", true)) (etMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0) else if (metodo == "Efectivo") precio else 0.0
                    val montoQR = if (metodo.startsWith("Mixto", true)) maxOf(0.0, precio - montoEfec) else if (metodo.contains("QR", true)) precio else 0.0

                    sala.estado = EstadoSala.OCUPADA
                    sala.cliente = cliente
                    sala.celular = celular
                    sala.pelicula = pelicula
                    sala.horaFinProgramada = horaFin
                    sala.endTimeMillis = System.currentTimeMillis() + duracionMillis
                    sala.precioCobrado = precio
                    sala.metodoPago = metodo
                    sala.montoEfectivo = montoEfec
                    sala.montoQR = montoQR
                    sala.comprobanteUri = if (metodo.contains("QR", true) || metodo.startsWith("Mixto", true)) rutaComprobanteActual else null

                    database.child("salas").child(sala.id.toString()).setValue(sala)
                    AlarmUtils.programarAlarmaSala(this, sala.id, sala.nombre, sala.cliente, sala.endTimeMillis!!)

                    // Registrar en Auditoría de Salas con Trazabilidad de Dispositivo
                    val catSala = if (sala.id < 100) "Minicine" else if (sala.id in 101..199) "Autitos" else "Just Dance"
                    roomAuditManager.registrarAccionSala(
                        tipoAccion = "INICIO_TIEMPO",
                        salaId = sala.id.toString(),
                        salaNombre = sala.nombre,
                        categoria = catSala,
                        tiempoMinutos = (duracionMillis / (60 * 1000)).toInt(),
                        usuarioNombre = "Admin",
                        usuarioRol = "Administrador",
                        detalles = "Cliente: $cliente | Película/Juego: $pelicula | Cobro: $precio Bs ($metodo)"
                    )

                    val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    val key = database.child("registros").push().key ?: ""
                    val nuevaEntrada = RegistroEntry(
                        idFirebase = key,
                        tipo = if (sala.id < 100) "Minicine" else if (sala.id in 101..199) "Autitos" else "Just Dance",
                        nombreElemento = sala.nombre,
                        cliente = cliente,
                        celular = celular,
                        peliculaJuego = pelicula,
                        tiempoConfigurado = tiempoStr.ifEmpty { "1:00" },
                        fechaHora = fechaHora,
                        totalCobrado = precio,
                        metodoPago = metodo,
                        montoEfectivo = montoEfec,
                        montoQR = montoQR,
                        comprobanteUri = if (metodo.contains("QR", true) || metodo.startsWith("Mixto", true)) rutaComprobanteActual else null,
                        creadoPor = usuarioActual,
                        eliminado = false
                    )
                    database.child("registros").child(key).setValue(nuevaEntrada)
                    database.child("auditoria_registros").child(key).setValue(nuevaEntrada)

                    for (p in sala.pedidos) {
                        val pKey = database.child("registros").push().key ?: continue
                        val pEntrada = RegistroEntry(
                            idFirebase = pKey,
                            tipo = "Consumo",
                            nombreElemento = "Consumo ${sala.nombre}",
                            cliente = cliente,
                            celular = celular,
                            fechaHora = fechaHora,
                            pedidos = "${p.cantidad}x ${p.nombre} (${p.subtotal()} Bs)",
                            totalCobrado = p.subtotal(),
                            metodoPago = metodo,
                            montoEfectivo = if (metodo == "Efectivo") p.subtotal() else 0.0,
                            montoQR = if (metodo.contains("QR", true)) p.subtotal() else 0.0,
                            comprobanteUri = if (metodo.contains("QR", true) || metodo.startsWith("Mixto", true)) rutaComprobanteActual else null,
                            creadoPor = usuarioActual,
                            eliminado = false
                        )
                        database.child("registros").child(pKey).setValue(pEntrada)
                        database.child("auditoria_registros").child(pKey).setValue(pEntrada)
                    }

                    if (reservaPrevia != null) {
                        database.child("reservas").child(reservaPrevia.idFirebase).removeValue()
                    }

                    Toast.makeText(this, "${sala.nombre} iniciada con éxito", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                if ((rgMetodoPago.checkedRadioButtonId == R.id.rbPagoQR || rgMetodoPago.checkedRadioButtonId == R.id.rbPagoMixto) && rutaComprobanteActual.isNullOrEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("📸 Comprobante QR Requerido")
                        .setMessage("Has seleccionado método de pago por $metodo pero aún no has adjuntado una foto del comprobante.\n\n¿Deseas capturar o seleccionar la foto del comprobante ahora para la auditoría?")
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
                            ejecutarGuardadoAdmin()
                        }
                        .show()
                } else {
                    ejecutarGuardadoAdmin()
                }
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Liberar ${sala.nombre}")
                    .setMessage("¿Deseas finalizar la función y liberar la sala?")
                    .setPositiveButton("Sí, Liberar") { _, _ ->
                        AlarmUtils.cancelarAlarmaSala(this, sala.id)
                        salasNotificadasFin.remove(sala.id)

                        // Sincronizar al registro antes de limpiar el estado en memoria
                        actualizarRegistroDeSala(sala)

                        sala.estado = EstadoSala.DISPONIBLE
                        sala.cliente = null
                        sala.celular = null
                        sala.pelicula = null
                        sala.horaFinProgramada = null
                        sala.endTimeMillis = null
                        sala.precioCobrado = 0.0
                        sala.metodoPago = "Efectivo"
                        sala.comprobanteUri = null
                        sala.pedidos.clear()

                        database.child("salas").child(sala.id.toString()).setValue(sala)
                        Toast.makeText(this, "${sala.nombre} liberada exitosamente", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        val btnEliminarSala = dialogView.findViewById<Button>(R.id.btnEliminarSalaDesdeDialog)
        btnEliminarSala?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("🗑️ Eliminar ${sala.nombre}")
                .setMessage("¿Deseas eliminar permanentemente '${sala.nombre}' de Firebase?")
                .setPositiveButton("Sí, eliminar") { _, _ ->
                    eliminarSalaDeFirebase(sala)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

    private fun mostrarDialogoEditarReserva(reserva: Reserva) { mostrarDialogoReservasConfigurado(reserva) }
    private fun mostrarDialogoNuevaReserva() { mostrarDialogoReservasConfigurado(null) }

    private fun mostrarDialogoReservasConfigurado(reservaExistente: Reserva?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nueva_reserva, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etResNombre = dialogView.findViewById<EditText>(R.id.etResNombre)
        val etResCelular = dialogView.findViewById<EditText>(R.id.etResCelular)
        val btnResFecha = dialogView.findViewById<Button>(R.id.btnResFecha)
        val btnResHora = dialogView.findViewById<Button>(R.id.btnResHora)
        val rgResTipo = dialogView.findViewById<RadioGroup>(R.id.rgResTipo)
        val rbResCine = dialogView.findViewById<RadioButton>(R.id.rbResCine)
        val rbResAuto = dialogView.findViewById<RadioButton>(R.id.rbResAuto)
        val rbResDance = dialogView.findViewById<RadioButton>(R.id.rbResDance)
        val llResVolantesContainer = dialogView.findViewById<LinearLayout>(R.id.llResVolantesContainer)
        val etResVolantes = dialogView.findViewById<EditText>(R.id.etResVolantes)
        val etResDuracion = dialogView.findViewById<EditText>(R.id.etResDuracion)
        val etResAdelanto = dialogView.findViewById<EditText>(R.id.etResAdelanto)
        val rgResMetodoPago = dialogView.findViewById<RadioGroup>(R.id.rgResMetodoPago)
        val rbResPagoEfectivo = dialogView.findViewById<RadioButton>(R.id.rbResPagoEfectivo)
        val rbResPagoQR = dialogView.findViewById<RadioButton>(R.id.rbResPagoQR)
        val rbResPagoMixto = dialogView.findViewById<RadioButton>(R.id.rbResPagoMixto)
        val llResSeccionMixto = dialogView.findViewById<LinearLayout>(R.id.llResSeccionMixto)
        val etResMontoEfectivoMixto = dialogView.findViewById<EditText>(R.id.etResMontoEfectivoMixto)
        val tvResMontoQRMixto = dialogView.findViewById<TextView>(R.id.tvResMontoQRMixto)

        val llResSeccionQR = dialogView.findViewById<LinearLayout>(R.id.llResSeccionQR)
        val btnResTomarFotoQR = dialogView.findViewById<Button>(R.id.btnResTomarFotoQR)
        val btnResGaleriaQR = dialogView.findViewById<Button>(R.id.btnResGaleriaQR)
        val rlResPreviewQR = dialogView.findViewById<View>(R.id.rlResPreviewQR)
        val ivResPreviewQR = dialogView.findViewById<ImageView>(R.id.ivResPreviewQR)
        val tvResVerFotoQR = dialogView.findViewById<TextView>(R.id.tvResVerFotoQR)
        val btnResEliminarFotoQR = dialogView.findViewById<TextView>(R.id.btnResEliminarFotoQR)

        val btnResGuardar = dialogView.findViewById<Button>(R.id.btnResGuardar)
        val btnResCancelar = dialogView.findViewById<Button>(R.id.btnResCancelar)

        var fechaSeleccionada = reservaExistente?.fecha ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        var horaSeleccionada = reservaExistente?.horaProgramada ?: ""
        var rutaComprobanteReserva: String? = reservaExistente?.comprobanteUri

        btnResFecha.text = "📅 $fechaSeleccionada"
        if (horaSeleccionada.isNotEmpty()) btnResHora.text = "⏰ $horaSeleccionada"

        btnResFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                fechaSeleccionada = String.format("%02d/%02d/%d", day, month + 1, year)
                btnResFecha.text = "📅 $fechaSeleccionada"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnResHora.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                horaSeleccionada = String.format("%02d:%02d", hourOfDay, minute)
                btnResHora.text = "⏰ $horaSeleccionada"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        rgResTipo.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbResAuto) {
                llResVolantesContainer.visibility = View.VISIBLE
            } else {
                llResVolantesContainer.visibility = View.GONE
            }
        }

        fun recalcularMixtoReserva() {
            val adelanto = etResAdelanto.text.toString().toDoubleOrNull() ?: 0.0
            val efec = etResMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
            val saldoQR = maxOf(0.0, adelanto - efec)
            tvResMontoQRMixto?.text = "📱 Saldo en QR: $saldoQR Bs"
        }

        etResAdelanto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalcularMixtoReserva() }
            override fun afterTextChanged(s: Editable?) {}
        })

        etResMontoEfectivoMixto?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { recalcularMixtoReserva() }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun actualizarVistaPreviaQRReserva(fullPath: String?) {
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

        rgResMetodoPago.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbResPagoQR -> {
                    llResSeccionQR.visibility = View.VISIBLE
                    llResSeccionMixto?.visibility = View.GONE
                }
                R.id.rbResPagoMixto -> {
                    llResSeccionQR.visibility = View.VISIBLE
                    llResSeccionMixto?.visibility = View.VISIBLE
                    recalcularMixtoReserva()
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
                    rutaComprobanteReserva = ImageUtils.agregarComprobante(rutaComprobanteReserva, path)
                    actualizarVistaPreviaQRReserva(rutaComprobanteReserva)
                }
            }
        }

        btnResGaleriaQR.setOnClickListener {
            seleccionarFotoGaleria { path ->
                if (path != null) {
                    rutaComprobanteReserva = ImageUtils.agregarComprobante(rutaComprobanteReserva, path)
                    actualizarVistaPreviaQRReserva(rutaComprobanteReserva)
                }
            }
        }

        tvResVerFotoQR.setOnClickListener {
            if (rutaComprobanteReserva != null) {
                ImageUtils.mostrarDialogoVerComprobante(this, rutaComprobanteReserva!!, "Comprobante Adelanto") { nuevaCadena ->
                    rutaComprobanteReserva = nuevaCadena
                    actualizarVistaPreviaQRReserva(nuevaCadena)
                }
            }
        }

        btnResEliminarFotoQR.setOnClickListener {
            rutaComprobanteReserva = null
            actualizarVistaPreviaQRReserva(null)
        }

        if (reservaExistente != null) {
            etResNombre.setText(reservaExistente.nombre)
            etResCelular.setText(reservaExistente.celular)
            etResDuracion.setText(reservaExistente.duracion)
            etResAdelanto.setText(reservaExistente.adelanto)
            actualizarVistaPreviaQRReserva(rutaComprobanteReserva)

            when (reservaExistente.tipoSala) {
                "Autitos" -> {
                    rbResAuto.isChecked = true
                    llResVolantesContainer.visibility = View.VISIBLE
                    etResVolantes.setText(reservaExistente.cantidadVolantes.toString())
                }
                "Just Dance" -> rbResDance.isChecked = true
                else -> rbResCine.isChecked = true
            }

            when {
                reservaExistente.metodoPagoAdelanto.startsWith("Mixto", ignoreCase = true) -> rbResPagoMixto.isChecked = true
                reservaExistente.metodoPagoAdelanto.equals("QR", ignoreCase = true) -> rbResPagoQR.isChecked = true
                else -> rbResPagoEfectivo.isChecked = true
            }

            btnResGuardar.text = "Actualizar Reserva"
        }

        btnResGuardar.setOnClickListener {
            val nombre = etResNombre.text.toString().trim()
            val celular = etResCelular.text.toString().trim()
            val duracion = etResDuracion.text.toString().trim()
            val adelanto = etResAdelanto.text.toString().trim()
            val volantes = etResVolantes.text.toString().toIntOrNull() ?: 1

            if (nombre.isEmpty()) {
                Toast.makeText(this, "El nombre del cliente es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (horaSeleccionada.isEmpty()) {
                Toast.makeText(this, "Selecciona la hora de la reserva", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tipoSala = when (rgResTipo.checkedRadioButtonId) {
                R.id.rbResAuto -> "Autitos"
                R.id.rbResDance -> "Just Dance"
                else -> "Minicine"
            }

            val metodo = when (rgResMetodoPago.checkedRadioButtonId) {
                R.id.rbResPagoQR -> "QR"
                R.id.rbResPagoMixto -> {
                    val ad = adelanto.toDoubleOrNull() ?: 0.0
                    val efec = etResMontoEfectivoMixto?.text.toString().toDoubleOrNull() ?: 0.0
                    val qr = maxOf(0.0, ad - efec)
                    "Mixto (💵 $efec Bs | 📱 $qr Bs)"
                }
                else -> "Efectivo"
            }

            if (reservaExistente != null) {
                reservaExistente.nombre = nombre
                reservaExistente.celular = celular
                reservaExistente.fecha = fechaSeleccionada
                reservaExistente.horaProgramada = horaSeleccionada
                reservaExistente.tipoSala = tipoSala
                reservaExistente.duracion = duracion
                reservaExistente.adelanto = adelanto
                reservaExistente.metodoPagoAdelanto = metodo
                reservaExistente.cantidadVolantes = volantes
                reservaExistente.comprobanteUri = rutaComprobanteReserva

                database.child("reservas").child(reservaExistente.idFirebase).setValue(reservaExistente)
                Toast.makeText(this, "Reserva actualizada", Toast.LENGTH_SHORT).show()
            } else {
                val key = database.child("reservas").push().key ?: ""
                val nuevaReserva = Reserva(
                    idFirebase = key,
                    nombre = nombre,
                    celular = celular,
                    fecha = fechaSeleccionada,
                    horaProgramada = horaSeleccionada,
                    tipoSala = tipoSala,
                    duracion = duracion,
                    adelanto = adelanto,
                    metodoPagoAdelanto = metodo,
                    cantidadVolantes = volantes,
                    comprobanteUri = rutaComprobanteReserva,
                    asistio = false
                )
                database.child("reservas").child(key).setValue(nuevaReserva)
                Toast.makeText(this, "Reserva agendada exitosamente", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        btnResCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun exportarCSV(listaAExportar: List<RegistroEntry>) {
        try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("FechaHora,Tipo,Elemento,Cliente,Celular,PeliculaJuego,Tiempo,Pedidos,TotalBs,MetodoPago,Estado,EliminadoPor,FechaEliminacion\n")

            for (r in listaAExportar) {
                val estadoStr = if (r.eliminado) "ELIMINADO" else "ACTIVO"
                val elPor = r.eliminadoPor ?: ""
                val elFecha = r.fechaEliminacion ?: ""
                csvBuilder.append("\"${r.fechaHora}\",\"${r.tipo}\",\"${r.nombreElemento}\",\"${r.cliente}\",\"${r.celular ?: ""}\",\"${r.peliculaJuego ?: ""}\",\"${r.tiempoConfigurado ?: ""}\",\"${r.pedidos}\",${r.totalCobrado},\"${r.metodoPago}\",\"$estadoStr\",\"$elPor\",\"$elFecha\"\n")
            }

            val exportDir = File(cacheDir, "export")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, "auditoria_justplay_${System.currentTimeMillis()}.csv")
            file.writeText(csvBuilder.toString())

            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Reporte Auditoría Just Play")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir Reporte CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar CSV: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            posManager.mostrarDialogoCrearEditarProducto(codigoExistente = barcode)
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
                    .setTitle("🛒 Venta Rápida por Lector OTG")
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
                            creadoPor = "Admin OTG (${DeviceInfoProvider.getDeviceModel()})",
                            eliminado = false
                        )
                        database.child("registros").child(regKey).setValue(ventaEntry)
                        database.child("auditoria_registros").child(regKey).setValue(ventaEntry)

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
                            usuarioNombre = "Admin",
                            usuarioRol = "Administrador",
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