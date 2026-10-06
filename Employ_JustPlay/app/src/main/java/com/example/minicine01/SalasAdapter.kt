package com.example.minicine01

import android.content.Context
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SalasAdapter(
    private val listaSalas: List<Sala>,
    private val onClickSala: (Sala) -> Unit
) : RecyclerView.Adapter<SalasAdapter.SalaViewHolder>() {

    class SalaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombreSala: TextView = view.findViewById(R.id.tvNombreSala)
        val tvEstadoSala: TextView = view.findViewById(R.id.tvEstadoSala)
        val tvSalaCliente: TextView = view.findViewById(R.id.tvSalaCliente) // Nuevo
        val tvCountdown: TextView = view.findViewById(R.id.tvCountdown)
        val tvHoraFin: TextView = view.findViewById(R.id.tvHoraFin)
        var timer: CountDownTimer? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sala, parent, false)
        return SalaViewHolder(view)
    }

    override fun onBindViewHolder(holder: SalaViewHolder, position: Int) {
        val salaActual = listaSalas[position]

        holder.tvNombreSala.text = salaActual.nombre
        holder.timer?.cancel()
        holder.tvCountdown.clearAnimation()

        // Mostrar nombre del cliente si existe
        if (!salaActual.cliente.isNullOrEmpty()) {
            holder.tvSalaCliente.visibility = View.VISIBLE
            holder.tvSalaCliente.text = "👤 ${salaActual.cliente}"
        } else {
            holder.tvSalaCliente.visibility = View.GONE
        }

        when (salaActual.estado) {
            EstadoSala.OCUPADA -> {
                holder.tvEstadoSala.text = "Ocupada"
                holder.tvEstadoSala.setTextColor(Color.parseColor("#FF5252"))
                holder.tvHoraFin.visibility = View.VISIBLE
                holder.tvHoraFin.text = "Termina: ${salaActual.horaFinProgramada ?: "--:--"}"

                // Lógica de Reloj Anti-Reseteo
                if (salaActual.endTimeMillis != null) {
                    val tiempoFaltanteReal = salaActual.endTimeMillis!! - System.currentTimeMillis()

                    if (tiempoFaltanteReal > 0L) {
                        holder.tvCountdown.visibility = View.VISIBLE
                        holder.tvCountdown.setTextColor(Color.WHITE)

                        holder.timer = object : CountDownTimer(tiempoFaltanteReal, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                val horas = (millisUntilFinished / (1000 * 60 * 60)) % 24
                                val minutos = (millisUntilFinished / (1000 * 60)) % 60
                                val segundos = (millisUntilFinished / 1000) % 60
                                holder.tvCountdown.text = String.format("%02d:%02d:%02d", horas, minutos, segundos)
                            }
                            override fun onFinish() {
                                activarAlarma(holder)
                            }
                        }.start()
                    } else {
                        activarAlarma(holder)
                    }
                } else {
                    holder.tvCountdown.visibility = View.GONE
                }
            }
            EstadoSala.RESERVADA -> {
                holder.tvEstadoSala.text = "Reservada: ${salaActual.horaReserva}"
                holder.tvEstadoSala.setTextColor(Color.parseColor("#FFC107"))
                holder.tvCountdown.visibility = View.GONE
                holder.tvHoraFin.visibility = View.GONE
            }
            EstadoSala.DISPONIBLE -> {
                holder.tvEstadoSala.text = "Disponible"
                holder.tvEstadoSala.setTextColor(Color.parseColor("#00E676"))
                holder.tvCountdown.visibility = View.GONE
                holder.tvHoraFin.visibility = View.GONE
            }
        }
        holder.itemView.setOnClickListener { onClickSala(salaActual) }
    }

    private fun activarAlarma(holder: SalaViewHolder) {
        holder.tvCountdown.visibility = View.VISIBLE
        holder.tvCountdown.text = "00:00:00"
        holder.tvCountdown.setTextColor(Color.parseColor("#FF5252"))

        val anim = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 500
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        holder.tvCountdown.startAnimation(anim)

        try {
            val context = holder.itemView.context
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(1000)
            }
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context, notification)
            r.play()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onViewRecycled(holder: SalaViewHolder) {
        super.onViewRecycled(holder)
        holder.timer?.cancel()
        holder.timer = null
        holder.tvCountdown.clearAnimation()
    }

    override fun getItemCount() = listaSalas.size
}