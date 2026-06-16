package com.ggmacro.app.di

import android.content.Context
import androidx.room.Room
import com.ggmacro.app.data.local.MacroDao
import com.ggmacro.app.data.local.MacroDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMacroDatabase(
        @ApplicationContext context: Context
    ): MacroDatabase {
        return Room.databaseBuilder(
            context,
            MacroDatabase::class.java,
            MacroDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMacroDao(database: MacroDatabase): MacroDao {
        return database.macroDao()
    }
}
