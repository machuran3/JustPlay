package com.example.adminjustplay

import android.app.AlertDialog
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class VetadosAdapter(
    private val listaVetados: List<ClienteVetado>,
    private val onEditar: (ClienteVetado) -> Unit,
    private val onEliminar: (ClienteVetado) -> Unit
) : RecyclerView.Adapter<VetadosAdapter.VetadoViewHolder>() {

    class VetadoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvVetNombre)
        val tvCelular: TextView = view.findViewById(R.id.tvVetCelular)
        val tvMotivo: TextView = view.findViewById(R.id.tvVetMotivo)
        val btnEditar: TextView = view.findViewById(R.id.btnEditarVetado)
        val btnEliminar: TextView = view.findViewById(R.id.btnEliminarVetado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VetadoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vetado, parent, false)
        return VetadoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VetadoViewHolder, position: Int) {
        val vetado = listaVetados[position]
        holder.tvNombre.text = "Nombre: ${if (vetado.nombre.isNullOrEmpty()) "Desconocido" else vetado.nombre}"
        holder.tvCelular.text = "Celular: ${vetado.celular}"
        holder.tvMotivo.text = "Motivo: ${if (vetado.motivo.isNullOrEmpty()) "Sin notas especificadas" else vetado.motivo}"

        holder.btnEditar.setOnClickListener { onEditar(vetado) }
        holder.btnEliminar.setOnClickListener {
            val context = holder.itemView.context
            AlertDialog.Builder(context)
                .setTitle("Eliminar de Lista Negra")
                .setMessage("¿Deseas quitar a ${vetado.nombre ?: vetado.celular} de clientes vetados?")
                .setPositiveButton("Sí, eliminar") { dialog, _ ->
                    onEliminar(vetado)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun getItemCount() = listaVetados.size
}
