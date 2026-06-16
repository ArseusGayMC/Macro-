package com.ggmacro.app.data.repository

import com.ggmacro.app.data.local.MacroDao
import com.ggmacro.app.data.model.Macro
import com.ggmacro.app.data.model.MacroAction
import com.ggmacro.app.utils.GsonProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroRepository @Inject constructor(
    private val macroDao: MacroDao
) {
    private val gson = GsonProvider.gson

    fun getAllMacros(): Flow<List<Macro>> = macroDao.getAllMacros()

    suspend fun getMacroById(id: Long): Macro? = macroDao.getMacroById(id)

    suspend fun saveMacro(macro: Macro): Long = macroDao.insertMacro(macro)

    suspend fun updateMacro(macro: Macro) = macroDao.updateMacro(macro.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteMacro(id: Long) = macroDao.deleteMacroById(id)

    suspend fun duplicateMacro(macro: Macro): Long {
        val copy = macro.copy(
            id = 0L,
            name = "${macro.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return macroDao.insertMacro(copy)
    }

    fun getMacroActions(macro: Macro): List<MacroAction> {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<MacroAction>>() {}.type
            gson.fromJson(macro.actionsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeActions(actions: List<MacroAction>): String {
        return gson.toJson(actions)
    }
}
