package ai.heypico.uiautomator.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.heypico.uiautomator.agent.MainActivity
import ai.heypico.uiautomator.agent.R
import ai.heypico.uiautomator.agent.server.AppiumWebDriverServer
import ai.heypico.uiautomator.agent.server.UIAutomator2JsonRpcServer
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class UIAutomatorAgentService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "UIAutomatorAgentChannel"
        private const val CHANNEL_NAME = "UI Automator Agent"
        
        private val isRunning = AtomicBoolean(false)
        
        fun isServiceRunning(): Boolean = isRunning.get()
    }

    private var appiumServer: AppiumWebDriverServer? = null
    private var uiAutomator2Server: UIAutomator2JsonRpcServer? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("UIAutomatorAgentService onCreate")
        
        createNotificationChannel()
        isRunning.set(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("UIAutomatorAgentService onStartCommand")
        
        val startedBy = intent?.getStringExtra("started_by") ?: "manual"
        Timber.i("Service started by: $startedBy")

        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Start HTTP servers
        startServers()

        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("UIAutomatorAgentService onDestroy")
        
        stopServers()
        isRunning.set(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "UI Automator Agent background service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UI Automator Agent")
            .setContentText("HTTP servers running on ports 6790 & 7912")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startServers() {
        try {
            // Start Appium WebDriver server on port 6790
            appiumServer = AppiumWebDriverServer(6790)
            appiumServer?.start()
            Timber.i("Appium WebDriver server started on port 6790")

            // Start UIAutomator2 JSON-RPC server on port 7912
            uiAutomator2Server = UIAutomator2JsonRpcServer(7912)
            uiAutomator2Server?.start()
            Timber.i("UIAutomator2 JSON-RPC server started on port 7912")

        } catch (e: Exception) {
            Timber.e(e, "Failed to start HTTP servers")
        }
    }

    private fun stopServers() {
        try {
            appiumServer?.stop()
            appiumServer = null
            Timber.i("Appium WebDriver server stopped")

            uiAutomator2Server?.stop()
            uiAutomator2Server = null
            Timber.i("UIAutomator2 JSON-RPC server stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping HTTP servers")
        }
    }
}
