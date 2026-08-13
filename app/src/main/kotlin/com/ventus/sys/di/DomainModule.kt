package com.ventus.sys.di

import com.ventus.sys.data.repository.FeatureResolverImpl
import com.ventus.sys.domain.FeatureResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the domain-level [FeatureResolver] interface to its data-layer implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {
    @Binds
    @Singleton
    abstract fun bindFeatureResolver(impl: FeatureResolverImpl): FeatureResolver
}
