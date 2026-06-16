package com.ggmacro.app.data.local

import androidx.room.*
import com.ggmacro.app.data.model.Macro
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {

    @Query("SELECT * FROM macros ORDER BY updatedAt DESC")
    fun getAllMacros(): Flow<List<Macro>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getMacroById(id: Long): Macro?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: Macro): Long

    @Update
    suspend fun updateMacro(macro: Macro)

    @Delete
    suspend fun deleteMacro(macro: Macro)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteMacroById(id: Long)

    @Query("SELECT COUNT(*) FROM macros")
    suspend fun getMacroCount(): Int
}
