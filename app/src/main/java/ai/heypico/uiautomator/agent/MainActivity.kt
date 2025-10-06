package ai.heypico.uiautomator.agent

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.heypico.uiautomator.agent.service.UIAutomatorAgentService
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        initViews()
        setupClickListeners()
        updateStatus()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
    }

    private fun setupClickListeners() {
        startButton.setOnClickListener {
            startService()
        }

        stopButton.setOnClickListener {
            stopService()
        }
    }

    private fun startService() {
        val intent = Intent(this, UIAutomatorAgentService::class.java)
        startForegroundService(intent)
        updateStatus()
        Timber.d("UI Automator Agent Service started manually")
    }

    private fun stopService() {
        val intent = Intent(this, UIAutomatorAgentService::class.java)
        stopService(intent)
        updateStatus()
        Timber.d("UI Automator Agent Service stopped manually")
    }

    private fun updateStatus() {
        val isServiceRunning = UIAutomatorAgentService.isServiceRunning()
        
        statusText.text = if (isServiceRunning) {
            "🟢 Service Running\n\nHTTP Server listening on:\n• Port 6790 (Appium WebDriver)\n• Port 7912 (UIAutomator2 JSON-RPC)\n\nReady for Zrok tunnel connection"
        } else {
            "🔴 Service Stopped\n\nUI Automator Agent is not running.\nStart the service to enable remote control."
        }

        startButton.isEnabled = !isServiceRunning
        stopButton.isEnabled = isServiceRunning
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
