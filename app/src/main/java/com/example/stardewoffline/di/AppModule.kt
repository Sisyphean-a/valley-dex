package com.example.stardewoffline.di

import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.common.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }

    @Provides
    @Singleton
    fun provideHashUtils(@IoDispatcher dispatcher: CoroutineDispatcher): HashUtils = HashUtils(dispatcher)
}
