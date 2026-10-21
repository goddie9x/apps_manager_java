package com.god.ApplicationManager.UI

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.god.ApplicationManager.Entity.AppInfo
import com.god.ApplicationManager.Interface.IHandleListApp
import com.god.ApplicationManager.data.AppListRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Exposes the main screen's list state as [StateFlow]s backed by
 * [AppListRepository]. Loading/filtering still runs through the existing
 * TaskingHandler pipeline (batch-by-batch migration); this ViewModel is the
 * lifecycle-aware observation point the Activity renders from.
 */
class MainViewModel : ViewModel() {
    val displayedApps: StateFlow<List<AppInfo>> = AppListRepository.displayedApps
    val loading: StateFlow<Boolean> = AppListRepository.loading

    /** Java-friendly collector: delivers the displayed list while [owner] is at least STARTED. */
    fun observeDisplayedApps(owner: LifecycleOwner, listener: IHandleListApp) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                displayedApps.collect { listener.callback(it) }
            }
        }
    }
}
