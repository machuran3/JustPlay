package com.example.minicine01

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmUtils {

    /**
     * Programa una alarma de alta precisión con AlarmManager del sistema operativo.
     * Esta alarma se disparará exactamente cuando venza el tiempo, incluso si la app
     * está totalmente cerrada o el teléfono en reposo profundo (Doze mode).
     */
    fun programarAlarmaSala(context: Context, salaId: Int, salaNombre: String, cliente: String?, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, TimerNotificationReceiver::class.java).apply {
                action = "com.example.minicine01.ACTION_SALA_TIMER_EXPIRED_$salaId"
                putExtra("EXTRA_SALA_ID", salaId)
                putExtra("EXTRA_SALA_NOMBRE", salaNombre)
                putExtra("EXTRA_CLIENTE", cliente ?: "")
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(context, salaId, intent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cancela la alarma del sistema operativo si la sala es liberada o se cancela el tiempo.
     */
    fun cancelarAlarmaSala(context: Context, salaId: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TimerNotificationReceiver::class.java).apply {
                action = "com.example.minicine01.ACTION_SALA_TIMER_EXPIRED_$salaId"
            }
            val flags = PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(context, salaId, intent, flags)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
