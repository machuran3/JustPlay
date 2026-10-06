package com.example.adminjustplay

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase

class RegistroAdminAdapter(
    private val listaRegistro: List<RegistroEntry>,
    private val onEliminarRegistro: (RegistroEntry) -> Unit,
    private val onRestaurarRegistro: (RegistroEntry) -> Unit
) : RecyclerView.Adapter<RegistroAdminAdapter.RegistroViewHolder>() {

    class RegistroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.cardRegistroAdmin)
        val tvRegFechaHora: TextView = view.findViewById(R.id.tvRegFechaHora)
        val tvRegMetodoPago: TextView = view.findViewById(R.id.tvRegMetodoPago)
        val tvRegTitulo: TextView = view.findViewById(R.id.tvRegTitulo)
        val tvRegCliente: TextView = view.findViewById(R.id.tvRegCliente)
        val tvRegDetalles: TextView = view.findViewById(R.id.tvRegDetalles)
        val tvRegVolantes: TextView = view.findViewById(R.id.tvRegVolantes)
        val tvRegPedidos: TextView = view.findViewById(R.id.tvRegPedidos)
        val tvRegTotal: TextView = view.findViewById(R.id.tvRegTotal)
        val btnVerComprobante: TextView = view.findViewById(R.id.btnVerComprobanteRegistro)
        val btnEliminarRegistro: TextView = view.findViewById(R.id.btnEliminarRegistro)

        // Elementos de Auditoría y Registros Eliminados
        val llBannerEliminado: LinearLayout = view.findViewById(R.id.llBannerEliminado)
        val tvInfoEliminacion: TextView = view.findViewById(R.id.tvInfoEliminacion)
        val btnRestaurarRegistro: Button = view.findViewById(R.id.btnRestaurarRegistro)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegistroViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_registro_admin, parent, false)
        return RegistroViewHolder(view)
    }

    override fun onBindViewHolder(holder: RegistroViewHolder, position: Int) {
        val registro = listaRegistro[position]

        // Fecha y hora del registro
        holder.tvRegFechaHora.text = registro.fechaHora

        // Cliente y celular
        val textoCliente = if (!registro.celular.isNullOrEmpty() && registro.celular != "S/N") {
            "👤 Cliente: ${registro.cliente} (Cel: ${registro.celular})"
        } else {
            "👤 Cliente: ${registro.cliente}"
        }
        holder.tvRegCliente.text = textoCliente

        if (registro.tipo.equals("Egreso", ignoreCase = true) || registro.nombreElemento.startsWith("Egreso", ignoreCase = true)) {
            holder.tvRegTitulo.text = "💸 ${registro.nombreElemento}"
            holder.tvRegDetalles.text = "Detalle: ${registro.cliente} | Registrado por: ${registro.creadoPor ?: "Personal"}"
            holder.tvRegPedidos.text = "Gasto Operativo / Salida de Caja"
            holder.tvRegTotal.text = "Egreso: -${registro.totalCobrado} Bs"
            holder.tvRegTotal.setTextColor(Color.parseColor("#FF5252"))
        } else if (registro.tipo.equals("Consumo", ignoreCase = true)) {
            val nombreLimpio = registro.nombreElemento.replace("Consumo ", "").trim()
            holder.tvRegTitulo.text = "🍿 Consumo $nombreLimpio"
            holder.tvRegDetalles.text = "Película/Juego: ${registro.peliculaJuego ?: "N/A"} | Tiempo: ${registro.tiempoConfigurado ?: "N/A"}"
            holder.tvRegPedidos.text = "Pedidos: ${if (registro.pedidos.isEmpty()) "Ninguno" else registro.pedidos}"
            holder.tvRegTotal.text = "Total: ${registro.totalCobrado} Bs"
            holder.tvRegTotal.setTextColor(Color.parseColor("#00E676"))
        } else if (registro.tipo.equals("Venta Directa", ignoreCase = true)) {
            holder.tvRegTitulo.text = "🛒 Venta de Mostrador"
            holder.tvRegDetalles.text = "Película/Juego: ${registro.peliculaJuego ?: "N/A"} | Tiempo: ${registro.tiempoConfigurado ?: "N/A"}"
            holder.tvRegPedidos.text = "Pedidos: ${if (registro.pedidos.isEmpty()) "Ninguno" else registro.pedidos}"
            holder.tvRegTotal.text = "Total: ${registro.totalCobrado} Bs"
            holder.tvRegTotal.setTextColor(Color.parseColor("#00E676"))
        } else {
            holder.tvRegTitulo.text = "${registro.tipo} - ${registro.nombreElemento}"
            holder.tvRegDetalles.text = "Película/Juego: ${registro.peliculaJuego ?: "N/A"} | Tiempo: ${registro.tiempoConfigurado ?: "N/A"}"
            holder.tvRegPedidos.text = "Pedidos: ${if (registro.pedidos.isEmpty()) "Ninguno" else registro.pedidos}"
            holder.tvRegTotal.text = "Total: ${registro.totalCobrado} Bs"
            holder.tvRegTotal.setTextColor(Color.parseColor("#00E676"))
        }

        // Método de pago
        val metodo = when {
            registro.metodoPago.startsWith("Mixto", ignoreCase = true) -> "💵📱 ${registro.metodoPago}"
            registro.metodoPago.equals("QR", ignoreCase = true) -> "📱 QR"
            else -> "💵 Efectivo"
        }
        holder.tvRegMetodoPago.text = metodo

        // Volantes
        if (registro.cantidadVolantes != null && registro.cantidadVolantes!! > 0) {
            holder.tvRegVolantes.visibility = View.VISIBLE
            holder.tvRegVolantes.text = "🏎️ Volantes: ${registro.cantidadVolantes}"
        } else {
            holder.tvRegVolantes.visibility = View.GONE
        }

        // COMPROBANTE QR
        if (!registro.comprobanteUri.isNullOrEmpty()) {
            holder.btnVerComprobante.visibility = View.VISIBLE
            holder.btnVerComprobante.setOnClickListener {
                ImageUtils.mostrarDialogoVerComprobante(
                    holder.itemView.context,
                    registro.comprobanteUri!!,
                    "Comprobante: ${registro.nombreElemento} (${registro.cliente})"
                ) { nuevaCadena ->
                    registro.comprobanteUri = nuevaCadena
                    if (registro.idFirebase.isNotEmpty()) {
                        FirebaseDatabase.getInstance().reference
                            .child("auditoria_registros")
                            .child(registro.idFirebase)
                            .child("comprobanteUri")
                            .setValue(nuevaCadena)

                        if (!registro.eliminado) {
                            FirebaseDatabase.getInstance().reference
                                .child("registros")
                                .child(registro.idFirebase)
                                .child("comprobanteUri")
                                .setValue(nuevaCadena)
                        }
                    }
                    if (nuevaCadena.isNullOrEmpty()) {
                        holder.btnVerComprobante.visibility = View.GONE
                    }
                }
            }
        } else {
            holder.btnVerComprobante.visibility = View.GONE
        }

        // CONTROL AUDITORÍA: SI EL REGISTRO FUE BORRADO DE LA BASE DE DATOS
        if (registro.eliminado) {
            // Estilo visual de eliminado / advertencia
            holder.cardView.setCardBackgroundColor(Color.parseColor("#261418"))
            holder.llBannerEliminado.visibility = View.VISIBLE
            val eliminadoPor = if (!registro.eliminadoPor.isNullOrBlank()) registro.eliminadoPor else "Operador"
            val fechaElim = if (!registro.fechaEliminacion.isNullOrBlank()) registro.fechaEliminacion else "Desconocida"
            holder.tvInfoEliminacion.text = "⚠️ BORRADO DE BD\nPor: $eliminadoPor | Fecha: $fechaElim"

            holder.btnRestaurarRegistro.visibility = View.VISIBLE
            holder.btnRestaurarRegistro.setOnClickListener {
                onRestaurarRegistro(registro)
            }

            // Ocultar botón eliminar para eliminados o cambiar a Permanente
            holder.btnEliminarRegistro.visibility = View.GONE
        } else {
            // Registro activo normal
            holder.cardView.setCardBackgroundColor(Color.parseColor("#1A1C29"))
            holder.llBannerEliminado.visibility = View.GONE
            holder.btnRestaurarRegistro.visibility = View.GONE
            holder.btnEliminarRegistro.visibility = View.VISIBLE

            holder.btnEliminarRegistro.setOnClickListener {
                val context = holder.itemView.context
                AlertDialog.Builder(context)
                    .setTitle("Eliminar Registro")
                    .setMessage("¿Deseas enviar este registro a la papelera de auditoría?")
                    .setPositiveButton("Eliminar") { dialog, _ ->
                        onEliminarRegistro(registro)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    override fun getItemCount() = listaRegistro.size
}
