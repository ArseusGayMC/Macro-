package com.ggmacro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ggmacro.app.data.model.Macro

@Database(
    entities = [Macro::class],
    version = 1,
    exportSchema = false
)
abstract class MacroDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao

    companion object {
        const val DATABASE_NAME = "gg_macro_db"
    }
}
