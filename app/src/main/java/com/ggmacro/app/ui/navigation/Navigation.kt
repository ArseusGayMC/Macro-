package com.ggmacro.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ggmacro.app.ui.screens.HomeScreen
import com.ggmacro.app.ui.screens.MacroDetailScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object MacroDetail : Screen("macro_detail/{macroId}") {
        fun createRoute(macroId: Long = -1L) = "macro_detail/$macroId"
    }
}

@Composable
fun GGMacroNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { macroId ->
                    navController.navigate(Screen.MacroDetail.createRoute(macroId))
                },
                onCreateNew = {
                    navController.navigate(Screen.MacroDetail.createRoute(-1L))
                }
            )
        }

        composable(
            route = Screen.MacroDetail.route,
            arguments = listOf(
                navArgument("macroId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            MacroDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
