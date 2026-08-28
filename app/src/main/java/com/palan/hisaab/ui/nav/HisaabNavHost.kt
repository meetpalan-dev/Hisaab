package com.palan.hisaab.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.SettingsRepository
import com.palan.hisaab.ui.account.AccountScreen
import com.palan.hisaab.ui.home.HomeScreen
import com.palan.hisaab.ui.settings.SettingsScreen
import com.palan.hisaab.ui.splitbill.SplitBillScreen

object Routes {
    const val HOME = "home"
    const val ACCOUNT = "account/{accountId}"
    const val SETTINGS = "settings"
    const val SPLIT_BILL = "split_bill"
    fun account(id: Long) = "account/$id"
}

@Composable
fun HisaabNavHost(repository: HisaabRepository, settingsRepository: SettingsRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onOpenAccount = { id -> navController.navigate(Routes.account(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSplitBill = { navController.navigate(Routes.SPLIT_BILL) }
            )
        }
        composable(
            route = Routes.ACCOUNT,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L
            AccountScreen(
                repository = repository,
                settingsRepository = settingsRepository,
                accountId = accountId,
                onBack = { navController.popBackStack() },
                onOpenAccount = { id -> navController.navigate(Routes.account(id)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                settingsRepository = settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SPLIT_BILL) {
            SplitBillScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
