package com.hamfilm.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hamfilm.app.ui.screens.*

object Routes {
    const val HOME = "home"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CREATE = "create"
    const val JOIN = "join"
    const val ROOM = "room/{code}?password={password}&videoUrl={videoUrl}"
    const val ARCHIVE = "archive"
    const val MOVIE = "movie/{slug}"
    const val PLANS = "plans"
    const val PROFILE = "profile"
    const val TICKETS = "tickets"
    const val TICKET = "ticket/{id}"
    const val SETTINGS = "settings"

    fun room(code: String, password: String = "", videoUrl: String = "") =
        "room/$code?password=${android.net.Uri.encode(password)}&videoUrl=${android.net.Uri.encode(videoUrl)}"

    fun movie(slug: String) = "movie/$slug"
    fun ticket(id: String) = "ticket/$id"
}

@Composable
fun AppNavigation(startDestination: String = Routes.HOME) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInVertically(tween(380)) { it / 6 } + fadeIn(tween(380)) },
        exitTransition = { fadeOut(tween(280)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { slideOutVertically(tween(320)) { it / 6 } + fadeOut(tween(280)) }
    ) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.LOGIN) { LoginScreen(navController) }
        composable(Routes.REGISTER) { RegisterScreen(navController) }
        composable(Routes.CREATE) { CreateRoomScreen(navController) }
        composable(Routes.JOIN) { JoinRoomScreen(navController) }
        composable(
            Routes.ROOM,
            arguments = listOf(
                navArgument("code") { type = NavType.StringType },
                navArgument("password") { type = NavType.StringType; defaultValue = "" },
                navArgument("videoUrl") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            RoomScreen(
                nav = navController,
                roomCode = entry.arguments?.getString("code") ?: "",
                initialPassword = entry.arguments?.getString("password") ?: "",
                initialVideoUrl = entry.arguments?.getString("videoUrl") ?: ""
            )
        }
        composable(Routes.ARCHIVE) { ArchiveScreen(navController) }
        composable(
            Routes.MOVIE,
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { entry ->
            MovieDetailScreen(navController, entry.arguments?.getString("slug") ?: "")
        }
        composable(Routes.PLANS) { PlansScreen(navController) }
        composable(Routes.PROFILE) { ProfileScreen(navController) }
        composable(Routes.TICKETS) { TicketsScreen(navController) }
        composable(
            Routes.TICKET,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            TicketDetailScreen(navController, entry.arguments?.getString("id") ?: "")
        }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
    }
}
