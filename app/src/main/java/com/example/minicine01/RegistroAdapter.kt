package com.example.minicine01

import android.app.AlertDialog
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class RegistroAdapter(
    private val listaRegistro: List<RegistroEntry>,
    private val onEliminarRegistro: (RegistroEntry) -> Unit
) : RecyclerView.Adapter<RegistroAdapter.RegistroViewHolder>() {

    class RegistroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegistroViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_registro, parent, false)
        return RegistroViewHolder(view)
    }

    override fun onBindViewHolder(holder: RegistroViewHolder, position: Int) {
        val registro = listaRegistro[position]

        // Fecha y hora real del registro
        holder.tvRegFechaHora.text = registro.fechaHora

        // Cliente y celular
        val textoCliente = if (!registro.celular.isNullOrEmpty() && registro.celular != "S/N") {
            "👤 Cliente: ${registro.cliente} (Cel: ${registro.celular})"
        } else {
            "👤 Cliente: ${registro.cliente}"
        }
        holder.tvRegCliente.text = textoCliente

        if (registro.tipo.equals("Consumo", ignoreCase = true)) {
            val nombreLimpio = registro.nombreElemento.replace("Consumo ", "").trim()
            holder.tvRegTitulo.text = "🍿 Consumo $nombreLimpio"
        } else if (registro.tipo.equals("Venta Directa", ignoreCase = true)) {
            holder.tvRegTitulo.text = "🛒 Venta de Mostrador"
        } else {
            holder.tvRegTitulo.text = "${registro.tipo} - ${registro.nombreElemento}"
        }
        holder.tvRegDetalles.text = "Película/Juego: ${registro.peliculaJuego ?: "N/A"} | Tiempo: ${registro.tiempoConfigurado ?: "N/A"}"
        holder.tvRegPedidos.text = "Pedidos: ${if (registro.pedidos.isEmpty()) "Ninguno" else registro.pedidos}"
        holder.tvRegTotal.text = "Total: ${registro.totalCobrado} Bs"

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

        // Comprobante QR
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
                        com.google.firebase.database.FirebaseDatabase.getInstance().reference
                            .child("registros")
                            .child(registro.idFirebase)
                            .child("comprobanteUri")
                            .setValue(nuevaCadena)
                    }
                    if (nuevaCadena.isNullOrEmpty()) {
                        holder.btnVerComprobante.visibility = View.GONE
                    }
                }
            }
        } else {
            holder.btnVerComprobante.visibility = View.GONE
        }

        holder.btnEliminarRegistro.setOnClickListener {
            val context = holder.itemView.context

            val input = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "Código de seguridad"
            }

            AlertDialog.Builder(context)
                .setTitle("Eliminar Registro")
                .setMessage("Esta acción no se puede deshacer. Ingresa el código de confirmación:")
                .setView(input)
                .setPositiveButton("Eliminar") { dialog, _ ->
                    val codigo = input.text.toString()
                    if (codigo == "0001") {
                        onEliminarRegistro(registro)
                        Toast.makeText(context, "Registro eliminado exitosamente", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Código incorrecto", Toast.LENGTH_LONG).show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    override fun getItemCount() = listaRegistro.size
}
