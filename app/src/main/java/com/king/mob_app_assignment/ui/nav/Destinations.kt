package com.king.mob_app_assignment.ui.nav

object Destinations {
    const val ONBOARDING = "onboarding"
    const val PRIVACY_NOTICE = "privacy_notice"

    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_CREDENTIALS = "forgot_credentials"

    const val HOME = "home"
    // viaShake distinguishes how CaptureScreen was reached: true when launched by the shake
    // gesture (jumps straight to the camera, unchanged from before multi-photo support), false/
    // omitted for every other entry point - the "Report Issue" button, a notification tap, etc -
    // which land on the form-first flow instead (see CaptureViewModel.startFormFirstFlow()).
    // CAPTURE is the templated route registered with NavHost's composable(); capture(viaShake) is
    // the only way any call site should actually navigate to it.
    const val CAPTURE = "capture?viaShake={viaShake}"
    fun capture(viaShake: Boolean = false) = "capture?viaShake=$viaShake"
    const val MY_REPORTS = "my_reports"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val PERMISSION_DASHBOARD = "permission_dashboard"
    const val ADMIN_REPORTS = "admin_reports"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val CHAT = "chat"
    const val CONTACT_SUPPORT = "contact_support"
    const val ADMIN_SUPPORT_MESSAGES = "admin_support_messages"
    const val ADMIN_INCIDENTS = "admin_incidents"
    const val ADMIN_RISK_AREAS = "admin_risk_areas"
    const val PROMOTE_ADMIN = "promote_admin"
    const val MAP = "map"
    const val NOTIFICATION_SETTINGS = "notification_settings"

    const val SUPPORT_THREAD_ROUTE = "support_thread/{ticketId}?isAdmin={isAdmin}"
    fun supportThread(ticketId: String, isAdmin: Boolean) = "support_thread/$ticketId?isAdmin=$isAdmin"

    const val REPORT_DETAIL_ROUTE = "report_detail/{reportId}"
    fun reportDetail(reportId: String) = "report_detail/$reportId"

    const val INCIDENT_DETAIL_ROUTE = "incident_detail/{incidentId}"
    fun incidentDetail(incidentId: String) = "incident_detail/$incidentId"

    const val RISK_AREA_DETAIL_ROUTE = "risk_area_detail/{riskScoreId}"
    fun riskAreaDetail(riskScoreId: String) = "risk_area_detail/$riskScoreId"

    const val AUTH_GRAPH = "auth_graph"
    const val MAIN_GRAPH = "main_graph"
    const val ONBOARDING_GRAPH = "onboarding_graph"
    const val PRIVACY_NOTICE_GRAPH = "privacy_notice_graph"
}
