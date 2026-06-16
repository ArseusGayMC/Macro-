package com.ggmacro.app.utils

import android.content.Context
import android.net.Uri
import com.ggmacro.app.data.model.Macro
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object MacroImportExport {

    private val gson = GsonProvider.gson

    data class MacroExportFile(
        val version: Int = 1,
        val appId: String = "com.ggmacro.app",
        val macros: List<Macro>
    )

    fun exportMacros(context: Context, macros: List<Macro>, uri: Uri): Result<Unit> {
        return try {
            val exportData = MacroExportFile(macros = macros)
            val json = gson.toJson(exportData)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importMacros(context: Context, uri: Uri): Result<List<Macro>> {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return Result.failure(Exception("Cannot open file"))

            val exportFile = gson.fromJson(json, MacroExportFile::class.java)
            if (exportFile.appId != "com.ggmacro.app") {
                return Result.failure(Exception("Invalid file format"))
            }
            val importedMacros = exportFile.macros.map { macro ->
                macro.copy(
                    id = 0L,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
            Result.success(importedMacros)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
