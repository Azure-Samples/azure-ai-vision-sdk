package com.azure.android.ai.vision.face.deviceattestation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Shared low-level HTTP plumbing for the auth flow's five endpoints.
 *
 * Each endpoint lives in its own file ([AttestationChallengeApi],
 * [AttestationVerifyApi], [AttestationRegisterApi], [SessionTokenApi],
 * [LivenessDigestApi]); they all funnel through [postJson] / [postEmpty]
 * here so connection setup, timeouts, and error decoding sit in one place.
 */
internal object AuthHttpClient {

    const val SYSTEM = "android"

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 90_000

    /** Raw HTTP response: status code + body (success body on 2xx, error body otherwise). */
    data class HttpResponse(val code: Int, val body: String?)

    /**
     * Builds an endpoint [URL] from [base] plus the ordered query [params],
     * percent-encoding every name and value.
     *
     * Encoding is essential, not cosmetic: values such as the App Link `s`
     * session id reach the client already URL-decoded (via
     * `Uri.getQueryParameter`), so splicing one in raw would let a crafted
     * value inject extra `&name=value` pairs into the query string ("query
     * smuggling") — e.g. a second `cid` that a first-value-wins server would
     * honor over the real one. Encoding folds any `&`, `=`, `#`, `?` inside a
     * value back into a single opaque parameter.
     */
    fun buildUrl(base: String, vararg params: Pair<String, String>): URL {
        val query = params.joinToString("&") { (name, value) ->
            "${encodeQueryComponent(name)}=${encodeQueryComponent(value)}"
        }
        return URL("$base?$query")
    }

    private fun encodeQueryComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    /** POST [url] with no request body. */
    suspend fun postEmpty(url: URL): HttpResponse = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
            }
            readResponse(connection)
        } catch (e: Exception) {
            HttpResponse(-1, e.message)
        } finally {
            connection?.disconnect()
        }
    }

    /** POST [url] with [body] as `application/json`. */
    suspend fun postJson(url: URL, body: JSONObject): HttpResponse = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            readResponse(connection)
        } catch (e: Exception) {
            HttpResponse(-1, e.message)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readResponse(connection: HttpsURLConnection): HttpResponse {
        val code = connection.responseCode
        val body = if (code == HttpsURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        }
        return HttpResponse(code, body)
    }
}
