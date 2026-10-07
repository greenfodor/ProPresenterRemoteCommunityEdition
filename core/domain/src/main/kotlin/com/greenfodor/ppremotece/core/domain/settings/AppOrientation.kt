package com.greenfodor.ppremotece.core.domain.settings

/** How the app is oriented: as the system decides, upright portrait, or either landscape direction. */
enum class AppOrientation {
    SYSTEM,
    PORTRAIT,
    LANDSCAPE;

    companion object {
        val Default = SYSTEM
    }
}
