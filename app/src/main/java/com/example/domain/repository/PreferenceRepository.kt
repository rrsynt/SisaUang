package com.example.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface PreferenceRepository {
    // Dynamic themes
    val themeFlow: StateFlow<String>
    suspend fun setTheme(theme: String)

    // Dynamic languages
    val languageFlow: StateFlow<String>
    suspend fun setLanguage(language: String)

    // Currency preference (IDR vs USD)
    val currencyFlow: StateFlow<String>
    suspend fun setCurrency(currency: String)

    // Biometrics and security
    val pinFlow: StateFlow<String?>
    suspend fun setPin(pin: String?)

    val biometricFlow: StateFlow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)

    // Onboarding
    val onboardingCompletedFlow: StateFlow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    // User Tour Guide
    val dashboardTourCompletedFlow: StateFlow<Boolean>
    suspend fun setDashboardTourCompleted(completed: Boolean)

    // Notification Listener
    val notificationListenerEnabledFlow: StateFlow<Boolean>
    suspend fun setNotificationListenerEnabled(enabled: Boolean)
    fun isNotificationAppEnabled(packageName: String): Boolean
    suspend fun setNotificationAppEnabled(packageName: String, enabled: Boolean)
}

