package com.example.airquix01

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * CsvLogger: weil einfache Textdateien nicht schon kompliziert genug sind.
 */

class CsvLogger(private val context: Context, private val fileName: String) {
    /**
     * Hier holt die Log-Datei oder erstellt sie, wenn sie sich entschließt nicht zu existieren...
     */
    private fun getLogFile(): File {
        val dir = context.getExternalFilesDir(null)
        val file = File(dir, fileName)
        if (!file.exists()) {
            file.writeText("timestamp,status\n")
        }
        return file
    }
    /**
     * Loggt den Status mit einem Zeitstempel.
     * hatte viele Problme mit dieser Funktion.. keine Ahnung wieso es nun funktioniert, aber Gemini hats gefixt
     */
    fun logStatus(status: String) {
        val file = getLogFile()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        FileWriter(file, true).use {
            it.append("$timestamp,$status\n")
        }
    }
    /**
     * Liest alle Logs und ignoriert den ersten Header, weil wir keinen Bedafr an Titelzeilen haben
     */
    fun readLogs(): List<String> {
        val file = getLogFile()
        if (!file.exists()) return emptyList()
        return file.readLines().drop(1)
    }
    /**
     * Löscht alle Logs, um Speicher zu sparen
     */
    fun deleteLogs() {
        val file = getLogFile()
        if (file.exists()) {
            file.delete()
        }
    }
    /**
     * Überprüft, ob die Log-Datei existiert. erwartungsgemäß einfach
     */
    fun fileExists(): Boolean {
        val file = getLogFile()
        return file.exists()
    }
    /**
     * gibt die Log-File zurück
     */
    fun getFile(): File = getLogFile()
}
