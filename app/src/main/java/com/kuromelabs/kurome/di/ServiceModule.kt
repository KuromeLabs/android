package com.kuromelabs.kurome.di

import android.content.Context
import com.kuromelabs.kurome.domain.util.link.LinkProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @ServiceScoped
    @Provides
    fun provideLinkProvider(@ApplicationContext context: Context): LinkProvider {
        return LinkProvider(context)
    }
}