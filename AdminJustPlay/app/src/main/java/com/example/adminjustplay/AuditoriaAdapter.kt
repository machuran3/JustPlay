package com.example.adminjustplay

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

enum class TipoAuditoriaEvento {
    VENTA,
    SALA_INICIO,
    MODIFICACION,
    FINALIZACION,
    ELIMINACION,
    EGRESO,
    PRODUCTO
}

data class AuditoriaEvento(
    val id: String = "",
    val tipo: TipoAuditoriaEvento = TipoAuditoriaEvento.VENTA,
    val categoria: String = "",
    val tituloBadge: String = "🛒 VENTA",
    val usuarioNombre: String = "Personal",
    val usuarioRol: String = "Empleado",
    val dispositivo: String = "",
    val fechaHora: String = "",
    val timestamp: Long = 0L,
    val elemento: String = "",
    val cliente: String = "",
    val celular: String? = null,
    val monto: Double = 0.0,
    val metodoPago: String = "",
    val comprobanteUri: String? = null,
    val detalles: String = ""
)

class AuditoriaAdapter(
    private val listaEventos: List<AuditoriaEvento>,
    private val onVerComprobante: (AuditoriaEvento) -> Unit
) : RecyclerView.Adapter<AuditoriaAdapter.AuditoriaViewHolder>() {

    class AuditoriaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardAuditoriaEvento)
        val tvBadgeTipo: TextView = view.findViewById(R.id.tvAuditBadgeTipo)
        val tvFechaHora: TextView = view.findViewById(R.id.tvAuditFechaHora)
        val tvUsuario: TextView = view.findViewById(R.id.tvAuditUsuario)
        val tvDispositivo: TextView = view.findViewById(R.id.tvAuditDispositivo)
        val tvElemento: TextView = view.findViewById(R.id.tvAuditElemento)
        val tvCliente: TextView = view.findViewById(R.id.tvAuditCliente)
        val tvMontoPago: TextView = view.findViewById(R.id.tvAuditMontoPago)
        val tvDetalles: TextView = view.findViewById(R.id.tvAuditDetalles)
        val btnVerComprobante: TextView = view.findViewById(R.id.btnVerComprobanteAudit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuditoriaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_auditoria_evento, parent, false)
        return AuditoriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AuditoriaViewHolder, position: Int) {
        val ev = listaEventos[position]

        // Badge y color según tipo
        holder.tvBadgeTipo.text = ev.tituloBadge
        when (ev.tipo) {
            TipoAuditoriaEvento.VENTA -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#00E676"))
                holder.tvBadgeTipo.setTextColor(Color.BLACK)
            }
            TipoAuditoriaEvento.SALA_INICIO -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#DEFF9A"))
                holder.tvBadgeTipo.setTextColor(Color.BLACK)
            }
            TipoAuditoriaEvento.MODIFICACION -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#FFD54F"))
                holder.tvBadgeTipo.setTextColor(Color.BLACK)
            }
            TipoAuditoriaEvento.FINALIZACION -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#64B5F6"))
                holder.tvBadgeTipo.setTextColor(Color.BLACK)
            }
            TipoAuditoriaEvento.ELIMINACION -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#FF5252"))
                holder.tvBadgeTipo.setTextColor(Color.WHITE)
            }
            TipoAuditoriaEvento.EGRESO -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#FF8A80"))
                holder.tvBadgeTipo.setTextColor(Color.BLACK)
            }
            TipoAuditoriaEvento.PRODUCTO -> {
                holder.tvBadgeTipo.setBackgroundColor(Color.parseColor("#BA68C8"))
                holder.tvBadgeTipo.setTextColor(Color.WHITE)
            }
        }

        holder.tvFechaHora.text = "⏰ ${ev.fechaHora}"

        // Usuario y rol
        val rolTexto = if (ev.usuarioRol.isNotBlank()) " (${ev.usuarioRol})" else ""
        holder.tvUsuario.text = "🧑‍💼 Usuario: ${ev.usuarioNombre}$rolTexto"

        // Dispositivo
        if (ev.dispositivo.isNotBlank()) {
            holder.tvDispositivo.visibility = View.VISIBLE
            holder.tvDispositivo.text = "📱 Dispositivo: ${ev.dispositivo}"
        } else {
            holder.tvDispositivo.visibility = View.GONE
        }

        // Elemento
        holder.tvElemento.text = ev.elemento

        // Cliente
        if (ev.cliente.isNotBlank()) {
            holder.tvCliente.visibility = View.VISIBLE
            val celText = if (!ev.celular.isNullOrBlank() && ev.celular != "S/N") " (📱 Cel: ${ev.celular})" else ""
            holder.tvCliente.text = "👤 Cliente: ${ev.cliente}$celText"
        } else {
            holder.tvCliente.visibility = View.GONE
        }

        // Monto y pago
        if (ev.monto > 0 || ev.metodoPago.isNotBlank()) {
            holder.tvMontoPago.visibility = View.VISIBLE
            val signo = if (ev.tipo == TipoAuditoriaEvento.EGRESO) "-" else ""
            val colorMonto = if (ev.tipo == TipoAuditoriaEvento.EGRESO || ev.tipo == TipoAuditoriaEvento.ELIMINACION) "#FF5252" else "#00E676"
            holder.tvMontoPago.setTextColor(Color.parseColor(colorMonto))
            val metodoText = if (ev.metodoPago.isNotBlank()) " (${ev.metodoPago})" else ""
            holder.tvMontoPago.text = "💰 ${if (ev.tipo == TipoAuditoriaEvento.EGRESO) "Egreso" else "Importe"}: $signo${ev.monto} Bs$metodoText"
        } else {
            holder.tvMontoPago.visibility = View.GONE
        }

        // Detalles
        if (ev.detalles.isNotBlank()) {
            holder.tvDetalles.visibility = View.VISIBLE
            holder.tvDetalles.text = "📝 ${ev.detalles}"
        } else {
            holder.tvDetalles.visibility = View.GONE
        }

        // Comprobante
        if (!ev.comprobanteUri.isNullOrBlank()) {
            holder.btnVerComprobante.visibility = View.VISIBLE
            holder.btnVerComprobante.setOnClickListener {
                onVerComprobante(ev)
            }
        } else {
            holder.btnVerComprobante.visibility = View.GONE
        }
    }

    override fun getItemCount() = listaEventos.size
}
