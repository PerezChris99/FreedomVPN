package com.freedomvpn.localization

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-Language Support for Africa
 * 
 * Provides translations in major African languages to improve
 * accessibility for non-English speakers.
 * 
 * Supported languages:
 * - English (default)
 * - Swahili (Kenya, Tanzania, Uganda)
 * - Luganda (Uganda)
 * - French (West/Central Africa)
 * - Amharic (Ethiopia)
 * - Arabic (North Africa)
 * - Portuguese (Mozambique, Angola)
 * - Hausa (Nigeria, Niger)
 */
@Singleton
class LanguageManager @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "freedom_vpn_language", Context.MODE_PRIVATE
    )

    private val _currentLanguage = MutableStateFlow(Language.ENGLISH)
    val currentLanguage: StateFlow<Language> = _currentLanguage

    enum class Language(
        val code: String,
        val nativeName: String,
        val englishName: String,
        val countries: List<String>
    ) {
        ENGLISH("en", "English", "English", listOf("Global")),
        SWAHILI("sw", "Kiswahili", "Swahili", listOf("Kenya", "Tanzania", "Uganda", "DRC")),
        LUGANDA("lg", "Luganda", "Luganda", listOf("Uganda")),
        FRENCH("fr", "Français", "French", listOf("Senegal", "Ivory Coast", "Cameroon", "DRC", "Mali")),
        AMHARIC("am", "አማርኛ", "Amharic", listOf("Ethiopia")),
        ARABIC("ar", "العربية", "Arabic", listOf("Egypt", "Morocco", "Algeria", "Sudan")),
        PORTUGUESE("pt", "Português", "Portuguese", listOf("Mozambique", "Angola", "Cape Verde")),
        HAUSA("ha", "Hausa", "Hausa", listOf("Nigeria", "Niger", "Ghana"))
    }

    // ==================== STRING RESOURCES ====================

    private val translations = mapOf(
        // ===== ENGLISH =====
        Language.ENGLISH to mapOf(
            // Main Screen
            "app_name" to "FreedomVPN",
            "connect" to "Connect",
            "disconnect" to "Disconnect",
            "connecting" to "Connecting...",
            "connected" to "Connected",
            "disconnected" to "Disconnected",
            "tap_to_connect" to "Tap to connect",
            "protected" to "Your connection is protected",
            "not_protected" to "Your connection is not protected",
            
            // Server Selection
            "servers" to "Servers",
            "select_server" to "Select Server",
            "best_server" to "Best Server",
            "quick_connect" to "Quick Connect",
            "favorites" to "Favorites",
            "recent" to "Recent",
            "all_servers" to "All Servers",
            "search_servers" to "Search servers...",
            "no_servers" to "No servers available",
            "server_speed" to "Speed",
            "server_ping" to "Ping",
            "server_load" to "Load",
            
            // Settings
            "settings" to "Settings",
            "language" to "Language",
            "kill_switch" to "Kill Switch",
            "kill_switch_desc" to "Block internet if VPN disconnects",
            "auto_connect" to "Auto Connect",
            "auto_connect_desc" to "Connect automatically on app start",
            "split_tunneling" to "Split Tunneling",
            "split_tunneling_desc" to "Choose which apps use VPN",
            "data_saver" to "Data Saver",
            "data_saver_desc" to "Compress data to save mobile data",
            "stealth_mode" to "Stealth Mode",
            "stealth_mode_desc" to "Hide VPN traffic from detection",
            "about" to "About",
            "help" to "Help",
            "privacy_policy" to "Privacy Policy",
            
            // Stats
            "download" to "Download",
            "upload" to "Upload",
            "duration" to "Duration",
            "data_used" to "Data Used",
            "data_saved" to "Data Saved",
            
            // Errors & Warnings
            "connection_failed" to "Connection failed",
            "try_again" to "Try Again",
            "no_internet" to "No internet connection",
            "vpn_blocked" to "VPN may be blocked",
            "switching_server" to "Switching to another server...",
            
            // Stealth
            "panic_button" to "Panic Button",
            "panic_desc" to "Quickly disconnect and hide evidence",
            "app_disguise" to "App Disguise",
            
            // Onboarding
            "welcome" to "Welcome to FreedomVPN",
            "onboarding_1" to "Access the internet freely and securely",
            "onboarding_2" to "One tap to connect - no registration needed",
            "onboarding_3" to "Your traffic is encrypted and hidden",
            "get_started" to "Get Started",
            "skip" to "Skip",
            "next" to "Next"
        ),
        
        // ===== SWAHILI =====
        Language.SWAHILI to mapOf(
            // Main Screen
            "app_name" to "FreedomVPN",
            "connect" to "Unganisha",
            "disconnect" to "Tenganisha",
            "connecting" to "Inaunganisha...",
            "connected" to "Umeunganishwa",
            "disconnected" to "Umetenganishwa",
            "tap_to_connect" to "Gusa kuunganisha",
            "protected" to "Muunganisho wako unalindwa",
            "not_protected" to "Muunganisho wako haujalindwa",
            
            // Server Selection
            "servers" to "Seva",
            "select_server" to "Chagua Seva",
            "best_server" to "Seva Bora",
            "quick_connect" to "Unganisha Haraka",
            "favorites" to "Vipendwa",
            "recent" to "Hivi Karibuni",
            "all_servers" to "Seva Zote",
            "search_servers" to "Tafuta seva...",
            "no_servers" to "Hakuna seva zinazopatikana",
            "server_speed" to "Kasi",
            "server_ping" to "Ping",
            "server_load" to "Mzigo",
            
            // Settings
            "settings" to "Mipangilio",
            "language" to "Lugha",
            "kill_switch" to "Swichi ya Kuua",
            "kill_switch_desc" to "Zuia intaneti VPN ikiacha",
            "auto_connect" to "Unganisha Kiotomatiki",
            "auto_connect_desc" to "Unganisha moja kwa moja programu inapoanza",
            "split_tunneling" to "Mgawanyiko wa Tuneli",
            "split_tunneling_desc" to "Chagua programu zipi zitumie VPN",
            "data_saver" to "Kiokoa Data",
            "data_saver_desc" to "Punguza data ili kuokoa data ya simu",
            "stealth_mode" to "Hali ya Siri",
            "stealth_mode_desc" to "Ficha trafiki ya VPN",
            "about" to "Kuhusu",
            "help" to "Msaada",
            "privacy_policy" to "Sera ya Faragha",
            
            // Stats
            "download" to "Pakua",
            "upload" to "Pakia",
            "duration" to "Muda",
            "data_used" to "Data Iliyotumika",
            "data_saved" to "Data Iliyookolewa",
            
            // Errors
            "connection_failed" to "Muunganisho umeshindikana",
            "try_again" to "Jaribu Tena",
            "no_internet" to "Hakuna muunganisho wa intaneti",
            "vpn_blocked" to "VPN inaweza kuzuiwa",
            "switching_server" to "Inabadilisha seva...",
            
            // Stealth
            "panic_button" to "Kitufe cha Dharura",
            "panic_desc" to "Tenganisha haraka na uficha ushahidi",
            "app_disguise" to "Ficha Programu",
            
            // Onboarding
            "welcome" to "Karibu FreedomVPN",
            "onboarding_1" to "Fikia intaneti kwa uhuru na usalama",
            "onboarding_2" to "Gusa mara moja kuunganisha - hakuna usajili",
            "onboarding_3" to "Trafiki yako imefichwa na kulindwa",
            "get_started" to "Anza",
            "skip" to "Ruka",
            "next" to "Ifuatayo"
        ),
        
        // ===== LUGANDA (Uganda) =====
        Language.LUGANDA to mapOf(
            // Main Screen
            "app_name" to "FreedomVPN",
            "connect" to "Yungana",
            "disconnect" to "Gyako",
            "connecting" to "Eyunga...",
            "connected" to "Oyungidde",
            "disconnected" to "Ogiddeko",
            "tap_to_connect" to "Nyiga okuyunga",
            "protected" to "Enyunzi yo ekuumiddwa",
            "not_protected" to "Enyunzi yo terikuumibwa",
            
            // Server Selection
            "servers" to "Seva",
            "select_server" to "Londa Seva",
            "best_server" to "Seva Esinga Obulungi",
            "quick_connect" to "Yunga Mangu",
            "favorites" to "Ze Oyagala",
            "recent" to "Za Kaakano",
            "all_servers" to "Seva Zonna",
            "search_servers" to "Noonya seva...",
            "no_servers" to "Tewali seva",
            "server_speed" to "Mbiiro",
            "server_ping" to "Ping",
            "server_load" to "Obuzito",
            
            // Settings
            "settings" to "Entegeka",
            "language" to "Olulimi",
            "kill_switch" to "Swichi y'Okutta",
            "kill_switch_desc" to "Ziyiza intaneti VPN bw'egwa",
            "auto_connect" to "Yungana Wekka",
            "auto_connect_desc" to "Yungana apu bw'etandika",
            "split_tunneling" to "Gabanya Entuneli",
            "split_tunneling_desc" to "Londa apu zirikozesa VPN",
            "data_saver" to "Tereka Data",
            "data_saver_desc" to "Nyigiriza data okulonga data ya simu",
            "stealth_mode" to "Engeri Ekisibe",
            "stealth_mode_desc" to "Kweka traffic ya VPN",
            "about" to "Ebikwatako",
            "help" to "Obuyambi",
            "privacy_policy" to "Enkola y'Ebyama",
            
            // Stats
            "download" to "Dawunirodi",
            "upload" to "Aplodi",
            "duration" to "Ekiseera",
            "data_used" to "Data Ekozeseddwa",
            "data_saved" to "Data Eterekeddwa",
            
            // Errors
            "connection_failed" to "Okuyunga kugaanye",
            "try_again" to "Gezako Nate",
            "no_internet" to "Tewali yintaneti",
            "vpn_blocked" to "VPN eyinza okuba eziyiziddwa",
            "switching_server" to "Ekyusa seva...",
            
            // Stealth
            "panic_button" to "Batoni y'Obwangu",
            "panic_desc" to "Gya mangu era okweke ebijulirwa",
            "app_disguise" to "Kweka Apu",
            
            // Onboarding
            "welcome" to "Tukwaniriza ku FreedomVPN",
            "onboarding_1" to "Kozesa yintaneti n'eddembe n'obukuumi",
            "onboarding_2" to "Nyiga mulundi gumu okuyunga - tewetaagisa kusaininnga",
            "onboarding_3" to "Traffic yo ekuumiddwa era ekwekeddwa",
            "get_started" to "Tandika",
            "skip" to "Buuka",
            "next" to "Ekiddako"
        ),
        
        // ===== FRENCH =====
        Language.FRENCH to mapOf(
            // Main Screen
            "app_name" to "FreedomVPN",
            "connect" to "Connecter",
            "disconnect" to "Déconnecter",
            "connecting" to "Connexion...",
            "connected" to "Connecté",
            "disconnected" to "Déconnecté",
            "tap_to_connect" to "Appuyez pour connecter",
            "protected" to "Votre connexion est protégée",
            "not_protected" to "Votre connexion n'est pas protégée",
            
            // Server Selection
            "servers" to "Serveurs",
            "select_server" to "Sélectionner un Serveur",
            "best_server" to "Meilleur Serveur",
            "quick_connect" to "Connexion Rapide",
            "favorites" to "Favoris",
            "recent" to "Récents",
            "all_servers" to "Tous les Serveurs",
            "search_servers" to "Rechercher des serveurs...",
            "no_servers" to "Aucun serveur disponible",
            "server_speed" to "Vitesse",
            "server_ping" to "Ping",
            "server_load" to "Charge",
            
            // Settings
            "settings" to "Paramètres",
            "language" to "Langue",
            "kill_switch" to "Arrêt d'Urgence",
            "kill_switch_desc" to "Bloquer internet si VPN se déconnecte",
            "auto_connect" to "Connexion Auto",
            "auto_connect_desc" to "Se connecter au démarrage",
            "split_tunneling" to "Tunneling Divisé",
            "split_tunneling_desc" to "Choisir quelles apps utilisent VPN",
            "data_saver" to "Économiseur de Données",
            "data_saver_desc" to "Compresser les données pour économiser",
            "stealth_mode" to "Mode Furtif",
            "stealth_mode_desc" to "Cacher le trafic VPN",
            "about" to "À Propos",
            "help" to "Aide",
            "privacy_policy" to "Politique de Confidentialité",
            
            // Stats
            "download" to "Téléchargement",
            "upload" to "Envoi",
            "duration" to "Durée",
            "data_used" to "Données Utilisées",
            "data_saved" to "Données Économisées",
            
            // Errors
            "connection_failed" to "Échec de connexion",
            "try_again" to "Réessayer",
            "no_internet" to "Pas de connexion internet",
            "vpn_blocked" to "VPN peut être bloqué",
            "switching_server" to "Changement de serveur...",
            
            // Stealth
            "panic_button" to "Bouton Panique",
            "panic_desc" to "Déconnecter rapidement et cacher les preuves",
            "app_disguise" to "Déguiser l'App",
            
            // Onboarding
            "welcome" to "Bienvenue sur FreedomVPN",
            "onboarding_1" to "Accédez à internet librement et en sécurité",
            "onboarding_2" to "Un appui pour connecter - pas d'inscription",
            "onboarding_3" to "Votre trafic est chiffré et caché",
            "get_started" to "Commencer",
            "skip" to "Passer",
            "next" to "Suivant"
        ),
        
        // ===== AMHARIC (Ethiopia) =====
        Language.AMHARIC to mapOf(
            "app_name" to "FreedomVPN",
            "connect" to "አገናኝ",
            "disconnect" to "አቋርጥ",
            "connecting" to "በማገናኘት ላይ...",
            "connected" to "ተገናኝቷል",
            "disconnected" to "ተቋርጧል",
            "tap_to_connect" to "ለማገናኘት ንካ",
            "protected" to "ግንኙነትዎ የተጠበቀ ነው",
            "not_protected" to "ግንኙነትዎ አልተጠበቀም",
            "servers" to "አገልጋዮች",
            "select_server" to "አገልጋይ ምረጥ",
            "settings" to "ቅንብሮች",
            "language" to "ቋንቋ",
            "connect" to "አገናኝ",
            "welcome" to "ወደ FreedomVPN እንኳን በደህና መጡ",
            "get_started" to "ጀምር",
            "skip" to "ዝለል",
            "next" to "ቀጣይ"
        ),
        
        // ===== ARABIC (North Africa) =====
        Language.ARABIC to mapOf(
            "app_name" to "FreedomVPN",
            "connect" to "اتصال",
            "disconnect" to "قطع الاتصال",
            "connecting" to "جاري الاتصال...",
            "connected" to "متصل",
            "disconnected" to "غير متصل",
            "tap_to_connect" to "انقر للاتصال",
            "protected" to "اتصالك محمي",
            "not_protected" to "اتصالك غير محمي",
            "servers" to "الخوادم",
            "select_server" to "اختر خادم",
            "settings" to "الإعدادات",
            "language" to "اللغة",
            "welcome" to "مرحباً بك في FreedomVPN",
            "get_started" to "ابدأ",
            "skip" to "تخطي",
            "next" to "التالي"
        ),
        
        // ===== PORTUGUESE (Mozambique, Angola) =====
        Language.PORTUGUESE to mapOf(
            "app_name" to "FreedomVPN",
            "connect" to "Conectar",
            "disconnect" to "Desconectar",
            "connecting" to "Conectando...",
            "connected" to "Conectado",
            "disconnected" to "Desconectado",
            "tap_to_connect" to "Toque para conectar",
            "protected" to "Sua conexão está protegida",
            "not_protected" to "Sua conexão não está protegida",
            "servers" to "Servidores",
            "select_server" to "Selecionar Servidor",
            "settings" to "Configurações",
            "language" to "Idioma",
            "welcome" to "Bem-vindo ao FreedomVPN",
            "get_started" to "Começar",
            "skip" to "Pular",
            "next" to "Próximo"
        ),
        
        // ===== HAUSA (Nigeria, Niger) =====
        Language.HAUSA to mapOf(
            "app_name" to "FreedomVPN",
            "connect" to "Haɗa",
            "disconnect" to "Katse",
            "connecting" to "Ana haɗawa...",
            "connected" to "An haɗa",
            "disconnected" to "An katse",
            "tap_to_connect" to "Taɓa don haɗawa",
            "protected" to "An kare haɗin ku",
            "not_protected" to "Ba a kare haɗin ku ba",
            "servers" to "Sabar",
            "select_server" to "Zaɓi Sabar",
            "settings" to "Saituna",
            "language" to "Harshe",
            "welcome" to "Barka da zuwa FreedomVPN",
            "get_started" to "Fara",
            "skip" to "Tsallake",
            "next" to "Gaba"
        )
    )

    // ==================== INITIALIZATION ====================

    init {
        loadSavedLanguage()
    }

    private fun loadSavedLanguage() {
        val savedCode = prefs.getString(KEY_LANGUAGE, null)
        if (savedCode != null) {
            val language = Language.values().find { it.code == savedCode } ?: Language.ENGLISH
            _currentLanguage.value = language
        } else {
            // Auto-detect from system locale
            detectSystemLanguage()
        }
    }

    private fun detectSystemLanguage() {
        val systemLocale = Locale.getDefault().language
        val matchedLanguage = Language.values().find { it.code == systemLocale } ?: Language.ENGLISH
        _currentLanguage.value = matchedLanguage
    }

    // ==================== PUBLIC API ====================

    fun setLanguage(language: Language) {
        _currentLanguage.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun getString(key: String): String {
        val lang = _currentLanguage.value
        return translations[lang]?.get(key)
            ?: translations[Language.ENGLISH]?.get(key)
            ?: key
    }

    operator fun get(key: String): String = getString(key)

    fun getAvailableLanguages(): List<Language> = Language.values().toList()

    fun getLanguageForCountry(countryCode: String): Language {
        return Language.values().find { lang ->
            lang.countries.any { it.equals(countryCode, ignoreCase = true) }
        } ?: Language.ENGLISH
    }

    // ==================== COMPOSE HELPERS ====================

    /**
     * Use in Compose: val text = lang("connect")
     */
    fun lang(key: String): String = getString(key)

    companion object {
        private const val KEY_LANGUAGE = "selected_language"
    }
}
