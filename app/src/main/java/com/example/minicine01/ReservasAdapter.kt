package com.example.minicine01

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReservasAdapter(
    private val listaReservas: List<Reserva>,
    private val onEditarReserva: (Reserva) -> Unit,
    private val onAsignarSala: (Reserva) -> Unit
) : RecyclerView.Adapter<ReservasAdapter.ReservaViewHolder>() {

    class ReservaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHora: TextView = view.findViewById(R.id.tvResHora)
        val tvFecha: TextView = view.findViewById(R.id.tvResFecha)
        val tvTipoBadge: TextView = view.findViewById(R.id.tvResTipoBadge)
        val tvNombre: TextView = view.findViewById(R.id.tvResNombre)
        val tvDetalles: TextView = view.findViewById(R.id.tvResDetalles)
        val tvVolantes: TextView = view.findViewById(R.id.tvResVolantes)
        val llAdelantoContainer: View = view.findViewById(R.id.llAdelantoContainer)
        val tvAdelanto: TextView = view.findViewById(R.id.tvResAdelanto)
        val btnVerComprobante: TextView = view.findViewById(R.id.btnVerComprobanteReserva)
        val tvHoraLlegada: TextView = view.findViewById(R.id.tvResHoraLlegada)
        val btnMarcarAsistencia: Button = view.findViewById(R.id.btnMarcarAsistencia)
        val btnAsignarSala: Button = view.findViewById(R.id.btnAsignarSala)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reserva, parent, false)
        return ReservaViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservaViewHolder, position: Int) {
        val reserva = listaReservas[position]

        // Fecha y Hora con alta legibilidad
        holder.tvHora.text = "⏰ ${reserva.horaProgramada}"
        holder.tvFecha.text = "🗓️ ${reserva.fecha}"
        holder.tvTipoBadge.text = reserva.tipoSala

        holder.tvNombre.text = reserva.nombre
        val textoDuracion = if (reserva.duracion.isNotEmpty()) " | Tiempo: ${reserva.duracion}" else ""
        holder.tvDetalles.text = "Cel: ${if (reserva.celular.isNotEmpty()) reserva.celular else "S/N"}$textoDuracion"

        // Cantidad de volantes (Solo para Autitos)
        if (reserva.tipoSala.equals("Autitos", ignoreCase = true)) {
            holder.tvVolantes.visibility = View.VISIBLE
            val volantes = if (reserva.cantidadVolantes > 0) reserva.cantidadVolantes else 1
            holder.tvVolantes.text = "🏎️ Volantes reservados: $volantes"
        } else {
            holder.tvVolantes.visibility = View.GONE
        }

        // Adelanto y Comprobante
        if (reserva.adelanto.isNotEmpty()) {
            holder.llAdelantoContainer.visibility = View.VISIBLE
            val metodo = if (reserva.metodoPagoAdelanto.equals("QR", ignoreCase = true)) "📱 QR" else "💵 Efectivo"
            holder.tvAdelanto.text = "Adelanto: ${reserva.adelanto} Bs ($metodo)"

            if (!reserva.comprobanteUri.isNullOrEmpty()) {
                holder.btnVerComprobante.visibility = View.VISIBLE
                holder.btnVerComprobante.setOnClickListener {
                    ImageUtils.mostrarDialogoVerComprobante(
                        holder.itemView.context,
                        reserva.comprobanteUri!!,
                        "Comprobante Adelanto: ${reserva.nombre}"
                    )
                }
            } else {
                holder.btnVerComprobante.visibility = View.GONE
            }
        } else {
            holder.llAdelantoContainer.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onEditarReserva(reserva) }

        if (reserva.asistio) {
            holder.btnMarcarAsistencia.visibility = View.GONE
            holder.tvHoraLlegada.visibility = View.VISIBLE
            holder.tvHoraLlegada.text = "Llegó a las: ${reserva.horaLlegada}"

            holder.btnAsignarSala.visibility = View.VISIBLE
            holder.btnAsignarSala.setOnClickListener { onAsignarSala(reserva) }
        } else {
            holder.btnMarcarAsistencia.visibility = View.VISIBLE
            holder.tvHoraLlegada.visibility = View.GONE
            holder.btnAsignarSala.visibility = View.GONE

            holder.btnMarcarAsistencia.setOnClickListener {
                reserva.asistio = true
                reserva.horaLlegada = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                FirebaseDatabase.getInstance().reference
                    .child("reservas")
                    .child(reserva.idFirebase)
                    .setValue(reserva)

                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount() = listaReservas.size
}