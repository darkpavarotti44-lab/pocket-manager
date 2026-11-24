package com.pocketmanager.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import java.time.LocalDate

internal fun scheduleDebtReminder(context:Context){
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("debt_due_reminder",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<DebtReminder>(1,TimeUnit.DAYS).build())
}
internal fun cancelDebtReminder(context:Context){
    WorkManager.getInstance(context).cancelUniqueWork("debt_due_reminder")
    context.getSystemService(NotificationManager::class.java).cancel(420)
}
class DebtReminder(context:Context,params:WorkerParameters):Worker(context,params){
    override fun doWork():Result{
        val ctx=applicationContext
        if(!ctx.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean("reminders",true))return Result.success()
        return try{
            val upcoming=Store(ctx).use{db->db.obligations().any{it.remaining>0&&it.due.isNotBlank()&&it.due<=LocalDate.now().plusDays(3).toString()}}
            val nm=ctx.getSystemService(NotificationManager::class.java)
            if(!upcoming){nm.cancel(420);return Result.success()}
            if(!nm.areNotificationsEnabled())return Result.success()
            nm.createNotificationChannel(NotificationChannel("due","Échéances financières",NotificationManager.IMPORTANCE_DEFAULT))
            val open=PendingIntent.getActivity(ctx,0,Intent(ctx,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(420,Notification.Builder(ctx,"due").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Pocket Manager").setContentText("Une échéance financière approche ou est dépassée. Consulte l’application.").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
            Result.success()
        }catch(e:Exception){Result.retry()}
    }
}
