package com.midnight.kuira

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.midnight.kuira.feature.balance.BalanceScreen
import com.midnight.kuira.ui.theme.KuiraTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for Kuira Wallet.
 *
 * **Current Implementation:**
 * Simple integration test UI for proving live balance updates work.
 *
 * **Test Flow:**
 * 1. Display BalanceScreen with editable address input
 * 2. User sends transaction via external Midnight SDK script
 * 3. WebSocket receives update → Database → Flow → UI updates automatically
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuiraTheme {
                BalanceScreen()
            }
        }
    }
}