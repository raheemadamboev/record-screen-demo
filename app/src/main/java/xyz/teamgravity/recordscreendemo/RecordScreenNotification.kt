package xyz.teamgravity.recordscreendemo

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService

class RecordScreenNotification(
    private val application: Application
) {

    private val manager: NotificationManager? by lazy { application.getSystemService() }

    private companion object {
        const val CHANNEL_ID = "xyz.teamgravity.recordscreendemo.RecordScreenNotification"
        const val CHANNEL_NAME = "Raheem"
        const val NOTIFICATION_ID = 1
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        manager?.createNotificationChannel(channel)
    }

    private fun getActivity(): PendingIntent {
        val intent = Intent(application, MainActivity::class.java)
        return PendingIntent.getActivity(application, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    ///////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////

    fun notification(): Notification {
        createChannel()
        return NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(getActivity())
            .setContentTitle(application.getString(R.string.screen_recording))
            .setContentText(application.getString(R.string.recording_in_progress))
            .build()
    }

    fun id(): Int = NOTIFICATION_ID
}