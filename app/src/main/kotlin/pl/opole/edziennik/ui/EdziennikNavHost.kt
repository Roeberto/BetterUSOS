package pl.opole.edziennik.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.dashboard.DashboardScreen
import pl.opole.edziennik.ui.grades.GradesScreen
import pl.opole.edziennik.ui.group.GroupDetailScreen
import pl.opole.edziennik.ui.login.LoginScreen
import pl.opole.edziennik.ui.notifications.NotificationsScreen
import pl.opole.edziennik.ui.payments.PaymentsScreen
import pl.opole.edziennik.ui.person.PersonDetailScreen
import pl.opole.edziennik.ui.plan.PlanScreen
import pl.opole.edziennik.viewmodel.AuthViewModel
import java.io.File

@Composable
fun EdziennikNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    apiClient: UsosApiClient,
    cacheDir: File,
) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(authViewModel, navController) }
        composable("dashboard") { DashboardScreen(apiClient, cacheDir, navController, authViewModel) }
        composable("plan") { PlanScreen(apiClient, cacheDir, navController) }
        composable("grades") { GradesScreen(apiClient, cacheDir, navController) }
        composable("notifications") { NotificationsScreen(cacheDir, navController) }
        composable("payments") { PaymentsScreen(apiClient, cacheDir, navController) }
        composable(
            "group/{unitId}/{groupNumber}",
            arguments = listOf(
                navArgument("unitId") { type = NavType.IntType },
                navArgument("groupNumber") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val unitId = backStackEntry.arguments?.getInt("unitId") ?: return@composable
            val groupNumber = backStackEntry.arguments?.getInt("groupNumber") ?: return@composable
            GroupDetailScreen(apiClient, cacheDir, navController, unitId, groupNumber)
        }
        composable(
            "person/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: return@composable
            PersonDetailScreen(apiClient, cacheDir, navController, userId)
        }
    }
}
