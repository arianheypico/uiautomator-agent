package ai.heypico.uiautomator.agent.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UIAutomationController {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }
    
    private val context: Context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    // Cache for found elements
    private val elementCache = ConcurrentHashMap<String, UiObject2>()

    // WebDriver-style element finding
    fun findElement(using: String, value: String): String? {
        return try {
            val element = when (using) {
                "xpath" -> findByXPath(value)
                "id" -> device.findObject(By.res(value))
                "class name" -> device.findObject(By.clazz(value))
                "name" -> device.findObject(By.text(value))
                "partial link text" -> device.findObject(By.textContains(value))
                "link text" -> device.findObject(By.text(value))
                else -> null
            }
            
            if (element != null) {
                val elementId = UUID.randomUUID().toString()
                elementCache[elementId] = element
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
        return try {
            val element = elementCache[elementId]
            if (element != null) {
                element.click()
                Timber.d("Clicked element: $elementId")
                true
            } else {
                Timber.w("Element not found in cache: $elementId")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking element: $elementId")
            false
        }
    }

    fun sendKeysToElement(elementId: String, text: String): Boolean {
        return try {
            val element = elementCache[elementId]
            if (element != null) {
                element.clear()
                element.text = text
                Timber.d("Sent keys to element: $elementId, text: $text")
                true
            } else {
                Timber.w("Element not found in cache: $elementId")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending keys to element: $elementId")
            false
        }
    }

    // UIAutomator2-style direct methods
    fun clickByText(text: String): Boolean {
        return try {
            val element = device.findObject(By.text(text))
            if (element != null) {
                element.click()
                Timber.d("Clicked by text: $text")
                true
            } else {
                // Try with UiSelector as fallback
                val uiObject = device.findObject(UiSelector().text(text))
                if (uiObject.exists()) {
                    uiObject.click()
                    Timber.d("Clicked by text (fallback): $text")
                    true
                } else {
                    Timber.w("Element with text not found: $text")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking by text: $text")
            false
        }
    }

    fun clickByResourceId(resourceId: String): Boolean {
        return try {
            val element = device.findObject(By.res(resourceId))
            if (element != null) {
                element.click()
                Timber.d("Clicked by resource ID: $resourceId")
                true
            } else {
                Timber.w("Element with resource ID not found: $resourceId")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking by resource ID: $resourceId")
            false
        }
    }

    fun clickByClassName(className: String): Boolean {
        return try {
            val element = device.findObject(By.clazz(className))
            if (element != null) {
                element.click()
                Timber.d("Clicked by class name: $className")
                true
            } else {
                Timber.w("Element with class name not found: $className")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking by class name: $className")
            false
        }
    }

    fun setTextByResourceId(resourceId: String, text: String): Boolean {
        return try {
            val element = device.findObject(By.res(resourceId))
            if (element != null) {
                element.clear()
                element.text = text
                Timber.d("Set text by resource ID: $resourceId, text: $text")
                true
            } else {
                Timber.w("Element with resource ID not found: $resourceId")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting text by resource ID: $resourceId")
            false
        }
    }

    fun setTextByClassName(className: String, text: String): Boolean {
        return try {
            val element = device.findObject(By.clazz(className))
            if (element != null) {
                element.clear()
                element.text = text
                Timber.d("Set text by class name: $className, text: $text")
                true
            } else {
                Timber.w("Element with class name not found: $className")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting text by class name: $className")
            false
        }
    }

    fun startActivity(packageName: String, activityName: String?): Boolean {
        return try {
            val intent = if (activityName != null) {
                Intent().apply {
                    setClassName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (intent != null) {
                context.startActivity(intent)
                // Wait for app to start
                device.wait(Until.hasObject(By.pkg(packageName)), 5000)
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
            device.pressKeyCode(keycode)
            Timber.d("Pressed keycode: $keycode")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error pressing keycode: $keycode")
            false
        }
    }

    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int): Boolean {
        return try {
            device.swipe(startX, startY, endX, endY, duration / 10) // Convert to steps
            Timber.d("Swiped from ($startX,$startY) to ($endX,$endY) in ${duration}ms")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error swiping")
            false
        }
    }

    fun takeScreenshot(): String? {
        return try {
            val bitmap = device.takeScreenshot()
            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                Timber.d("Screenshot taken, size: ${outputStream.size()} bytes")
                base64
            } else {
                Timber.w("Failed to take screenshot")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error taking screenshot")
            null
        }
    }

    fun getTextByText(text: String): String? {
        return try {
            val element = device.findObject(By.text(text))
            element?.text
        } catch (e: Exception) {
            Timber.e(e, "Error getting text by text: $text")
            null
        }
    }

    fun getTextByResourceId(resourceId: String): String? {
        return try {
            val element = device.findObject(By.res(resourceId))
            element?.text
        } catch (e: Exception) {
            Timber.e(e, "Error getting text by resource ID: $resourceId")
            null
        }
    }

    fun existsByText(text: String): Boolean {
        return try {
            device.findObject(By.text(text)) != null
        } catch (e: Exception) {
            Timber.e(e, "Error checking existence by text: $text")
            false
        }
    }

    fun existsByResourceId(resourceId: String): Boolean {
        return try {
            device.findObject(By.res(resourceId)) != null
        } catch (e: Exception) {
            Timber.e(e, "Error checking existence by resource ID: $resourceId")
            false
        }
    }

    private fun findByXPath(xpath: String): UiObject2? {
        // Basic XPath support - can be extended
        return when {
            xpath.contains("@text=") -> {
                val text = xpath.substringAfter("@text='").substringBefore("'")
                device.findObject(By.text(text))
            }
            xpath.contains("@resource-id=") -> {
                val resourceId = xpath.substringAfter("@resource-id='").substringBefore("'")
                device.findObject(By.res(resourceId))
            }
            xpath.contains("@class=") -> {
                val className = xpath.substringAfter("@class='").substringBefore("'")
                device.findObject(By.clazz(className))
            }
            else -> null
        }
    }
}
