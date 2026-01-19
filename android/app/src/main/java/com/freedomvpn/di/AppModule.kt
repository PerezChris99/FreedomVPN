package com.freedomvpn.di

import android.content.Context
import com.freedomvpn.vpn.VpnConnectionManager
import com.freedomvpn.vpngate.VpnGateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVpnGateRepository(): VpnGateRepository {
        return VpnGateRepository()
    }

    @Provides
    @Singleton
    fun provideVpnConnectionManager(
        @ApplicationContext context: Context
    ): VpnConnectionManager {
        return VpnConnectionManager(context)
    }
}
