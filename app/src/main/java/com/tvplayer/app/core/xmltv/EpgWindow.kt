package com.tvplayer.app.core.xmltv

/** Ventana temporal del spec (§F6): [now−12h, now+60h]. */
object EpgWindow {
    val PAST_HOURS = 12L
    val FORECAST_HOURS = 60L

    fun inWindow(startMillis: Long, nowMillis: Long, pastHours: Long = PAST_HOURS, forecastHours: Long = FORECAST_HOURS): Boolean =
        startMillis >= nowMillis - pastHours * 3_600_000L && startMillis <= nowMillis + forecastHours * 3_600_000L
}
