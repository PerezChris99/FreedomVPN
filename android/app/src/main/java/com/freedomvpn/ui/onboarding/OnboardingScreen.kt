package com.freedomvpn.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomvpn.localization.LanguageManager
import kotlinx.coroutines.launch

/**
 * Simple Onboarding for Non-Technical Users
 * 
 * Designed for users who may have never used a VPN:
 * - Simple language, no jargon
 * - Visual demonstrations
 * - Multi-language support
 * - Minimal steps
 * - Big, obvious buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    languageManager: LanguageManager,
    onComplete: () -> Unit,
    onLanguageSelect: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    
    val pages = listOf(
        OnboardingPage.Welcome,
        OnboardingPage.WhatIsVpn,
        OnboardingPage.HowToUse,
        OnboardingPage.Safety,
        OnboardingPage.GetStarted
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Language selector button (top right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onLanguageSelect,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF00D9FF)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Language"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = languageManager.currentLanguage.value.nativeName,
                        fontSize = 14.sp
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    languageManager = languageManager
                )
            }

            // Bottom navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isSelected) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFF00D9FF)
                                    else Color.White.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Skip button
                    if (pagerState.currentPage < pages.size - 1) {
                        TextButton(
                            onClick = onComplete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                text = languageManager["skip"],
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }

                    // Next / Get Started button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D9FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage < pages.size - 1) {
                                languageManager["next"]
                            } else {
                                languageManager["get_started"]
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (pagerState.currentPage < pages.size - 1) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    languageManager: LanguageManager
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with animation
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            page.iconColor.copy(alpha = 0.3f),
                            page.iconColor.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = page.iconColor
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = page.getTitle(languageManager),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = page.getDescription(languageManager),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        // Extra content for specific pages
        if (page == OnboardingPage.HowToUse) {
            Spacer(modifier = Modifier.height(32.dp))
            HowToUseSteps(languageManager)
        }
    }
}

@Composable
private fun HowToUseSteps(languageManager: LanguageManager) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(20.dp)
    ) {
        StepItem(
            number = "1",
            text = when (languageManager.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Fungua programu"
                LanguageManager.Language.LUGANDA -> "Ggulawo apu"
                LanguageManager.Language.FRENCH -> "Ouvrez l'application"
                else -> "Open the app"
            }
        )
        StepItem(
            number = "2",
            text = when (languageManager.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Bofya kitufe kikubwa"
                LanguageManager.Language.LUGANDA -> "Nyiga batoni ennene"
                LanguageManager.Language.FRENCH -> "Appuyez sur le bouton"
                else -> "Tap the big button"
            }
        )
        StepItem(
            number = "3",
            text = when (languageManager.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Subiri - umefanikiwa!"
                LanguageManager.Language.LUGANDA -> "Linda - okuwandiise!"
                LanguageManager.Language.FRENCH -> "Attendez - c'est fait!"
                else -> "Wait - you're done!"
            },
            isLast = true
        )
    }
}

@Composable
private fun StepItem(
    number: String,
    text: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF00D9FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.White
        )
    }
    if (!isLast) {
        Box(
            modifier = Modifier
                .padding(start = 15.dp)
                .width(2.dp)
                .height(16.dp)
                .background(Color(0xFF00D9FF).copy(alpha = 0.5f))
        )
    }
}

sealed class OnboardingPage(
    val icon: ImageVector,
    val iconColor: Color
) {
    abstract fun getTitle(lang: LanguageManager): String
    abstract fun getDescription(lang: LanguageManager): String

    object Welcome : OnboardingPage(
        icon = Icons.Default.Shield,
        iconColor = Color(0xFF00D9FF)
    ) {
        override fun getTitle(lang: LanguageManager): String {
            return lang["welcome"]
        }
        
        override fun getDescription(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> 
                    "Programu rahisi ya kufikia intaneti kwa uhuru na usalama. Hakuna usajili, hakuna malipo."
                LanguageManager.Language.LUGANDA -> 
                    "Apu enyangu okukozesa yintaneti n'eddembe n'obukuumi. Tewali kusaininnga, tewali kusasula."
                LanguageManager.Language.FRENCH -> 
                    "Application simple pour accéder à internet librement et en sécurité. Pas d'inscription, gratuit."
                else -> 
                    "Simple app to access internet freely and securely. No registration, no payment required."
            }
        }
    }

    object WhatIsVpn : OnboardingPage(
        icon = Icons.Default.Lock,
        iconColor = Color(0xFF7C4DFF)
    ) {
        override fun getTitle(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "VPN ni nini?"
                LanguageManager.Language.LUGANDA -> "VPN kiki?"
                LanguageManager.Language.FRENCH -> "Qu'est-ce qu'un VPN?"
                else -> "What is a VPN?"
            }
        }
        
        override fun getDescription(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> 
                    "VPN inaficha shughuli zako za intaneti kutoka kwa serikali na mashirika. Hakuna mtu anayeweza kuona unavyotembelea."
                LanguageManager.Language.LUGANDA -> 
                    "VPN ekweka by'okola ku yintaneti okuva eri gavumenti n'amagabalizi. Tewali ayinza okulaba w'ogenda."
                LanguageManager.Language.FRENCH -> 
                    "Un VPN cache votre activité internet du gouvernement et des entreprises. Personne ne peut voir ce que vous faites."
                else -> 
                    "A VPN hides your internet activity from the government and companies. Nobody can see what websites you visit."
            }
        }
    }

    object HowToUse : OnboardingPage(
        icon = Icons.Default.TouchApp,
        iconColor = Color(0xFF00E676)
    ) {
        override fun getTitle(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Jinsi ya kutumia"
                LanguageManager.Language.LUGANDA -> "Engeri y'okukozesa"
                LanguageManager.Language.FRENCH -> "Comment utiliser"
                else -> "How to use"
            }
        }
        
        override fun getDescription(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Rahisi sana - hatua 3 tu!"
                LanguageManager.Language.LUGANDA -> "Kyangu nnyo - mitendera 3 gyokka!"
                LanguageManager.Language.FRENCH -> "Très simple - seulement 3 étapes!"
                else -> "Very simple - just 3 steps!"
            }
        }
    }

    object Safety : OnboardingPage(
        icon = Icons.Default.Security,
        iconColor = Color(0xFFFF7043)
    ) {
        override fun getTitle(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Kaa salama"
                LanguageManager.Language.LUGANDA -> "Beera bulungi"
                LanguageManager.Language.FRENCH -> "Restez en sécurité"
                else -> "Stay safe"
            }
        }
        
        override fun getDescription(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> 
                    "Tumia kitufe cha 'Panic' ikiwa uko hatarini. Bofya kitufe cha sauti chini mara 3 kwa haraka kuondoka.\n\nTunakuhifadhi salama."
                LanguageManager.Language.LUGANDA -> 
                    "Kozesa 'Panic Button' bw'oba mu bulabe. Nyiga volume down emirundi 3 mangu okufuluma.\n\nTukukuuma obulungi."
                LanguageManager.Language.FRENCH -> 
                    "Utilisez le 'Bouton Panique' si vous êtes en danger. Appuyez 3 fois sur volume bas pour quitter rapidement.\n\nNous vous protégeons."
                else -> 
                    "Use the 'Panic Button' if you're in danger. Press volume down 3 times quickly to exit.\n\nWe keep you safe."
            }
        }
    }

    object GetStarted : OnboardingPage(
        icon = Icons.Default.RocketLaunch,
        iconColor = Color(0xFF00D9FF)
    ) {
        override fun getTitle(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Uko tayari!"
                LanguageManager.Language.LUGANDA -> "Oli mweetegefu!"
                LanguageManager.Language.FRENCH -> "Vous êtes prêt!"
                else -> "You're ready!"
            }
        }
        
        override fun getDescription(lang: LanguageManager): String {
            return when (lang.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> 
                    "Bofya 'Anza' kuanza kujilinda. Kumbuka: VPN yako ni bure, salama, na ya faragha.\n\nIntaneti ya bure kwa wote! 🇺🇬"
                LanguageManager.Language.LUGANDA -> 
                    "Nyiga 'Tandika' okutandika okwekuuma. Jjukira: VPN yo ya bwereere, ya bukuumi, era ya kyama.\n\nYintaneti ya bwereere eri bonna! 🇺🇬"
                LanguageManager.Language.FRENCH -> 
                    "Appuyez sur 'Commencer' pour vous protéger. Rappelez-vous: Votre VPN est gratuit, sécurisé et privé.\n\nInternet libre pour tous! 🇺🇬"
                else -> 
                    "Tap 'Get Started' to start protecting yourself. Remember: Your VPN is free, safe, and private.\n\nFree internet for everyone! 🇺🇬"
            }
        }
    }
}

/**
 * Language Selection Dialog
 */
@Composable
fun LanguageSelectionDialog(
    languageManager: LanguageManager,
    onDismiss: () -> Unit,
    onSelect: (LanguageManager.Language) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B263B),
        title = {
            Text(
                text = "Select Language / Chagua Lugha",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                LanguageManager.Language.values().forEach { language ->
                    val isSelected = languageManager.currentLanguage.value == language
                    
                    Surface(
                        onClick = { onSelect(language) },
                        color = if (isSelected) Color(0xFF00D9FF).copy(alpha = 0.2f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = language.nativeName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = language.countries.joinToString(", "),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00D9FF)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Color(0xFF00D9FF))
            }
        }
    )
}
