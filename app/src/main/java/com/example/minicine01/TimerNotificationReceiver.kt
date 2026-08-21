package com.example.minicine01

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class TimerNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val salaId = intent.getIntExtra("EXTRA_SALA_ID", 0)
        val salaNombre = intent.getStringExtra("EXTRA_SALA_NOMBRE") ?: "Sala"
        val cliente = intent.getStringExtra("EXTRA_CLIENTE") ?: ""

        val channelId = "justplay_timer_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Asegurar que el canal existe con alta prioridad y sonido de alarma
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Alarmas de Tiempo Just Play",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando finaliza el tiempo de una sala o auto"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intento para abrir la app al tocar la notificación
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            salaId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val clienteInfo = if (cliente.isNotEmpty()) " ($cliente)" else ""
        val mensajeTexto = "¡Se acabó el tiempo en $salaNombre$clienteInfo! Ya es hora de liberar o cobrar."

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ ¡Tiempo Finalizado: $salaNombre!")
            .setContentText("Ya es hora de $salaNombre$clienteInfo.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensajeTexto))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(salaId, builder.build())

        // Vibración y sonido directo
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                vibrator.vibrate(800)
            }
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context, alarmSound)
            r.play()
        } catch (_: Exception) {}
    }
}
