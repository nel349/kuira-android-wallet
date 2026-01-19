package com.midnight.kuira

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Kuira Wallet.
 *
 * **Purpose:**
 * - Initialize Hilt dependency injection
 * - Single source of truth for app-level components
 *
 * **Hilt Setup:**
 * The @HiltAndroidApp annotation triggers Hilt's code generation and creates
 * the application-level dependency container that serves as the parent for all
 * other Android components (Activities, ViewModels, etc.).
 */
@HiltAndroidApp
class KuiraApplication : Application()
