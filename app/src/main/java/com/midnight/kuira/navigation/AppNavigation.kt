package com.midnight.kuira.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.midnight.kuira.feature.balance.BalanceScreen
import com.midnight.kuira.feature.send.SendScreen

/**
 * App navigation routes.
 *
 * **Pattern:** Sealed class for type-safe navigation with arguments
 * **Routes:**
 * - Balance: Main screen showing wallet balance
 * - Send: Send transaction screen (requires sender address)
 */
sealed class Screen(val route: String) {
    data object Balance : Screen("balance")
    data object Send : Screen("send/{address}") {
        fun createRoute(address: String) = "send/$address"
    }
}

/**
 * Main app navigation setup.
 *
 * **Navigation Flow:**
 * - Start: Balance screen (default)
 * - Balance → Send: Click "Send" button
 * - Send → Balance: Navigate back after transaction
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun MainActivity() {
 *     KuiraTheme {
 *         AppNavigation()
 *     }
 * }
 * ```
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Balance.route
    ) {
        // Balance Screen
        composable(route = Screen.Balance.route) {
            BalanceScreen(
                onNavigateToSend = { address ->
                    navController.navigate(Screen.Send.createRoute(address))
                }
            )
        }

        // Send Screen
        composable(
            route = Screen.Send.route,
            arguments = listOf(
                navArgument("address") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address") ?: ""
            android.util.Log.d("AppNavigation", "SendScreen composable - extracted address: '$address'")
            SendScreen(address = address)
        }
    }
}
