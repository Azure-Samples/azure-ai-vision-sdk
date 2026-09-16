package com.azure.android.ai.vision.face.deviceattestation

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Lazy, process-wide cache for the Play Integrity standard token provider.
 *
 * Preparing a provider is the expensive part of the Play Integrity API
 * (hundreds of ms to seconds on cold start). Reusing the provider lets every
 * subsequent token request stay in the 100-300ms range. We cache one provider
 * per process and refresh it transparently if Play returns
 * INTEGRITY_TOKEN_PROVIDER_INVALID (which happens after the provider's TTL).
 */
internal object PlayIntegrityTokenProvider {

    private var integrityManager: StandardIntegrityManager? = null
    private var tokenProvider: StandardIntegrityTokenProvider? = null
    private val providerMutex = Mutex()

    sealed class TokenResult {
        data class Success(val token: String) : TokenResult()
        data class Exception(val exception: Throwable) : TokenResult()
    }

    /**
     * Pre-warms the provider in the background. Failures are swallowed because
     * warmup is best-effort — the real attempt happens at [requestToken] time.
     */
    suspend fun warmup(context: Context) {
        try {
            ensureProvider(context)
        } catch (e: kotlin.Exception) {
            println("Warning: Integrity provider warmup failed: ${e.message}")
        }
    }

    /**
     * Requests a Play Integrity token bound to [challengeHash].
     *
     * If Play returns INTEGRITY_TOKEN_PROVIDER_INVALID (provider expired), the
     * cached provider is dropped and the call is retried exactly once with a
     * fresh provider.
     */
    suspend fun requestToken(
        context: Context,
        challengeHash: String,
        isRetry: Boolean = false
    ): TokenResult {
        return try {
            val provider = ensureProvider(context)
            val response = provider.request(
                StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                    .setRequestHash(challengeHash)
                    .build()
            ).await()
            TokenResult.Success(response.token())
        } catch (e: kotlin.Exception) {
            if (!isRetry && e.message?.contains("INTEGRITY_TOKEN_PROVIDER_INVALID") == true) {
                providerMutex.withLock { tokenProvider = null }
                requestToken(context, challengeHash, isRetry = true)
            } else {
                TokenResult.Exception(e)
            }
        }
    }

    /**
     * Lazily creates (or returns the cached) integrity manager + token
     * provider. The double-checked pattern under the mutex prevents two
     * coroutines from both paying the prepareIntegrityToken cost.
     */
    private suspend fun ensureProvider(context: Context): StandardIntegrityTokenProvider {
        tokenProvider?.let { return it }

        return providerMutex.withLock {
            tokenProvider?.let { return it }

            if (integrityManager == null) {
                integrityManager = IntegrityManagerFactory.createStandard(context.applicationContext)
            }
            val provider = integrityManager!!.prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(DeviceAttestationConfig.cloudProject)
                    .build()
            ).await()
            tokenProvider = provider
            provider
        }
    }
}
