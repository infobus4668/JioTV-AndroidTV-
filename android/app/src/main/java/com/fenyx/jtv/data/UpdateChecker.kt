package com.fenyx.jtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight in-app update check against the GitHub releases API.
 *
 * Sideloaded APKs get no Play-Store update prompts, so this is the only way users learn a new
 * version exists. Rate-limited by the caller via [SettingsManager.lastUpdateCheckFlow] (checked at
 * most once per 24h). Uses HttpURLConnection + org.json — no new dependencies, matching the rest
 * of the data layer.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_API = "https://api.github.com/repos/infobus4668/JioTV-AndroidTV-/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/infobus4668/JioTV-AndroidTV-/releases/latest"

    data class Release(val version: String, val url: String)

    /** Fetches the latest published release. Network on [Dispatchers.IO]; never throws. */
    suspend fun latestRelease(): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "JTV-App") // GitHub API requires a UA
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                error("HTTP $code")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            val tag = json.optString("tag_name", "").trim()
            if (tag.isEmpty()) error("No tag_name in response")
            val url = json.optString("html_url", RELEASES_PAGE).ifBlank { RELEASES_PAGE }
            Release(version = tag.removePrefix("v").removePrefix("V"), url = url)
        }.onFailure {
            android.util.Log.d(TAG, "Update check failed: ${it.message}")
        }
    }

    /**
     * Compares "1.5.2-mod.2"-style versions. Numeric dot-prefix is compared segment by segment
     * (missing segments = 0, so 1.5 < 1.5.1); a differing pre-release suffix ("mod.2") never makes
     * a version greater — only the numeric core decides.
     */
    fun isNewer(remote: String, local: String): Boolean {
        // "1.5.2-mod.2" → core "1.5.2"; also tolerate a leading "v" (GitHub tags are usually "v1.2.3").
        fun core(v: String): List<Int> =
            v.trim().removePrefix("v").removePrefix("V")
                .takeWhile { it.isDigit() || it == '.' }
                .split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        val r = core(remote)
        val l = core(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
