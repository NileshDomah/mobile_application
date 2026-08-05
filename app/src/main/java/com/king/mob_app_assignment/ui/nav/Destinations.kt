package com.king.mob_app_assignment.ui.nav

object Destinations {
    const val ONBOARDING = "onboarding"

    const val LOGIN = "login"
    const val REGISTER = "register"

    const val HOME = "home"
    const val CAPTURE = "capture"
    const val REPORTS = "reports"
    const val REPORTS_MAP = "reports_map"
    const val MY_REPORTS = "my_reports"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val PERMISSION_DASHBOARD = "permission_dashboard"
    const val ADMIN_REPORTS = "admin_reports"

    const val REPORT_DETAIL_ROUTE = "report_detail/{reportId}"
    fun reportDetail(reportId: String) = "report_detail/$reportId"

    const val AUTH_GRAPH = "auth_graph"
    const val MAIN_GRAPH = "main_graph"
    const val ONBOARDING_GRAPH = "onboarding_graph"
}
