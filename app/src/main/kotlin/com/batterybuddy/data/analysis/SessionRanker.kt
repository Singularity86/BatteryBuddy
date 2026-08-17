package com.batterybuddy.data.analysis

import com.batterybuddy.data.model.ChargeSession

/**
 * Where a just-finished charge sits among recent ones.
 *
 * Only returns something when a charge is genuinely unusual. A comparison
 * attached to every ordinary charge is noise, and a running commentary on how
 * bad each charge was is exactly the anxiety trap this app is trying to avoid.
 */
data class SessionStanding(
    /** 1 = hottest charge in the window. */
    val temperatureRank: Int,
    val comparedAgainst: Int
) {
    val isNotable: Boolean get() = temperatureRank <= NOTABLE_RANK

    fun describe(): String? {
        if (!isNotable) return null
        val ordinal = when (temperatureRank) {
            1    -> "hottest"
            2    -> "2nd-hottest"
            else -> "3rd-hottest"
        }
        return "Your $ordinal charge of the last $comparedAgainst"
    }

    private companion object {
        const val NOTABLE_RANK = 3
    }
}

object SessionRanker {

    /** Below this there isn't enough history for a ranking to mean anything. */
    const val MIN_SESSIONS_FOR_RANKING = 5

    fun rankByTemperature(session: ChargeSession, recent: List<ChargeSession>): SessionStanding? {
        val peak = session.peakTempTenthsCelsius ?: return null
        val comparable = recent.filter { !it.isOpen && it.peakTempTenthsCelsius != null }
        if (comparable.size < MIN_SESSIONS_FOR_RANKING) return null

        // Rank among all comparable sessions, counting this one exactly once.
        val hotter = comparable.count { it.id != session.id && it.peakTempTenthsCelsius!! > peak }
        return SessionStanding(
            temperatureRank = hotter + 1,
            comparedAgainst = comparable.count { it.id != session.id } + 1
        )
    }
}
