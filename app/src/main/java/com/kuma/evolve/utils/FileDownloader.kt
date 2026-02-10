package com.kuma.evolve.utils

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object FileDownloader {

    fun saveCsv(context: Context, content: String, prefix: String = "asistencia") {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${prefix}_${timeStamp}.csv"
            
            // Usar directorio de descargas público
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            FileOutputStream(file).use { output: FileOutputStream ->
                output.write(content.toByteArray())
            }
            
            Toast.makeText(context, "Archivo guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("FileDownloader", "Error al guardar CSV", e)
            Toast.makeText(context, "Error al guardar archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
