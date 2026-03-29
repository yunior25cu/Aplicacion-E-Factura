package com.authvex.balaxysefactura.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object DevTools : Screen("devtools")
    object CfeList : Screen("cfe_list")
    object CfeDetail : Screen("cfe_detail/{documentoId}") {
        fun createRoute(documentoId: Int) = "cfe_detail/$documentoId"
    }
    object Emission : Screen("emission")
}
