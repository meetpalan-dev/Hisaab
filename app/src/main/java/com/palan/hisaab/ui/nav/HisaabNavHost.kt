package com.palan.hisaab.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.ui.account.AccountScreen
import com.palan.hisaab.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val ACCOUNT = "account/{accountId}"
    fun account(id: Long) = "account/$id"
}

@Composable
fun HisaabNavHost(repository: HisaabRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onOpenAccount = { id -> navController.navigate(Routes.account(id)) }
            )
        }
        composable(
            route = Routes.ACCOUNT,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L
            AccountScreen(
                repository = repository,
                accountId = accountId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
