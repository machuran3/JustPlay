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

class TerminalesPosAdapter(
    private val listaTerminales: List<TerminalPos>,
    private val onRenombrar: (TerminalPos) -> Unit,
    private val onToggleAutorizar: (TerminalPos) -> Unit
) : RecyclerView.Adapter<TerminalesPosAdapter.TerminalViewHolder>() {

    class TerminalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardTerminalPos: CardView = view.findViewById(R.id.cardTerminalPos)
        val tvBannerTerminalRevocada: TextView = view.findViewById(R.id.tvBannerTerminalRevocada)
        val tvNombreTerminalPos: TextView = view.findViewById(R.id.tvNombreTerminalPos)
        val tvIdTerminalPos: TextView = view.findViewById(R.id.tvIdTerminalPos)
        val tvEstadoConexionTerminal: TextView = view.findViewById(R.id.tvEstadoConexionTerminal)
        val tvHostnameIP: TextView = view.findViewById(R.id.tvHostnameIP)
        val tvOSVersion: TextView = view.findViewById(R.id.tvOSVersion)
        val tvUltimoHeartbeat: TextView = view.findViewById(R.id.tvUltimoHeartbeat)
        val btnRenombrarTerminal: Button = view.findViewById(R.id.btnRenombrarTerminal)
        val btnToggleAutorizarTerminal: Button = view.findViewById(R.id.btnToggleAutorizarTerminal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TerminalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_terminal_pos, parent, false)
        return TerminalViewHolder(view)
    }

    override fun onBindViewHolder(holder: TerminalViewHolder, position: Int) {
        val terminal = listaTerminales[position]

        holder.tvNombreTerminalPos.text = terminal.nombre.ifBlank { "PC Terminal ${terminal.deviceId.takeLast(4)}" }
        holder.tvIdTerminalPos.text = "ID: ${terminal.deviceId}"

        val isOnline = terminal.isCurrentlyOnline()
        if (isOnline) {
            holder.tvEstadoConexionTerminal.text = "🟢 En Línea"
            holder.tvEstadoConexionTerminal.setTextColor(Color.parseColor("#00E676"))
            holder.tvEstadoConexionTerminal.setBackgroundColor(Color.parseColor("#1B3E2B"))
        } else {
            holder.tvEstadoConexionTerminal.text = "⚪ Desconectado"
            holder.tvEstadoConexionTerminal.setTextColor(Color.parseColor("#A0A0A0"))
            holder.tvEstadoConexionTerminal.setBackgroundColor(Color.parseColor("#2A2C39"))
        }

        val hostname = if (terminal.hostname.isNotBlank()) terminal.hostname else "N/A"
        val ip = if (terminal.ipAddress.isNotBlank()) terminal.ipAddress else "N/A"
        holder.tvHostnameIP.text = "🌐 Hostname: $hostname | IP: $ip"

        val os = if (terminal.osName.isNotBlank()) terminal.osName else "Escritorio"
        holder.tvOSVersion.text = "⚙️ Sistema: $os • JustPlay POS v${terminal.appVersion}"

        if (terminal.lastHeartbeat > 0L) {
            val diffMs = System.currentTimeMillis() - terminal.lastHeartbeat
            val diffSec = diffMs / 1000
            val tiempoLatido = when {
                diffSec < 60 -> "Hace $diffSec segundos"
                diffSec < 3600 -> "Hace ${diffSec / 60} minutos"
                else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(terminal.lastHeartbeat))
            }
            holder.tvUltimoHeartbeat.text = "⏱️ Último latido: $tiempoLatido"
        } else if (terminal.lastHeartbeatStr.isNotBlank()) {
            holder.tvUltimoHeartbeat.text = "⏱️ Último latido: ${terminal.lastHeartbeatStr}"
        } else {
            holder.tvUltimoHeartbeat.text = "⏱️ Sin latido registrado"
        }

        if (!terminal.autorizado) {
            holder.tvBannerTerminalRevocada.visibility = View.VISIBLE
            holder.tvBannerTerminalRevocada.text = "🚫 ACCESO REVOCADO • COMPUTADORA BLOQUEADA"
            holder.cardTerminalPos.setCardBackgroundColor(Color.parseColor("#2A1C1C"))
            holder.btnToggleAutorizarTerminal.text = "✅ Autorizar"
            holder.btnToggleAutorizarTerminal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        } else {
            holder.tvBannerTerminalRevocada.visibility = View.GONE
            holder.cardTerminalPos.setCardBackgroundColor(Color.parseColor("#1A1C29"))
            holder.btnToggleAutorizarTerminal.text = "🚫 Revocar"
            holder.btnToggleAutorizarTerminal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        }

        holder.btnRenombrarTerminal.setOnClickListener { onRenombrar(terminal) }
        holder.btnToggleAutorizarTerminal.setOnClickListener { onToggleAutorizar(terminal) }
    }

    override fun getItemCount() = listaTerminales.size
}
