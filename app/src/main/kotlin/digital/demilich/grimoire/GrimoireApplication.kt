package digital.demilich.grimoire

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import digital.demilich.grimoire.overlay.OverlayService

class GrimoireApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // IMPORTANCE_LOW: the foreground-service notification that keeps the
        // overlay alive is required to exist, but shouldn't ever make noise or
        // pop up as heads-up - it's just the "this is still running, tap to
        // dismiss" affordance Play review expects for a persistent overlay.
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            OverlayService.CHANNEL_ID,
            "Floating tools",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the floating tool bubble available while it's open."
        }
        manager.createNotificationChannel(channel)
    }
}
