package com.pennywiseai.tracker.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for feature entitlements. In this fork, all features
 * are free and fully unlocked for all users by design.
 */
@Singleton
class EntitlementGate @Inject constructor() {

    val resolvedProEntitlement: StateFlow<Boolean?> = MutableStateFlow(true).asStateFlow()

    val isProEntitled: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
