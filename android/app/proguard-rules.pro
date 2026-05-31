# Add project specific ProGuard rules here.

# WireGuard
-keep class com.wireguard.** { *; }
-keep class com.wireguard.android.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keep class com.freedomvpn.vpngate.VpnGateServer { *; }
-keep class com.freedomvpn.vpn.FreedomVpnService$ConnectionStats { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Keep all VPN and security classes
-keep class com.freedomvpn.vpn.** { *; }
-keep class com.freedomvpn.security.** { *; }
-keep class com.freedomvpn.service.** { *; }
-keep class com.freedomvpn.data.model.** { *; }

# Obfuscation classes - keep but obfuscate internals
-keep class com.freedomvpn.vpn.obfuscation.TrafficObfuscator { *; }

# Crypto/Security
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class android.security.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Aggressive obfuscation for security
-repackageclasses 'f'
-allowaccessmodification

# Keep BuildConfig
-keep class com.freedomvpn.BuildConfig { *; }

# Don't warn about missing classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
