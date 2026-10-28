package com.retro.gba.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val TAG = "RetroGBA_Logger"
    private const val MAX_LOGS = 100
    private const val CRASH_FILE_NAME = "latest_crash_log.txt"

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
        val stackTrace: String? = null
    )

    private val logList = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTraceStr = sw.toString()

            log("CRASH", "Uncaught Exception on thread '${thread.name}': ${throwable.localizedMessage}", "ERROR", throwable)

            try {
                val crashFile = File(appContext.filesDir, CRASH_FILE_NAME)
                val report = buildDiagnosticReport(appContext, stackTraceStr)
                crashFile.writeText(report, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao salvar crash log em arquivo", e)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }

        log("AppLogger", "RetroGBA Logger e UncaughtExceptionHandler inicializados")
    }

    @Synchronized
    fun log(tag: String, message: String, level: String = "INFO", throwable: Throwable? = null) {
        val time = dateFormat.format(Date())
        var stackTraceStr: String? = null

        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            stackTraceStr = sw.toString()
        }

        val entry = LogEntry(time, level, tag, message, stackTraceStr)

        if (logList.size >= MAX_LOGS) {
            logList.removeAt(0)
        }
        logList.add(entry)

        when (level) {
            "ERROR" -> Log.e(tag, message, throwable)
            "WARN" -> Log.w(tag, message, throwable)
            else -> Log.i(tag, message, throwable)
        }
    }

    @Synchronized
    fun getLogs(): List<LogEntry> {
        return logList.toList()
    }

    @Synchronized
    fun clearLogs(context: Context) {
        logList.clear()
        try {
            val crashFile = File(context.filesDir, CRASH_FILE_NAME)
            if (crashFile.exists()) {
                crashFile.delete()
            }
        } catch (_: Exception) {}
        log("AppLogger", "Logs limpos com sucesso.")
    }

    fun buildDiagnosticReport(context: Context, extraCrashTrace: String? = null): String {
        val sb = StringBuilder()
        sb.append("==========================================\n")
        sb.append("🎮 RetroGBA - Relatório de Diagnóstico\n")
        sb.append("==========================================\n")
        sb.append("Data/Hora: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("Android API: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Arquitetura: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n")

        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.append("Versão App: ").append(pInfo.versionName).append("\n")
        } catch (_: Exception) {}

        sb.append("\n------------------------------------------\n")
        sb.append("📋 ÚLTIMO CRASH (SE HOUVER):\n")
        sb.append("------------------------------------------\n")

        if (extraCrashTrace != null && extraCrashTrace.length > 0) {
            sb.append(extraCrashTrace).append("\n")
        } else {
            try {
                val crashFile = File(context.filesDir, CRASH_FILE_NAME)
                if (crashFile.exists() && crashFile.length() > 0) {
                    sb.append(crashFile.readText(StandardCharsets.UTF_8)).append("\n")
                } else {
                    sb.append("Nenhum crash registrado em arquivo.\n")
                }
            } catch (e: Exception) {
                sb.append("Erro ao ler arquivo de crash: ").append(e.localizedMessage).append("\n")
            }
        }

        sb.append("\n------------------------------------------\n")
        sb.append("📝 HISTÓRICO DE LOGS RECENTES:\n")
        sb.append("------------------------------------------\n")

        val currentLogs = getLogs()
        if (currentLogs.isEmpty()) {
            sb.append("Nenhum log registrado na sessão atual.\n")
        } else {
            for (entry in currentLogs) {
                sb.append("[").append(entry.timestamp).append("] [").append(entry.level).append("] [").append(entry.tag).append("] ").append(entry.message).append("\n")
                val st = entry.stackTrace
                if (st != null && st.length > 0) {
                    val traceSnippet = if (st.length > 500) st.subSequence(0, 500) else st
                    sb.append("  Stack Trace:\n").append(traceSnippet).append("\n")
                }
            }
        }

        sb.append("==========================================\n")
        return sb.toString()
    }

    fun copyReportToClipboard(context: Context) {
        val report = buildDiagnosticReport(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("RetroGBA_Diagnostic_Log", report)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Relatório copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
    }

    fun shareReport(context: Context) {
        val report = buildDiagnosticReport(context)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Relatório de Erro - RetroGBA")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartilhar Diagnóstico do RetroGBA").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
