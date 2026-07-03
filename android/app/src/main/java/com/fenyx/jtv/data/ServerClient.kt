package com.fenyx.jtv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the optional JTV companion/proxy server (see `server/`).
 *
 * In "Server mode" the app does not log in with an OTP itself; instead it pulls the shared, centrally
 * refreshed [JioApiClient.AuthData] from the server's `GET /api/credentials` endpoint (protected by a
 * bearer access token). Everything downstream — stream resolution, DRM, token refresh — is unchanged,
 * because the app still receives the same AuthData it would have gotten from a direct OTP login.
 */
object ServerClient {
    private const val TAG = "ServerClient"

    /** Trims whitespace and any trailing slash so we can safely append paths. */
    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    /**
     * Fetches the shared credentials from the server.
     * @param baseUrl e.g. "http://192.168.1.10:8080" (or an https URL)
     * @param token   the server access token (JTV_SERVER_TOKEN)
     */
    suspend fun fetchCredentials(baseUrl: String, token: String): Result<JioApiClient.AuthData> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${normalizeBaseUrl(baseUrl)}/api/credentials")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "fetchCredentials failed: $code $err")
                    return@withContext Result.failure(
                        Exception(
                            when (code) {
                                401, 403 -> "Access denied — check the server access token."
                                404 -> "Server reachable but /api/credentials was not found."
                                else -> "Server returned HTTP $code."
                            }
                        )
                    )
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val ssoToken = json.optString("ssoToken", "")
                if (ssoToken.isEmpty()) {
                    return@withContext Result.failure(Exception("Server has no active login yet. Sign in on the server first."))
                }
                Result.success(
                    JioApiClient.AuthData(
                        ssoToken = ssoToken,
                        authToken = json.optString("authToken", ""),
                        crmid = json.optString("crmid", ""),
                        uniqueId = json.optString("uniqueId", ""),
                        deviceId = json.optString("deviceId", ""),
                        userId = json.optString("userId", "")
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception in fetchCredentials", e)
                Result.failure(Exception("Couldn't reach the server. Check the URL and network."))
            }
        }
}
