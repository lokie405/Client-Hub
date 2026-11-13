package com.seryoga.myapplication.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.seryoga.myapplication.MainActivity

class TaskReminderReceiver : BroadcastReceiver() {

    @android.annotation.SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", 0L)
        val taskTitle = intent.getStringExtra("taskTitle") ?: "Завдання"
        val commentText = intent.getStringExtra("commentText") ?: ""
        val clientName = intent.getStringExtra("clientName") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "task_reminders_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Нагадування завдань",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сповіщення про нагадування для завдань клієнтів"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = listOfNotNull(
            clientName.ifBlank { null },
            commentText.ifBlank { null }
        ).joinToString(" • ")

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🔔 $taskTitle")
            .setContentText(contentText.ifBlank { "Нагадування про завдання" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText.ifBlank { "Нагадування про завдання" }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(taskId.toInt(), notification)
    }

    companion object {
        fun scheduleReminder(context: Context, task: TaskEntity, clientName: String = "") {
            val reminderTs = task.reminderTimestamp ?: return
            if (reminderTs <= System.currentTimeMillis() || task.isCompleted) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                putExtra("taskId", task.id)
                putExtra("taskTitle", task.title)
                putExtra("commentText", task.commentText)
                putExtra("clientName", clientName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTs, pendingIntent)
                }
            } catch (_: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminderTs, pendingIntent)
            }
        }

        fun cancelReminder(context: Context, taskId: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TaskReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
