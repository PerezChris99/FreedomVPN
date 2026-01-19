package com.freedomvpn.di

import android.content.Context
import com.freedomvpn.localization.LanguageManager
import com.freedomvpn.security.CertificatePinningManager
import com.freedomvpn.security.SecureLogger
import com.freedomvpn.security.SecureStorage
import com.freedomvpn.security.SecurityChecker
import com.freedomvpn.stealth.StealthManager
import com.freedomvpn.update.UpdateManager
import com.freedomvpn.vpn.VpnConnectionManager
import com.freedomvpn.vpn.obfuscation.AdvancedObfuscationManager
import com.freedomvpn.vpn.optimization.BatteryOptimizer
import com.freedomvpn.vpn.optimization.ConnectionOptimizer
import com.freedomvpn.vpn.optimization.DataCompressionManager
import com.freedomvpn.vpn.optimization.LowBandwidthOptimizer
import com.freedomvpn.vpn.optimization.PacketOptimizer
import com.freedomvpn.vpn.optimization.SplitTunnelingManager
import com.freedomvpn.vpn.resilience.OfflineResilienceManager
import com.freedomvpn.vpn.server.AfricanServerPriority
import com.freedomvpn.vpngate.VpnGateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

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
    
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage {
        return SecureStorage(context)
    }
    
    @Provides
    @Singleton
    fun provideSecurityChecker(
        @ApplicationContext context: Context
    ): SecurityChecker {
        return SecurityChecker(context)
    }
    
    @Provides
    @Singleton
    fun provideSecureLogger(): SecureLogger {
        return SecureLogger()
    }
    
    @Provides
    @Singleton
    fun provideCertificatePinningManager(): CertificatePinningManager {
        return CertificatePinningManager()
    }
    
    @Provides
    @Singleton
    fun provideConnectionOptimizer(): ConnectionOptimizer {
        return ConnectionOptimizer()
    }
    
    @Provides
    @Singleton
    fun provideBatteryOptimizer(
        @ApplicationContext context: Context
    ): BatteryOptimizer {
        return BatteryOptimizer(context)
    }
    
    @Provides
    @Singleton
    fun providePacketOptimizer(
        @ApplicationContext context: Context
    ): PacketOptimizer {
        return PacketOptimizer(context)
    }
    
    @Provides
    @Singleton
    fun provideSplitTunnelingManager(
        @ApplicationContext context: Context
    ): SplitTunnelingManager {
        return SplitTunnelingManager(context)
    }
    
    @Provides
    @Singleton
    fun provideUpdateManager(
        @ApplicationContext context: Context
    ): UpdateManager {
        return UpdateManager(context)
    }
    
    // ==================== AFRICA-SPECIFIC FEATURES ====================
    
    @Provides
    @Singleton
    fun provideAdvancedObfuscationManager(): AdvancedObfuscationManager {
        return AdvancedObfuscationManager()
    }
    
    @Provides
    @Singleton
    fun provideDataCompressionManager(): DataCompressionManager {
        return DataCompressionManager()
    }
    
    @Provides
    @Singleton
    fun provideLowBandwidthOptimizer(
        @ApplicationContext context: Context
    ): LowBandwidthOptimizer {
        return LowBandwidthOptimizer(context)
    }
    
    @Provides
    @Singleton
    fun provideAfricanServerPriority(): AfricanServerPriority {
        return AfricanServerPriority()
    }
    
    @Provides
    @Singleton
    fun provideOfflineResilienceManager(
        @ApplicationContext context: Context
    ): OfflineResilienceManager {
        return OfflineResilienceManager(context)
    }
    
    @Provides
    @Singleton
    fun provideLanguageManager(
        @ApplicationContext context: Context
    ): LanguageManager {
        return LanguageManager(context)
    }
    
    @Provides
    @Singleton
    fun provideStealthManager(
        @ApplicationContext context: Context
    ): StealthManager {
        return StealthManager(context)
    }
}

