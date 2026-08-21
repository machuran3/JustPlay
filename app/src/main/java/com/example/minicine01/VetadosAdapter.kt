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

        // FUNCIÓN DE SEGURIDAD
        fun pedirCodigo(accion: () -> Unit) {
            val context = holder.itemView.context
            val input = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "Código de administrador (0002)"
            }
            AlertDialog.Builder(context)
                .setTitle("Seguridad Requerida")
                .setMessage("Ingrese el código de seguridad para modificar vetados:")
                .setView(input)
                .setPositiveButton("Confirmar") { dialog, _ ->
                    if (input.text.toString() == "0002") {
                        accion()
                    } else {
                        Toast.makeText(context, "Código incorrecto", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        holder.btnEditar.setOnClickListener { pedirCodigo { onEditar(vetado) } }
        holder.btnEliminar.setOnClickListener { pedirCodigo { onEliminar(vetado) } }
    }

    override fun getItemCount() = listaVetados.size
}