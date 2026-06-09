package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceRepositoryImpl(context: Context) : PreferenceRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("dompetku_prefs", Context.MODE_PRIVATE)

    private val _themeFlow = MutableStateFlow(prefs.getString("theme", "SYSTEM") ?: "SYSTEM")
    override val themeFlow: StateFlow<String> = _themeFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(prefs.getString("language", "id") ?: "id")
    override val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _currencyFlow = MutableStateFlow(prefs.getString("currency", "IDR") ?: "IDR")
    override val currencyFlow: StateFlow<String> = _currencyFlow.asStateFlow()

    private val _pinFlow = MutableStateFlow(prefs.getString("pin_code", null))
    override val pinFlow: StateFlow<String?> = _pinFlow.asStateFlow()

    private val _biometricFlow = MutableStateFlow(prefs.getBoolean("biometric_enabled", false))
    override val biometricFlow: StateFlow<Boolean> = _biometricFlow.asStateFlow()

    private val _onboardingCompletedFlow = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    override val onboardingCompletedFlow: StateFlow<Boolean> = _onboardingCompletedFlow.asStateFlow()

    override suspend fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _themeFlow.value = theme
    }

    override suspend fun setLanguage(language: String) {
        prefs.edit().putString("language", language).apply()
        _languageFlow.value = language
    }

    override suspend fun setCurrency(currency: String) {
        prefs.edit().putString("currency", currency).apply()
        _currencyFlow.value = currency
    }

    override suspend fun setPin(pin: String?) {
        prefs.edit().putString("pin_code", pin).apply()
        _pinFlow.value = pin
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _biometricFlow.value = enabled
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
        _onboardingCompletedFlow.value = completed
     }

    private val _dashboardTourCompletedFlow = MutableStateFlow(prefs.getBoolean("dashboard_tour_completed", false))
    override val dashboardTourCompletedFlow: StateFlow<Boolean> = _dashboardTourCompletedFlow.asStateFlow()

    override suspend fun setDashboardTourCompleted(completed: Boolean) {
        prefs.edit().putBoolean("dashboard_tour_completed", completed).apply()
        _dashboardTourCompletedFlow.value = completed
    }

    private val _notificationListenerEnabledFlow = MutableStateFlow(prefs.getBoolean("notification_listener_enabled", false))
    override val notificationListenerEnabledFlow: StateFlow<Boolean> = _notificationListenerEnabledFlow.asStateFlow()

    override suspend fun setNotificationListenerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notification_listener_enabled", enabled).apply()
        _notificationListenerEnabledFlow.value = enabled
    }

    override fun isNotificationAppEnabled(packageName: String): Boolean {
        return prefs.getBoolean("notif_app_enabled_$packageName", true)
    }

    override suspend fun setNotificationAppEnabled(packageName: String, enabled: Boolean) {
        prefs.edit().putBoolean("notif_app_enabled_$packageName", enabled).apply()
    }
}

