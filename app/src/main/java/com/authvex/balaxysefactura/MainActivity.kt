package com.authvex.balaxysefactura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.auth.SessionManager
import com.authvex.balaxysefactura.core.network.AuthApi
import com.authvex.balaxysefactura.core.network.BudgetApi
import com.authvex.balaxysefactura.core.network.CfeApi
import com.authvex.balaxysefactura.core.network.ClientApi
import com.authvex.balaxysefactura.core.network.CollectionApi
import com.authvex.balaxysefactura.core.network.ProductApi
import com.authvex.balaxysefactura.core.network.CollectionReportApi
import com.authvex.balaxysefactura.core.network.ProductoDto
import com.authvex.balaxysefactura.core.network.ReportsApi
import com.authvex.balaxysefactura.core.network.RetrofitClient
import com.authvex.balaxysefactura.core.network.ScanApi
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.ClientRepository
import com.authvex.balaxysefactura.core.repository.CollectionReportRepository
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import com.authvex.balaxysefactura.core.repository.ProductRepository
import com.authvex.balaxysefactura.core.repository.ReportsRepository
import com.authvex.balaxysefactura.core.repository.ScanRepository
import com.authvex.balaxysefactura.ui.navigation.Screen
import com.authvex.balaxysefactura.ui.screens.client.ClientCreateScreen
import com.authvex.balaxysefactura.ui.screens.client.ClientCreateViewModel
import com.authvex.balaxysefactura.ui.screens.product.ProductCreateScreen
import com.authvex.balaxysefactura.ui.screens.product.ProductCreateViewModel
import com.authvex.balaxysefactura.ui.screens.scan.ScanScreen
import com.authvex.balaxysefactura.ui.screens.scan.ScanStatusScreen
import com.authvex.balaxysefactura.ui.screens.scan.ScanStatusViewModel
import com.authvex.balaxysefactura.ui.screens.scan.ScanViewModel
import com.authvex.balaxysefactura.ui.screens.scan.inbox.ScanInboxScreen
import com.authvex.balaxysefactura.ui.screens.scan.inbox.ScanInboxViewModel
import com.authvex.balaxysefactura.ui.screens.collection.detail.CollectionDetailScreen
import com.authvex.balaxysefactura.ui.screens.collection.detail.CollectionDetailViewModel
import com.authvex.balaxysefactura.ui.screens.collection.form.CollectionFormScreen
import com.authvex.balaxysefactura.ui.screens.collection.form.CollectionFormViewModel
import com.authvex.balaxysefactura.ui.screens.collection.list.CollectionListScreen
import com.authvex.balaxysefactura.ui.screens.collection.list.CollectionListViewModel
import com.authvex.balaxysefactura.ui.screens.reports.collections.receivables.ReceivablesReportScreen
import com.authvex.balaxysefactura.ui.screens.reports.collections.receivables.ReceivablesReportViewModel
import com.authvex.balaxysefactura.ui.screens.reports.collections.aging.AgingReportScreen
import com.authvex.balaxysefactura.ui.screens.reports.collections.aging.AgingReportViewModel
import com.authvex.balaxysefactura.ui.screens.reports.collections.collected.CollectedReportScreen
import com.authvex.balaxysefactura.ui.screens.reports.collections.collected.CollectedReportViewModel
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetDetailScreen
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetDetailViewModel
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormScreen
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormViewModel
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetListScreen
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetListViewModel
import com.authvex.balaxysefactura.ui.screens.cfe.detail.CfeDetailScreen
import com.authvex.balaxysefactura.ui.screens.cfe.detail.CfeDetailViewModel
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListScreen
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListViewModel
import com.authvex.balaxysefactura.ui.screens.devtools.DevToolsScreen
import com.authvex.balaxysefactura.ui.screens.devtools.DevToolsViewModel
import com.authvex.balaxysefactura.ui.screens.emission.EmissionScreen
import com.authvex.balaxysefactura.ui.screens.emission.EmissionViewModel
import com.authvex.balaxysefactura.ui.screens.home.HomeScreen
import com.authvex.balaxysefactura.ui.screens.login.LoginScreen
import com.authvex.balaxysefactura.ui.screens.login.LoginViewModel
import com.authvex.balaxysefactura.ui.screens.reports.ReportsScreen
import com.authvex.balaxysefactura.ui.screens.reports.ReportsViewModel
import com.authvex.balaxysefactura.ui.theme.BalaxysEfacturaTheme
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
        val budgetApi = retrofitClient.create(BudgetApi::class.java)
        val collectionApi = retrofitClient.create(CollectionApi::class.java)
        val collectionReportApi = retrofitClient.create(CollectionReportApi::class.java)
        val scanApi = retrofitClient.create(ScanApi::class.java)
        val clientApi = retrofitClient.create(ClientApi::class.java)
        val productApi = retrofitClient.create(ProductApi::class.java)

        val cfeRepository = CfeRepository(cfeApi)
        val reportsRepository = ReportsRepository(reportsApi)
        val budgetRepository = BudgetRepository(budgetApi)
        val collectionRepository = CollectionRepository(collectionApi)
        val collectionReportRepository = CollectionReportRepository(collectionReportApi, collectionApi)
        val scanRepository = ScanRepository(scanApi)
        val clientRepository = ClientRepository(clientApi)
        val productRepository = ProductRepository(productApi)

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
                            },
                            onViewBudgets = {
                                navController.navigate(Screen.Budgets.route)
                            },
                            onViewCollections = {
                                navController.navigate(Screen.Collections.route)
                            },
                            onOpenScan = {
                                navController.navigate(Screen.Scan.route)
                            }
                        )
                    }
                    composable(Screen.Budgets.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return BudgetListViewModel(budgetRepository, cfeRepository) as T
                            }
                        }
                        val listViewModel: BudgetListViewModel = viewModel(factory = factory)
                        BudgetListScreen(
                            viewModel = listViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { budgetId ->
                                navController.navigate(Screen.BudgetDetail.createRoute(budgetId))
                            },
                            onNavigateToCreate = {
                                navController.navigate(Screen.BudgetForm.route)
                            }
                        )
                    }
                    composable(
                        route = Screen.BudgetDetail.route,
                        arguments = listOf(navArgument("budgetId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val budgetId = backStackEntry.arguments?.getLong("budgetId") ?: 0L
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return BudgetDetailViewModel(budgetRepository, cfeRepository, budgetId) as T
                            }
                        }
                        val detailViewModel: BudgetDetailViewModel = viewModel(factory = factory)
                        BudgetDetailScreen(
                            viewModel = detailViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEdit = { editId ->
                                navController.navigate(Screen.BudgetEdit.createRoute(editId))
                            }
                        )
                    }
                    composable(Screen.BudgetForm.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return BudgetFormViewModel(budgetRepository, cfeRepository) as T
                            }
                        }
                        val formViewModel: BudgetFormViewModel = viewModel(factory = factory)
                        BudgetFormScreen(
                            viewModel = formViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onBudgetSaved = { createdId ->
                                navController.navigate(Screen.BudgetDetail.createRoute(createdId)) {
                                    popUpTo(Screen.Budgets.route)
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.BudgetEdit.route,
                        arguments = listOf(navArgument("budgetId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val editBudgetId = backStackEntry.arguments?.getLong("budgetId") ?: 0L
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return BudgetFormViewModel(budgetRepository, cfeRepository) as T
                            }
                        }
                        val formViewModel: BudgetFormViewModel = viewModel(factory = factory)
                        BudgetFormScreen(
                            viewModel = formViewModel,
                            budgetIdToEdit = editBudgetId,
                            onNavigateBack = { navController.popBackStack() },
                            onBudgetSaved = { savedId ->
                                navController.navigate(Screen.BudgetDetail.createRoute(savedId)) {
                                    popUpTo(Screen.Budgets.route)
                                }
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
                        arguments = listOf(navArgument("documentoId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val documentoId = backStackEntry.arguments?.getLong("documentoId") ?: 0L
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CfeDetailViewModel(cfeRepository, documentoId, collectionRepository) as T
                            }
                        }
                        val detailViewModel: CfeDetailViewModel = viewModel(factory = factory)
                        CfeDetailScreen(
                            viewModel = detailViewModel,
                            onBack = { navController.popBackStack() },
                            onCollectInvoice = { facturaId ->
                                navController.navigate(Screen.CollectionForm.createRoute(facturaIdInitial = facturaId))
                            }
                        )
                    }
                    composable(Screen.Collections.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CollectionListViewModel(collectionRepository) as T
                            }
                        }
                        val listViewModel: CollectionListViewModel = viewModel(factory = factory)
                        CollectionListScreen(
                            viewModel = listViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.CollectionDetail.createRoute(id))
                            },
                            onNavigateToCreate = {
                                navController.navigate(Screen.CollectionForm.createRoute())
                            }
                        )
                    }
                    composable(
                        route = Screen.CollectionDetail.route,
                        arguments = listOf(navArgument("collectionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val collectionId = backStackEntry.arguments?.getLong("collectionId") ?: 0L
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CollectionDetailViewModel(cfeRepository, collectionId, collectionRepository) as T
                            }
                        }
                        val detailViewModel: CollectionDetailViewModel = viewModel(factory = factory)
                        CollectionDetailScreen(
                            viewModel = detailViewModel,
                            cfeRepository = cfeRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.CollectionForm.route,
                        arguments = listOf(navArgument("facturaIdInitial") { type = NavType.LongType; defaultValue = 0L })
                    ) { backStackEntry ->
                        val initFacturaId = backStackEntry.arguments?.getLong("facturaIdInitial")?.takeIf { it > 0 }
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CollectionFormViewModel(collectionRepository, cfeRepository, initFacturaId) as T
                            }
                        }
                        val formViewModel: CollectionFormViewModel = viewModel(factory = factory)
                        CollectionFormScreen(
                            viewModel = formViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onCollectionSaved = { createdId ->
                                navController.navigate(Screen.CollectionDetail.createRoute(createdId)) {
                                    popUpTo(Screen.Collections.route)
                                }
                            },
                            onOpenCreateClient = { navController.navigate(Screen.ClientCreate.route) }
                        )
                    }
                    composable(Screen.Emission.route) { backStackEntry ->
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return EmissionViewModel(cfeRepository) as T
                            }
                        }
                        val emissionViewModel: EmissionViewModel = viewModel(factory = factory)

                        val createdProdId = backStackEntry.savedStateHandle.get<Int>("created_product_id")
                        LaunchedEffect(createdProdId) {
                            if (createdProdId != null && createdProdId > 0) {
                                backStackEntry.savedStateHandle.remove<Int>("created_product_id")
                                productRepository.getProductById(createdProdId.toLong()).onSuccess { product ->
                                    emissionViewModel.startLineConfiguration(product)
                                }
                            }
                        }

                        EmissionScreen(
                            viewModel = emissionViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.CfeDetail.createRoute(id)) {
                                    popUpTo(Screen.Home.route)
                                }
                            },
                            onOpenCreateClient = { navController.navigate(Screen.ClientCreate.route) },
                            onOpenCreateProduct = { navController.navigate(Screen.ProductCreate.route) }
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
                            onBack = { navController.popBackStack() },
                            onNavigateToReceivablesReport = { navController.navigate(Screen.ReceivablesReport.route) },
                            onNavigateToAgingReport = { navController.navigate(Screen.AgingReport.route) },
                            onNavigateToCollectedReport = { navController.navigate(Screen.CollectedReport.route) }
                        )
                    }
                    composable(Screen.ReceivablesReport.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ReceivablesReportViewModel(collectionReportRepository, cfeRepository) as T
                            }
                        }
                        val recViewModel: ReceivablesReportViewModel = viewModel(factory = factory)
                        ReceivablesReportScreen(
                            viewModel = recViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.AgingReport.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return AgingReportViewModel(collectionReportRepository, cfeRepository) as T
                            }
                        }
                        val agingViewModel: AgingReportViewModel = viewModel(factory = factory)
                        AgingReportScreen(
                            viewModel = agingViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.CollectedReport.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return CollectedReportViewModel(collectionReportRepository, cfeRepository) as T
                            }
                        }
                        val colViewModel: CollectedReportViewModel = viewModel(factory = factory)
                        CollectedReportScreen(
                            viewModel = colViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToCollectionDetail = { id ->
                                navController.navigate(Screen.CollectionDetail.createRoute(id))
                            }
                        )
                    }
                    composable(Screen.Scan.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ScanViewModel(scanRepository) as T
                            }
                        }
                        val scanViewModel: ScanViewModel = viewModel(factory = factory)
                        ScanScreen(
                            viewModel = scanViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToInbox = { navController.navigate(Screen.ScanInbox.route) },
                            onUploadSuccess = { docId ->
                                navController.navigate(Screen.ScanStatus.createRoute(docId)) {
                                    popUpTo(Screen.Home.route)
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.ScanStatus.route,
                        arguments = listOf(navArgument("documentId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val docId = backStackEntry.arguments?.getLong("documentId") ?: 0L
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ScanStatusViewModel(scanRepository, docId) as T
                            }
                        }
                        val statusViewModel: ScanStatusViewModel = viewModel(factory = factory)
                        ScanStatusScreen(
                            viewModel = statusViewModel,
                            onNavigateHome = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNavigateToInbox = { navController.navigate(Screen.ScanInbox.route) }
                        )
                    }
                    composable(Screen.ScanInbox.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ScanInboxViewModel(scanRepository) as T
                            }
                        }
                        val inboxViewModel: ScanInboxViewModel = viewModel(factory = factory)
                        ScanInboxScreen(
                            viewModel = inboxViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToStatus = { docId ->
                                navController.navigate(Screen.ScanStatus.createRoute(docId))
                            }
                        )
                    }
                    composable(Screen.ClientCreate.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ClientCreateViewModel(clientRepository) as T
                            }
                        }
                        val clientViewModel: ClientCreateViewModel = viewModel(factory = factory)
                        ClientCreateScreen(
                            viewModel = clientViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onClientCreated = { _, _ ->
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(Screen.ProductCreate.route) {
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ProductCreateViewModel(productRepository, cfeRepository) as T
                            }
                        }
                        val productViewModel: ProductCreateViewModel = viewModel(factory = factory)
                        ProductCreateScreen(
                            viewModel = productViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onProductCreated = { createdProduct ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("created_product_id", createdProduct.id)
                                navController.popBackStack()
                            }
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
