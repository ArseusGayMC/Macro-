package com.ggmacro.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class Macro(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val actionsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val loopCount: Int = 1,
    val playbackSpeed: Float = 1.0f,
    val tapDuration: Long = 50L,
    val actionDelay: Long = 0L
) {
    val isInfiniteLoop: Boolean get() = loopCount == -1
}
