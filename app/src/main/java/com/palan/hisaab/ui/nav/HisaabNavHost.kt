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
import com.palan.hisaab.ui.split.SplitExpenseScreen

object Routes {
    const val HOME = "home"
    const val ACCOUNT = "account/{accountId}"
    const val SETTINGS = "settings"
    const val SPLIT = "split"
    fun account(id: Long) = "account/$id"
}

@Composable
fun HisaabNavHost(repository: HisaabRepository, settingsRepository: SettingsRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                settingsRepository = settingsRepository,
                onOpenAccount = { id -> navController.navigate(Routes.account(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSplit = { navController.navigate(Routes.SPLIT) }
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
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SPLIT) {
            SplitExpenseScreen(
                repository = repository,
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
