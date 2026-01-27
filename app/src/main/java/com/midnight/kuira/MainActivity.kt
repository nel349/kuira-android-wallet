package com.midnight.kuira

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.midnight.kuira.navigation.AppNavigation
import com.midnight.kuira.ui.theme.KuiraTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for Kuira Wallet.
 *
 * **Current Implementation:**
 * Phase 2F MVP - Balance viewing and send transactions.
 *
 * **Navigation:**
 * - Balance Screen (default): View wallet balance
 * - Send Screen: Send NIGHT transactions
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuiraTheme {
                AppNavigation()
            }
        }
    }
}