package ai.heypico.uiautomator.agent.automation

import android.content.Context
import android.content.Intent
import android.util.Base64
import ai.heypico.uiautomator.agent.service.UIAutomatorAccessibilityService
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * UI Automation Controller using Accessibility Service
 * This approach works in production apps without requiring test instrumentation
 */
class UIAutomationController(context: Context) {

    private val appContext: Context = context
    private val elementCache = ConcurrentHashMap<String, String>()

    // Get accessibility service instance
    private fun getAccessibilityService(): UIAutomatorAccessibilityService? {
        return UIAutomatorAccessibilityService.getInstance()
    }

    // WebDriver-style element finding
    fun findElement(using: String, value: String): String? {
        val service = getAccessibilityService()
        if (service == null) {
            Timber.w("Accessibility service not available")
            return null
        }

        return try {
            val exists = when (using) {
                "id" -> service.existsByResourceId(value)
                "name", "link text", "partial link text" -> service.existsByText(value)
                else -> false
            }
            
            if (exists) {
                val elementId = UUID.randomUUID().toString()
                elementCache[elementId] = "$using:$value"
                Timber.d("Found element: $using=$value, ID=$elementId")
                elementId
            } else {
                Timber.w("Element not found: $using=$value")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error finding element: $using=$value")
            null
        }
    }

    fun clickElement(elementId: String): Boolean {
        val service = getAccessibilityService() ?: return false
        val elementInfo = elementCache[elementId] ?: return false
        
        return try {
            val (using, value) = elementInfo.split(":", limit = 2)
            when (using) {
                "id" -> service.clickByResourceId(value)
                else -> service.clickByText(value)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking element: $elementId")
            false
        }
    }

    fun sendKeysToElement(elementId: String, text: String): Boolean {
        val service = getAccessibilityService() ?: return false
        val elementInfo = elementCache[elementId] ?: return false
        
        return try {
            val (using, value) = elementInfo.split(":", limit = 2)
            when (using) {
                "id" -> service.setTextByResourceId(value, text)
                else -> false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending keys to element: $elementId")
            false
        }
    }

    // Direct methods using accessibility service
    fun clickByText(text: String): Boolean {
        val service = getAccessibilityService()
        return service?.clickByText(text) ?: false
    }

    fun clickByResourceId(resourceId: String): Boolean {
        val service = getAccessibilityService()
        return service?.clickByResourceId(resourceId) ?: false
    }

    fun clickByClassName(className: String): Boolean {
        // Accessibility service doesn't support class name directly
        Timber.w("clickByClassName not supported via accessibility service")
        return false
    }

    fun setTextByResourceId(resourceId: String, text: String): Boolean {
        val service = getAccessibilityService()
        return service?.setTextByResourceId(resourceId, text) ?: false
    }

    fun setTextByClassName(className: String, text: String): Boolean {
        // Accessibility service doesn't support class name directly
        Timber.w("setTextByClassName not supported via accessibility service")
        return false
    }

    fun startActivity(packageName: String, activityName: String?): Boolean {
        return try {
            val intent = if (activityName != null) {
                Intent().apply {
                    setClassName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (intent != null) {
                appContext.startActivity(intent)
                Timber.d("Started activity: $packageName/$activityName")
                true
            } else {
                Timber.w("Could not create intent for: $packageName/$activityName")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting activity: $packageName/$activityName")
            false
        }
    }

    fun pressKeycode(keycode: Int): Boolean {
        return try {
            // Use shell command for key press
            val process = Runtime.getRuntime().exec("input keyevent $keycode")
            process.waitFor()
            Timber.d("Pressed keycode: $keycode")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error pressing keycode: $keycode")
            false
        }
    }

    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int): Boolean {
        val service = getAccessibilityService()
        return service?.performGesture(
            startX.toFloat(), 
            startY.toFloat(), 
            endX.toFloat(), 
            endY.toFloat(), 
            duration.toLong()
        ) ?: false
    }

    fun takeScreenshot(): String? {
        return try {
            // Use shell command for screenshot
            val screenshotFile = File(appContext.cacheDir, "screenshot_${System.currentTimeMillis()}.png")
            val process = Runtime.getRuntime().exec("screencap -p ${screenshotFile.absolutePath}")
            process.waitFor()
            
            if (screenshotFile.exists()) {
                val bytes = screenshotFile.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                screenshotFile.delete()
                Timber.d("Screenshot taken, size: ${bytes.size} bytes")
                base64
            } else {
                Timber.w("Screenshot file not created")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error taking screenshot")
            null
        }
    }

    fun getTextByText(text: String): String? {
        val service = getAccessibilityService()
        return service?.getTextByResourceId(text) // Fallback
    }

    fun getTextByResourceId(resourceId: String): String? {
        val service = getAccessibilityService()
        return service?.getTextByResourceId(resourceId)
    }

    fun existsByText(text: String): Boolean {
        val service = getAccessibilityService()
        return service?.existsByText(text) ?: false
    }

    fun existsByResourceId(resourceId: String): Boolean {
        val service = getAccessibilityService()
        return service?.existsByResourceId(resourceId) ?: false
    }

    private fun findByXPath(xpath: String): String? {
        // Basic XPath support
        val service = getAccessibilityService() ?: return null
        
        return when {
            xpath.contains("@text=") -> {
                val text = xpath.substringAfter("@text='").substringBefore("'")
                if (service.existsByText(text)) text else null
            }
            xpath.contains("@resource-id=") -> {
                val resourceId = xpath.substringAfter("@resource-id='").substringBefore("'")
                if (service.existsByResourceId(resourceId)) resourceId else null
            }
            else -> null
        }
    }
}
