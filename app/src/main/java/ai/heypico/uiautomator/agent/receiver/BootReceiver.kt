package ai.heypico.uiautomator.agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.heypico.uiautomator.agent.service.UIAutomatorAgentService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        Timber.d("BootReceiver received action: $action")
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_QUICKBOOT_POWERON,
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                
                // Start UI Automator Agent Service
                startUIAutomatorService(context)
            }
        }
    }

    private fun startUIAutomatorService(context: Context) {
        try {
            val serviceIntent = Intent(context, UIAutomatorAgentService::class.java)
            serviceIntent.putExtra("started_by", "boot_receiver")
            
            // Start as foreground service
            context.startForegroundService(serviceIntent)
            
            Timber.i("UI Automator Agent Service started automatically on boot")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start UI Automator Agent Service on boot")
        }
    }
}
