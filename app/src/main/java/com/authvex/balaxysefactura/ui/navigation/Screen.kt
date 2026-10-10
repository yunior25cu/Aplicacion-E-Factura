package com.authvex.balaxysefactura.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object DevTools : Screen("devtools")
    object CfeList : Screen("cfe_list")
    object CfeDetail : Screen("cfe_detail/{documentoId}") {
        fun createRoute(documentoId: Long) = "cfe_detail/$documentoId"
    }
    object Emission : Screen("emission")
    object Reports : Screen("reports")
    object Budgets : Screen("budgets")
    object BudgetDetail : Screen("budget_detail/{budgetId}") {
        fun createRoute(budgetId: Long) = "budget_detail/$budgetId"
    }
    object BudgetForm : Screen("budget_form")
    object BudgetEdit : Screen("budget_edit/{budgetId}") {
        fun createRoute(budgetId: Long) = "budget_edit/$budgetId"
    }
    object Collections : Screen("collections")
    object CollectionDetail : Screen("collection_detail/{collectionId}") {
        fun createRoute(collectionId: Long) = "collection_detail/$collectionId"
    }
    object CollectionForm : Screen("collection_form?facturaIdInitial={facturaIdInitial}") {
        fun createRoute(facturaIdInitial: Long? = null) = if (facturaIdInitial != null) "collection_form?facturaIdInitial=$facturaIdInitial" else "collection_form"
    }
    object ReceivablesReport : Screen("reports_receivables")
    object AgingReport : Screen("reports_aging")
    object CollectedReport : Screen("reports_collected")
}
