package com.example.adminjustplay

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsuariosOnlineAdapter(
    private val listaUsuarios: List<UsuarioPresencia>,
    private val onGestionarUsuario: (UsuarioPresencia) -> Unit,
    private val onToggleBloqueo: (UsuarioPresencia) -> Unit
) : RecyclerView.Adapter<UsuariosOnlineAdapter.UsuarioViewHolder>() {

    class UsuarioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardUsuario: CardView = view.findViewById(R.id.cardUsuario)
        val tvBannerBloqueado: TextView = view.findViewById(R.id.tvBannerBloqueado)
        val tvAvatarInicial: TextView = view.findViewById(R.id.tvAvatarInicial)
        val tvNombreUsuario: TextView = view.findViewById(R.id.tvNombreUsuario)
        val tvRolApp: TextView = view.findViewById(R.id.tvRolApp)
        val tvPinDispositivo: TextView = view.findViewById(R.id.tvPinDispositivo)
        val tvTiempoRestanteAcceso: TextView = view.findViewById(R.id.tvTiempoRestanteAcceso)
        val tvEstadoConexion: TextView = view.findViewById(R.id.tvEstadoConexion)
        val tvDispositivo: TextView = view.findViewById(R.id.tvDispositivo)
        val tvUltimaActividad: TextView = view.findViewById(R.id.tvUltimaActividad)
        val tvSeccionActual: TextView = view.findViewById(R.id.tvSeccionActual)
        val btnQuickToggleBloqueo: Button = view.findViewById(R.id.btnQuickToggleBloqueo)
        val btnGestionarUsuarioItem: TextView = view.findViewById(R.id.btnGestionarUsuarioItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_usuario_online, parent, false)
        return UsuarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        val usuario = listaUsuarios[position]

        // Inicial para el avatar
        val inicial = if (usuario.nombre.isNotBlank()) usuario.nombre.first().uppercase() else if (usuario.rol == "Administrador") "A" else "E"
        holder.tvAvatarInicial.text = inicial

        // Nombre
        holder.tvNombreUsuario.text = if (usuario.nombre.isNotBlank()) usuario.nombre else "Dispositivo ${usuario.id}"

        // Rol y App
        holder.tvRolApp.text = "${usuario.rol} • ${usuario.app}"

        // PIN
        holder.tvPinDispositivo.text = "🔑 PIN: ${if (usuario.pin.isNotBlank()) usuario.pin else "0001"}"

        // Tiempo restante de acceso autorizado
        val tiempoTexto = usuario.obtenerTiempoRestanteTexto()
        holder.tvTiempoRestanteAcceso.text = tiempoTexto
        if (usuario.estaExpirado()) {
            holder.tvTiempoRestanteAcceso.setTextColor(Color.parseColor("#FF5252"))
        } else {
            holder.tvTiempoRestanteAcceso.setTextColor(Color.parseColor("#FFC107"))
        }

        // Dispositivo
        holder.tvDispositivo.text = "📱 ${if (usuario.dispositivo.isNotBlank()) usuario.dispositivo else "Android"}"

        // Botón rápido de activación / desactivación
        if (usuario.bloqueado) {
            holder.btnQuickToggleBloqueo.text = "✅ Activar"
            holder.btnQuickToggleBloqueo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        } else {
            holder.btnQuickToggleBloqueo.text = "🚫 Desactivar"
            holder.btnQuickToggleBloqueo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        }
        holder.btnQuickToggleBloqueo.setOnClickListener { onToggleBloqueo(usuario) }

        // Estado Bloqueado o Expirado
        if (usuario.bloqueado) {
            holder.tvBannerBloqueado.visibility = View.VISIBLE
            holder.tvBannerBloqueado.text = "🚫 DISPOSITIVO DESACTIVADO (${usuario.motivoBloqueo})"
            holder.cardUsuario.setCardBackgroundColor(Color.parseColor("#2A1C1C"))
        } else if (usuario.estaExpirado()) {
            holder.tvBannerBloqueado.visibility = View.VISIBLE
            holder.tvBannerBloqueado.text = "⏰ TIEMPO DE CONEXIÓN EXPIRADO (Requiere reactivación)"
            holder.cardUsuario.setCardBackgroundColor(Color.parseColor("#2A1C1C"))
        } else {
            holder.tvBannerBloqueado.visibility = View.GONE
            holder.cardUsuario.setCardBackgroundColor(Color.parseColor("#1A1C29"))
        }

        // Estado en línea y última conexión
        if (usuario.enLinea) {
            holder.tvEstadoConexion.text = "🟢 En Línea"
            holder.tvEstadoConexion.setTextColor(Color.parseColor("#00E676"))
            holder.tvEstadoConexion.setBackgroundColor(Color.parseColor("#1B3E2B"))

            holder.tvSeccionActual.visibility = View.VISIBLE
            holder.tvSeccionActual.text = "📍 En: ${usuario.seccionActual}"

            holder.tvUltimaActividad.text = "Activo en este momento"
        } else {
            holder.tvEstadoConexion.text = "⚪ Desconectado"
            holder.tvEstadoConexion.setTextColor(Color.parseColor("#A0A0A0"))
            holder.tvEstadoConexion.setBackgroundColor(Color.parseColor("#2A2C39"))

            holder.tvSeccionActual.visibility = View.GONE

            val textoTiempo = if (usuario.ultimaConexion > 0) {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(usuario.ultimaConexion))
            } else if (usuario.ultimaConexionTexto.isNotBlank()) {
                usuario.ultimaConexionTexto
            } else {
                "Recientemente"
            }
            holder.tvUltimaActividad.text = "Última vez: $textoTiempo"
        }

        holder.cardUsuario.setOnClickListener { onGestionarUsuario(usuario) }
        holder.btnGestionarUsuarioItem.setOnClickListener { onGestionarUsuario(usuario) }
    }

    override fun getItemCount() = listaUsuarios.size
}
