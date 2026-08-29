package com.fenyx.jtv

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Login : NavKey
@Serializable data object Search : NavKey

/**
 * @param cu* Optional catch-up intent: when [cuStartMs] is set, the player starts this past
 *   programme (VOD replay) instead of the live feed. Carries the raw EPG fields so the player
 *   can resolve the replay even before its own EPG data loads.
 */
@Serializable data class Player(
    val channelIndex: Int,
    val group: String? = null,
    val cuTitle: String? = null,
    val cuStartMs: Long? = null,
    val cuEndMs: Long? = null,
    val cuSrno: String? = null,
    val cuShowId: String? = null,
    val cuShowtime: String? = null
) : NavKey
