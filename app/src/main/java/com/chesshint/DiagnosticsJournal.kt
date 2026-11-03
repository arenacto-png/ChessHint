package com.chesshint

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small, app-local diagnostics store. The journal is intentionally independent
 * from UI and chess rules so logging cannot alter board or engine behavior.
 */
object DiagnosticsJournal {
    enum class Level { DEBUG, INFO, WARNING, ERROR }
    enum class Category { UI, POSITION, STOCKFISH, ANALYSIS, SYSTEM }

    data class Event(
        val id: String,
        val timestamp: String,
        val level: Level,
        val category: Category,
        val message: String,
        val requestId: String? = null,
        val details: String? = null
    )

    private const val PREFS = "chesshint_diagnostics"
    private const val EVENTS_KEY = "events_json"
    private const val MAX_EVENTS = 500
    private const val MAX_DETAILS = 12_000
    private val initialized = AtomicBoolean(false)
    private val operations = ConcurrentHashMap<String, Long>()
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            log(Level.INFO, Category.SYSTEM, "Diagnostics journal initialized")
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                log(
                    Level.ERROR,
                    Category.SYSTEM,
                    "Uncaught exception on thread ${thread.name}: ${throwable.message}",
                    details = throwable.stackTraceToString()
                )
                // Preserve Android's original crash behavior.
                previousHandler?.uncaughtException(thread, throwable)
            }.also { previousHandler = it }
        }
    }

    @Volatile private var previousHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun log(
        level: Level,
        category: Category,
        message: String,
        requestId: String? = null,
        details: String? = null
    ) {
        val context = appContext ?: return
        val event = Event(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now().toString(),
            level = level,
            category = category,
            message = message.take(2_000),
            requestId = requestId,
            details = details?.take(MAX_DETAILS)
        )
        synchronized(this) {
            val all = readEvents(context).toMutableList()
            all.add(event)
            saveEvents(context, all.takeLast(MAX_EVENTS))
        }
    }

    /** Starts a timed operation; returns its correlation/request ID. */
    fun beginOperation(category: Category, name: String, timeoutMs: Long = 60_000): String {
        val requestId = UUID.randomUUID().toString()
        operations[requestId] = System.currentTimeMillis()
        log(Level.INFO, category, "Operation started: $name (timeout=${timeoutMs}ms)", requestId)
        return requestId
    }

    fun endOperation(requestId: String, category: Category, name: String, result: String = "completed") {
        val started = operations.remove(requestId)
        val duration = started?.let { System.currentTimeMillis() - it }
        log(
            if (result == "failed") Level.ERROR else Level.INFO,
            category,
            "Operation $result: $name" + (duration?.let { " (${it}ms)" } ?: " (start not recorded)"),
            requestId
        )
    }

    /** Reports operations that have exceeded the supplied age, without losing correlation IDs. */
    fun detectStalledOperations(maxAgeMs: Long = 60_000): List<String> {
        val now = System.currentTimeMillis()
        val stalled = operations.entries.filter { now - it.value >= maxAgeMs }
        stalled.forEach { (id, started) ->
            log(
                Level.WARNING,
                Category.SYSTEM,
                "Operation appears stalled (${now - started}ms)",
                requestId = id
            )
        }
        return stalled.map { it.key }
    }

    fun events(
        level: Level? = null,
        category: Category? = null,
        query: String = ""
    ): List<Event> {
        val context = appContext ?: return emptyList()
        val normalized = query.trim().lowercase()
        return readEvents(context).asReversed().filter { event ->
            (level == null || event.level == level) &&
                (category == null || event.category == category) &&
                (normalized.isEmpty() ||
                    event.message.lowercase().contains(normalized) ||
                    event.details.orEmpty().lowercase().contains(normalized) ||
                    event.requestId.orEmpty().lowercase().contains(normalized))
        }
    }

    fun clear() {
        appContext?.let { context ->
            synchronized(this) { saveEvents(context, emptyList()) }
            log(Level.INFO, Category.SYSTEM, "Diagnostics journal cleared")
        }
    }

    fun exportJson(): String {
        val context = appContext
        val root = JSONObject()
            .put("app", "ChessHint")
            .put("version", appVersion(context))
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("generatedAt", Instant.now().toString())
            .put("stalledOperations", JSONArray(detectStalledOperations().toList()))
            .put("events", JSONArray().apply {
                events().forEach { event ->
                    put(JSONObject()
                        .put("id", event.id)
                        .put("timestamp", event.timestamp)
                        .put("level", event.level.name)
                        .put("category", event.category.name)
                        .put("message", event.message)
                        .put("requestId", event.requestId ?: JSONObject.NULL)
                        .put("details", event.details ?: JSONObject.NULL))
                }
            })
        return root.toString(2)
    }

    fun exportText(): String = buildString {
        appendLine("ChessHint diagnostics")
        appendLine("Version: ${appVersion(appContext)}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Generated: ${Instant.now()}")
        appendLine("Stalled operations: ${detectStalledOperations().size}")
        appendLine()
        events().forEach { event ->
            appendLine("[${event.timestamp}] ${event.level}/${event.category}: ${event.message}")
            event.requestId?.let { appendLine("  request_id=$it") }
            event.details?.let { appendLine(it.prependIndent("  ")) }
        }
    }

    fun writeExportFile(extension: String = "json"): File? {
        val context = appContext ?: return null
        val safeExtension = if (extension.lowercase() == "txt") "txt" else "json"
        return runCatching {
            File(context.cacheDir, "chesshint-diagnostics-${System.currentTimeMillis()}.$safeExtension")
                .apply { writeText(if (safeExtension == "txt") exportText() else exportJson()) }
        }.getOrNull()
    }

    private fun appVersion(context: Context?): String = runCatching {
        context?.packageManager?.getPackageInfo(context.packageName, 0)?.versionName ?: "unknown"
    }.getOrDefault("unknown")

    private fun readEvents(context: Context): List<Event> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EVENTS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Event(
                    id = item.optString("id"),
                    timestamp = item.optString("timestamp"),
                    level = runCatching { Level.valueOf(item.optString("level")) }.getOrDefault(Level.INFO),
                    category = runCatching { Category.valueOf(item.optString("category")) }.getOrDefault(Category.SYSTEM),
                    message = item.optString("message"),
                    requestId = item.optString("requestId").takeUnless { it == "null" || it.isBlank() },
                    details = item.optString("details").takeUnless { it == "null" || it.isBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveEvents(context: Context, events: List<Event>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(JSONObject()
                .put("id", event.id)
                .put("timestamp", event.timestamp)
                .put("level", event.level.name)
                .put("category", event.category.name)
                .put("message", event.message)
                .put("requestId", event.requestId ?: JSONObject.NULL)
                .put("details", event.details ?: JSONObject.NULL))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(EVENTS_KEY, array.toString()).apply()
    }
}
