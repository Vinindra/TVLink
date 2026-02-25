package com.tvlink.di

import com.tvlink.data.adb.AdbRepository
import com.tvlink.data.adb.AdbRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAdbRepository(impl: AdbRepositoryImpl): AdbRepository
}
