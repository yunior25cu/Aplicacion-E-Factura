package com.authvex.balaxysefactura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.auth.SessionManager
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.ReportsRepository
import com.authvex.balaxysefactura.ui.navigation.Screen
import com.authvex.balaxysefactura.ui.screens.cfe.detail.CfeDetailScreen
import com.authvex.balaxysefactura.ui.screens.cfe.detail.CfeDetailViewModel
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListScreen
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListViewModel
import com.authvex.balaxysefactura.ui.screens.devtools.DevToolsScreen
import com.authvex.balaxysefactura.ui.screens.devtools.DevToolsViewModel
import com.authvex.balaxysefactura.ui.screens.home.HomeScreen
import com.authvex.balaxysefactura.ui.screens.login.LoginScreen
import com.authvex.balaxysefactura.ui.screens.login.LoginViewModel
import com.authvex.balaxysefactura.ui.screens.emission.EmissionScreen
import com.authvex.balaxysefactura.ui.screens.emission.EmissionViewModel
import com.authvex.balaxysefactura.ui.screens.reports.ReportsScreen
import com.authvex.balaxysefactura.ui.screens.reports.ReportsViewModel
import com.authvex.balaxysefactura.ui.theme.BalaxysEfacturaTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val authPreferences = AuthPreferences(this)
        val retrofitClient = RetrofitClient(authPreferences)
        val authApi = retrofitClient.create(AuthApi::class.java)
        val cfeApi = retrofitClient.create(CfeApi::class.java)
        val reportsApi = retrofitClient.create(ReportsApi::class.java)
        
        val cfeRepository = CfeRepository(cfeApi)
        val reportsRepository = ReportsRepository(reportsApi)

        val startDestination = runBlocking {
            if (authPreferences.getAuthTokenSync() != null) Screen.Home.route else Screen.Login.route
        }

        enableEdgeToEdge()
        setContent {
            BalaxysEfacturaTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    SessionManager.sessionExpiredEvent.collectLatest {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                
                NavHost(navController = navController, startDestination = startDestination) {
                    composable(Screen.Login.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return LoginViewModel(authApi, authPreferences) as T
                            }
                        }
                        val loginViewModel: LoginViewModel = viewModel(factory = factory)
                        
                        LoginScreen(
                            viewModel = loginViewModel,
                            onLoginSuccess = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onLogout = {
                                runBlocking { authPreferences.clearAuthData() }
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onOpenDevTools = {
                                if (BuildConfig.DEBUG) {
                                    navController.navigate(Screen.DevTools.route)
                                }
                            },
                            onViewCfeList = {
                                navController.navigate(Screen.CfeList.route)
                            },
                            onEmitDocument = {
                                navController.navigate(Screen.Emission.route)
                            },
                            onViewReports = {
                                navController.navigate(Screen.Reports.route)
                            }
                        )
                    }
                    composable(Screen.CfeList.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CfeListViewModel(cfeRepository) as T
                            }
                        }
                        val listViewModel: CfeListViewModel = viewModel(factory = factory)
                        CfeListScreen(
                            viewModel = listViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.CfeDetail.createRoute(id))
                            }
                        )
                    }
                    composable(
                        route = Screen.CfeDetail.route,
                        arguments = listOf(navArgument("documentoId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val documentoId = backStackEntry.arguments?.getInt("documentoId") ?: 0
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CfeDetailViewModel(cfeRepository, documentoId) as T
                            }
                        }
                        val detailViewModel: CfeDetailViewModel = viewModel(factory = factory)
                        CfeDetailScreen(
                            viewModel = detailViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Emission.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return EmissionViewModel(cfeRepository) as T
                            }
                        }
                        val emissionViewModel: EmissionViewModel = viewModel(factory = factory)
                        EmissionScreen(
                            viewModel = emissionViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.CfeDetail.createRoute(id)) {
                                    popUpTo(Screen.Home.route)
                                }
                            }
                        )
                    }
                    composable(Screen.Reports.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ReportsViewModel(reportsRepository) as T
                            }
                        }
                        val reportsViewModel: ReportsViewModel = viewModel(factory = factory)
                        ReportsScreen(
                            viewModel = reportsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        composable(Screen.DevTools.route) {
                            val factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return DevToolsViewModel(authApi, authPreferences) as T
                                }
                            }
                            val devViewModel: DevToolsViewModel = viewModel(factory = factory)
                            DevToolsScreen(
                                viewModel = devViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
