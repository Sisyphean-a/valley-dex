package com.example.stardewoffline.di

import android.content.Context
import androidx.room.Room
import com.example.stardewoffline.core.database.user.UserDataDao
import com.example.stardewoffline.core.database.user.UserDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, "user.db").build()

    @Provides fun provideUserDataDao(database: UserDatabase): UserDataDao = database.userDataDao()
}
