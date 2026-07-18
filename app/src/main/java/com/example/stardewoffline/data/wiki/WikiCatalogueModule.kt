package com.example.stardewoffline.data.wiki

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WikiCatalogueModule {
    @Binds
    abstract fun bindWikiCatalogue(implementation: DefaultWikiCatalogue): WikiCatalogue
}
