package ai.heypico.uiautomator.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

class UIAutomatorAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: UIAutomatorAccessibilityService? = null
        
        fun getInstance(): UIAutomatorAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("UIAutomatorAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle events for our use case
        // This service is primarily for performing actions
    }

    override fun onInterrupt() {
        Timber.w("UIAutomatorAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.i("UIAutomatorAccessibilityService destroyed")
    }

    // Public methods for UI automation
    fun clickByText(text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val targetNode = findNodeByText(rootNode, text)
            
            if (targetNode != null) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                targetNode.recycle()
                Timber.d("Clicked by text: $text")
                true
            } else {
                Timber.w("Node with text not found: $text")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking by text: $text")
            false
        }
    }

    fun clickByResourceId(resourceId: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val targetNode = findNodeByResourceId(rootNode, resourceId)
            
            if (targetNode != null) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                targetNode.recycle()
                Timber.d("Clicked by resource ID: $resourceId")
                true
            } else {
                Timber.w("Node with resource ID not found: $resourceId")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clicking by resource ID: $resourceId")
            false
        }
    }

    fun setTextByResourceId(resourceId: String, text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val targetNode = findNodeByResourceId(rootNode, resourceId)
            
            if (targetNode != null && targetNode.isEditable) {
                // Clear existing text
                targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,
                    android.os.Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 
                            targetNode.text?.length ?: 0)
                    })
                
                // Set new text
                targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    })
                
                targetNode.recycle()
                Timber.d("Set text by resource ID: $resourceId, text: $text")
                true
            } else {
                Timber.w("Editable node with resource ID not found: $resourceId")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting text by resource ID: $resourceId")
            false
        }
    }

    fun performGesture(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        return try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)
            
            val gesture = gestureBuilder.build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Timber.d("Gesture completed: ($startX,$startY) to ($endX,$endY)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Timber.w("Gesture cancelled: ($startX,$startY) to ($endX,$endY)")
                }
            }, null)
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Error performing gesture")
            false
        }
    }

    fun getTextByResourceId(resourceId: String): String? {
        return try {
            val rootNode = rootInActiveWindow ?: return null
            val targetNode = findNodeByResourceId(rootNode, resourceId)
            
            val text = targetNode?.text?.toString()
            targetNode?.recycle()
            text
        } catch (e: Exception) {
            Timber.e(e, "Error getting text by resource ID: $resourceId")
            null
        }
    }

    fun existsByText(text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val targetNode = findNodeByText(rootNode, text)
            val exists = targetNode != null
            targetNode?.recycle()
            exists
        } catch (e: Exception) {
            Timber.e(e, "Error checking existence by text: $text")
            false
        }
    }

    fun existsByResourceId(resourceId: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val targetNode = findNodeByResourceId(rootNode, resourceId)
            val exists = targetNode != null
            targetNode?.recycle()
            exists
        } catch (e: Exception) {
            Timber.e(e, "Error checking existence by resource ID: $resourceId")
            false
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Check current node
        if (root.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return root
        }
        
        // Check content description
        if (root.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            return root
        }
        
        // Search children recursively
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        
        return null
    }

    private fun findNodeByResourceId(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        // Check current node
        if (root.viewIdResourceName == resourceId) {
            return root
        }
        
        // Search children recursively
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByResourceId(child, resourceId)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        
        return null
    }

    fun dumpUIElements(): List<Map<String, Any>> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<Map<String, Any>>()
        collectElements(root, elements, 0)
        root.recycle()
        return elements
    }

    private fun collectElements(node: AccessibilityNodeInfo, elements: MutableList<Map<String, Any>>, depth: Int) {
        if (depth > 10) return // Limit depth to avoid too much data
        
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        val element = mutableMapOf<String, Any>(
            "className" to (node.className?.toString() ?: ""),
            "text" to (node.text?.toString() ?: ""),
            "contentDescription" to (node.contentDescription?.toString() ?: ""),
            "resourceId" to (node.viewIdResourceName ?: ""),
            "clickable" to node.isClickable,
            "enabled" to node.isEnabled,
            "focused" to node.isFocused,
            "bounds" to bounds.toShortString()
        )
        
        if (element["text"] != "" || element["contentDescription"] != "" || element["resourceId"] != "") {
            elements.add(element)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectElements(child, elements, depth + 1)
            child.recycle()
        }
    }
}
