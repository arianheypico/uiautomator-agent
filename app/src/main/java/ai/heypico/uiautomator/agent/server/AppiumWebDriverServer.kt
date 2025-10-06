package ai.heypico.uiautomator.agent.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import ai.heypico.uiautomator.agent.automation.UIAutomationController
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AppiumWebDriverServer(port: Int) : NanoHTTPD(port) {

    private val gson = Gson()
    private val automationController = UIAutomationController()
    private val sessions = ConcurrentHashMap<String, WebDriverSession>()

    data class WebDriverSession(
        val sessionId: String,
        val capabilities: JsonObject,
        val createdAt: Long = System.currentTimeMillis()
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Timber.d("Appium WebDriver request: $method $uri")

        return try {
            when {
                uri == "/wd/hub/status" && method == Method.GET -> handleStatus()
                uri == "/wd/hub/session" && method == Method.POST -> handleCreateSession(session)
                uri.startsWith("/wd/hub/session/") && method == Method.DELETE -> handleDeleteSession(uri)
                uri.contains("/element") && method == Method.POST -> handleFindElement(session, uri)
                uri.contains("/click") && method == Method.POST -> handleClick(session, uri)
                uri.contains("/value") && method == Method.POST -> handleSendKeys(session, uri)
                uri.contains("/start_activity") && method == Method.POST -> handleStartActivity(session, uri)
                uri.contains("/press_keycode") && method == Method.POST -> handlePressKeycode(session, uri)
                else -> createErrorResponse(404, "unknown command", "Unknown endpoint: $uri")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling WebDriver request")
            createErrorResponse(500, "unknown error", e.message ?: "Internal server error")
        }
    }

    private fun handleStatus(): Response {
        val status = mapOf(
            "value" to mapOf(
                "build" to mapOf(
                    "version" to "1.0.0"
                ),
                "os" to mapOf(
                    "name" to "Android"
                ),
                "ready" to true,
                "message" to "UI Automator Agent ready to accept commands"
            )
        )
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(status)
        )
    }

    private fun handleCreateSession(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val sessionId = UUID.randomUUID().toString()
        
        val requestData = gson.fromJson(body, JsonObject::class.java)
        val capabilities = requestData.getAsJsonObject("capabilities")
            ?.getAsJsonObject("alwaysMatch") ?: JsonObject()

        val webDriverSession = WebDriverSession(sessionId, capabilities)
        sessions[sessionId] = webDriverSession

        val response = mapOf(
            "value" to mapOf(
                "sessionId" to sessionId,
                "capabilities" to capabilities
            )
        )

        Timber.i("Created WebDriver session: $sessionId")
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(response)
        )
    }

    private fun handleDeleteSession(uri: String): Response {
        val sessionId = extractSessionId(uri)
        
        if (sessionId != null && sessions.containsKey(sessionId)) {
            sessions.remove(sessionId)
            Timber.i("Deleted WebDriver session: $sessionId")
            
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("value" to null))
            )
        }
        
        return createErrorResponse(404, "invalid session id", "Session not found: $sessionId")
    }

    private fun handleFindElement(session: IHTTPSession, uri: String): Response {
        val sessionId = extractSessionId(uri)
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return createErrorResponse(404, "invalid session id", "Session not found")
        }

        val body = getRequestBody(session)
        val requestData = gson.fromJson(body, JsonObject::class.java)
        val using = requestData.get("using")?.asString
        val value = requestData.get("value")?.asString

        if (using == null || value == null) {
            return createErrorResponse(400, "invalid argument", "Missing 'using' or 'value'")
        }

        // Find element using automation controller
        val elementId = automationController.findElement(using, value)
        
        if (elementId != null) {
            val response = mapOf(
                "value" to mapOf(
                    "ELEMENT" to elementId,
                    "element-6066-11e4-a52e-4f735466cecf" to elementId
                )
            )
            
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(response)
            )
        } else {
            return createErrorResponse(404, "no such element", "Element not found: $using=$value")
        }
    }

    private fun handleClick(session: IHTTPSession, uri: String): Response {
        val sessionId = extractSessionId(uri)
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return createErrorResponse(404, "invalid session id", "Session not found")
        }

        val elementId = extractElementId(uri)
        if (elementId == null) {
            return createErrorResponse(400, "invalid argument", "Element ID not found in URI")
        }

        val success = automationController.clickElement(elementId)
        
        if (success) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("value" to null))
            )
        } else {
            return createErrorResponse(500, "unknown error", "Failed to click element")
        }
    }

    private fun handleSendKeys(session: IHTTPSession, uri: String): Response {
        val sessionId = extractSessionId(uri)
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return createErrorResponse(404, "invalid session id", "Session not found")
        }

        val elementId = extractElementId(uri)
        if (elementId == null) {
            return createErrorResponse(400, "invalid argument", "Element ID not found in URI")
        }

        val body = getRequestBody(session)
        val requestData = gson.fromJson(body, JsonObject::class.java)
        val valueArray = requestData.getAsJsonArray("value")
        
        if (valueArray == null || valueArray.size() == 0) {
            return createErrorResponse(400, "invalid argument", "Missing 'value' array")
        }

        val text = valueArray.joinToString("") { it.asString }
        val success = automationController.sendKeysToElement(elementId, text)
        
        if (success) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("value" to null))
            )
        } else {
            return createErrorResponse(500, "unknown error", "Failed to send keys to element")
        }
    }

    private fun handleStartActivity(session: IHTTPSession, uri: String): Response {
        val sessionId = extractSessionId(uri)
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return createErrorResponse(404, "invalid session id", "Session not found")
        }

        val body = getRequestBody(session)
        val requestData = gson.fromJson(body, JsonObject::class.java)
        val appPackage = requestData.get("appPackage")?.asString
        val appActivity = requestData.get("appActivity")?.asString

        if (appPackage == null) {
            return createErrorResponse(400, "invalid argument", "Missing 'appPackage'")
        }

        val success = automationController.startActivity(appPackage, appActivity)
        
        if (success) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("value" to null))
            )
        } else {
            return createErrorResponse(500, "unknown error", "Failed to start activity")
        }
    }

    private fun handlePressKeycode(session: IHTTPSession, uri: String): Response {
        val sessionId = extractSessionId(uri)
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return createErrorResponse(404, "invalid session id", "Session not found")
        }

        val body = getRequestBody(session)
        val requestData = gson.fromJson(body, JsonObject::class.java)
        val keycode = requestData.get("keycode")?.asInt ?: 3 // Default HOME key

        val success = automationController.pressKeycode(keycode)
        
        if (success) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("value" to null))
            )
        } else {
            return createErrorResponse(500, "unknown error", "Failed to press keycode")
        }
    }

    private fun getRequestBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer)
            return String(buffer)
        }
        return "{}"
    }

    private fun extractSessionId(uri: String): String? {
        val pattern = Regex("/wd/hub/session/([^/]+)")
        return pattern.find(uri)?.groupValues?.get(1)
    }

    private fun extractElementId(uri: String): String? {
        val pattern = Regex("/element/([^/]+)")
        return pattern.find(uri)?.groupValues?.get(1)
    }

    private fun createErrorResponse(statusCode: Int, error: String, message: String): Response {
        val errorResponse = mapOf(
            "value" to mapOf(
                "error" to error,
                "message" to message,
                "stacktrace" to ""
            )
        )
        
        val status = when (statusCode) {
            400 -> Response.Status.BAD_REQUEST
            404 -> Response.Status.NOT_FOUND
            500 -> Response.Status.INTERNAL_ERROR
            else -> Response.Status.INTERNAL_ERROR
        }
        
        return newFixedLengthResponse(
            status,
            "application/json",
            gson.toJson(errorResponse)
        )
    }
}
