package ai.heypico.uiautomator.agent.server

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import ai.heypico.uiautomator.agent.automation.UIAutomationController
import timber.log.Timber

class UIAutomator2JsonRpcServer(port: Int, context: Context) : NanoHTTPD(port) {

    private val gson = Gson()
    private val automationController = UIAutomationController(context)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Timber.d("UIAutomator2 JSON-RPC request: $method $uri")

        return try {
            when {
                uri == "/info" && method == Method.GET -> handleInfo()
                uri == "/apps" && method == Method.GET -> handleListApps()
                uri == "/packages" && method == Method.GET -> handleListAllPackages()
                uri == "/jsonrpc" && method == Method.POST -> handleJsonRpc(session)
                else -> createErrorResponse("Unknown endpoint: $uri")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling JSON-RPC request")
            createErrorResponse(e.message ?: "Internal server error")
        }
    }

    private fun handleInfo(): Response {
        val info = mapOf(
            "version" to "1.0.0",
            "ready" to true,
            "message" to "UIAutomator2 JSON-RPC server ready",
            "platform" to mapOf(
                "name" to "Android",
                "version" to android.os.Build.VERSION.RELEASE
            ),
            "device" to mapOf(
                "model" to android.os.Build.MODEL,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "brand" to android.os.Build.BRAND
            )
        )
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(info)
        )
    }

    private fun handleListApps(): Response {
        val context = automationController.appContext
        val pm = context.packageManager
        
        // Get all apps with launch intent (launchable apps)
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(mainIntent, android.content.pm.PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.applicationInfo.packageName }
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                mapOf(
                    "packageName" to appInfo.packageName,
                    "appName" to pm.getApplicationLabel(appInfo).toString(),
                    "activityName" to (launchIntent?.component?.className ?: resolveInfo.activityInfo.name),
                    "enabled" to appInfo.enabled,
                    "isSystemApp" to ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                )
            }
            .sortedBy { it["appName"] as String }
        
        val response = mapOf(
            "apps" to apps,
            "count" to apps.size
        )
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(response)
        )
    }

    private fun handleListAllPackages(): Response {
        val context = automationController.appContext
        val pm = context.packageManager
        
        // Get ALL installed packages (system + user, no filter)
        val packages = pm.getInstalledPackages(android.content.pm.PackageManager.GET_META_DATA)
            .mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName)
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                
                mapOf(
                    "packageName" to packageInfo.packageName,
                    "appName" to pm.getApplicationLabel(appInfo).toString(),
                    "versionName" to (packageInfo.versionName ?: "unknown"),
                    "versionCode" to packageInfo.longVersionCode,
                    "enabled" to appInfo.enabled,
                    "isSystemApp" to isSystemApp,
                    "hasLaunchIntent" to (launchIntent != null),
                    "installTime" to packageInfo.firstInstallTime,
                    "updateTime" to packageInfo.lastUpdateTime
                )
            }
            .sortedBy { it["appName"] as String }
        
        val response = mapOf(
            "packages" to packages,
            "count" to packages.size,
            "userApps" to packages.count { !(it["isSystemApp"] as Boolean) },
            "systemApps" to packages.count { it["isSystemApp"] as Boolean }
        )
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(response)
        )
    }

    private fun handleJsonRpc(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val request = gson.fromJson(body, JsonObject::class.java)
        
        val method = request.get("method")?.asString
        val params = request.getAsJsonArray("params")
        val id = request.get("id")?.asInt ?: 1

        val result = when (method) {
            "click" -> handleClick(params)
            "set_text" -> handleSetText(params)
            "app_start" -> handleAppStart(params)
            "press" -> handlePress(params)
            "swipe" -> handleSwipe(params)
            "screenshot" -> handleScreenshot()
            "get_text" -> handleGetText(params)
            "exists" -> handleExists(params)
            "dump_ui" -> handleDumpUI()
            "adb_shell" -> handleAdbShell(params)
            "adb_tap" -> handleAdbTap(params)
            "adb_text" -> handleAdbText(params)
            "adb_swipe" -> handleAdbSwipe(params)
            else -> mapOf("error" to "Unknown method: $method")
        }

        val response = mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "result" to result
        )

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(response)
        )
    }

    private fun handleClick(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing parameters")
        }

        val selector = params.get(0).asJsonObject
        val text = selector.get("text")?.asString
        val resourceId = selector.get("resourceId")?.asString
        val className = selector.get("className")?.asString

        val success = when {
            text != null -> automationController.clickByText(text)
            resourceId != null -> automationController.clickByResourceId(resourceId)
            className != null -> automationController.clickByClassName(className)
            else -> false
        }

        return mapOf("success" to success)
    }

    private fun handleSetText(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() < 2) {
            return mapOf("success" to false, "error" to "Missing parameters")
        }

        val selector = params.get(0).asJsonObject
        val text = params.get(1).asString

        val resourceId = selector.get("resourceId")?.asString
        val className = selector.get("className")?.asString

        val success = when {
            resourceId != null -> automationController.setTextByResourceId(resourceId, text)
            className != null -> automationController.setTextByClassName(className, text)
            else -> false
        }

        return mapOf("success" to success)
    }

    private fun handleAppStart(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing package name")
        }

        val packageName = params.get(0).asString
        val success = automationController.startActivity(packageName, null)

        return mapOf("success" to success)
    }

    private fun handlePress(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing keycode")
        }

        val keycode = params.get(0).asInt
        val success = automationController.pressKeycode(keycode)

        return mapOf("success" to success)
    }

    private fun handleSwipe(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() < 4) {
            return mapOf("success" to false, "error" to "Missing swipe parameters")
        }

        val startX = params.get(0).asInt
        val startY = params.get(1).asInt
        val endX = params.get(2).asInt
        val endY = params.get(3).asInt
        val duration = if (params.size() > 4) params.get(4).asInt else 500

        val success = automationController.swipe(startX, startY, endX, endY, duration)

        return mapOf("success" to success)
    }

    private fun handleScreenshot(): Map<String, Any> {
        val screenshot = automationController.takeScreenshot()
        
        return if (screenshot != null) {
            mapOf("success" to true, "screenshot" to screenshot)
        } else {
            mapOf("success" to false, "error" to "Failed to take screenshot")
        }
    }

    private fun handleGetText(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing parameters")
        }

        val selector = params.get(0).asJsonObject
        val text = selector.get("text")?.asString
        val resourceId = selector.get("resourceId")?.asString

        val result = when {
            text != null -> automationController.getTextByText(text)
            resourceId != null -> automationController.getTextByResourceId(resourceId)
            else -> null
        }

        return if (result != null) {
            mapOf("success" to true, "text" to result)
        } else {
            mapOf("success" to false, "error" to "Element not found or no text")
        }
    }

    private fun handleExists(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing parameters")
        }

        val selector = params.get(0).asJsonObject
        val text = selector.get("text")?.asString
        val resourceId = selector.get("resourceId")?.asString

        val exists = when {
            text != null -> automationController.existsByText(text)
            resourceId != null -> automationController.existsByResourceId(resourceId)
            else -> false
        }

        return mapOf("success" to true, "exists" to exists)
    }

    private fun handleDumpUI(): Map<String, Any> {
        return try {
            val elements = automationController.dumpUIElements()
            mapOf("success" to true, "elements" to elements)
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Failed to dump UI"))
        }
    }

    private fun handleAdbShell(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing command parameter")
        }

        val command = params.get(0).asString
        return executeShellCommand(command)
    }

    private fun handleAdbTap(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() < 2) {
            return mapOf("success" to false, "error" to "Missing x,y parameters")
        }

        val x = params.get(0).asInt
        val y = params.get(1).asInt
        return executeShellCommand("input tap $x $y")
    }

    private fun handleAdbText(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() == 0) {
            return mapOf("success" to false, "error" to "Missing text parameter")
        }

        val text = params.get(0).asString
        val escapedText = text.replace(" ", "%s")
        return executeShellCommand("input text $escapedText")
    }

    private fun handleAdbSwipe(params: com.google.gson.JsonArray?): Map<String, Any> {
        if (params == null || params.size() < 5) {
            return mapOf("success" to false, "error" to "Missing swipe parameters")
        }

        val x1 = params.get(0).asInt
        val y1 = params.get(1).asInt
        val x2 = params.get(2).asInt
        val y2 = params.get(3).asInt
        val duration = params.get(4).asInt
        return executeShellCommand("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    private fun executeShellCommand(command: String): Map<String, Any> {
        return try {
            Timber.d("Executing shell command: $command")
            
            // Try method 1: Direct shell execution (limited)
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            Timber.d("Command exit code: $exitCode, output: $output, error: $error")
            
            // If direct shell fails, try via local ADB if available
            if (exitCode != 0 && isAdbAvailable()) {
                Timber.d("Retrying via local ADB...")
                return executeViaLocalAdb(command)
            }
            
            mapOf(
                "success" to (exitCode == 0),
                "exitCode" to exitCode,
                "output" to output,
                "error" to error,
                "method" to "shell"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to execute shell command: $command")
            mapOf("success" to false, "error" to (e.message ?: "Command execution failed"))
        }
    }

    private fun isAdbAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which adb")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun executeViaLocalAdb(command: String): Map<String, Any> {
        return try {
            // Execute via local ADB (if Termux ADB is installed)
            val adbCommand = "adb -s localhost:5555 shell $command"
            val process = Runtime.getRuntime().exec(adbCommand)
            val exitCode = process.waitFor()
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            mapOf(
                "success" to (exitCode == 0),
                "exitCode" to exitCode,
                "output" to output,
                "error" to error,
                "method" to "adb"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to execute via local ADB")
            mapOf("success" to false, "error" to (e.message ?: "ADB execution failed"))
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

    private fun createErrorResponse(message: String): Response {
        val errorResponse = mapOf(
            "jsonrpc" to "2.0",
            "error" to mapOf(
                "code" to -1,
                "message" to message
            ),
            "id" to null
        )
        
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            "application/json",
            gson.toJson(errorResponse)
        )
    }
}
