package com.god.ApplicationManager.data

import com.god.ApplicationManager.Entity.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the app list currently shown by the main screen.
 *
 * Producers (today: TaskingHandler while its legacy pipeline is retired step by
 * step) publish computed lists here; the UI observes [displayedApps] through
 * MainViewModel instead of receiving one-shot callbacks.
 */
object AppListRepository {
    private val _displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val displayedApps: StateFlow<List<AppInfo>> = _displayedApps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    @JvmStatic
    fun publish(apps: List<AppInfo>) {
        _displayedApps.value = apps
        _loading.value = false
    }

    @JvmStatic
    fun setLoading(value: Boolean) {
        _loading.value = value
    }
}
