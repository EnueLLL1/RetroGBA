package com.retro.gba.security

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor de segurança de rede que audita e higieniza requisições HTTP,
 * garantindo que credenciais sensíveis (senhas, tokens de sessão, chaves de API)
 * sejam mascaradas antes de qualquer emissão de log.
 */
class NetworkSecurityInterceptor(
    private val enableLogging: Boolean = true
) : Interceptor {

    companion object {
        private const val TAG = "RetroGbaNetwork"
        private val SENSITIVE_QUERY_PARAMS = listOf("p", "t", "y", "password", "token", "key")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Adiciona User-Agent de navegador/app para evitar bloqueio HTTP 403 do Firewall / Cloudflare do RetroAchievements
        val request = originalRequest.newBuilder()
            .header("User-Agent", "RetroGBA/1.0.0 (Android; RetroAchievements Client)")
            .header("Accept", "application/json, text/plain, */*")
            .build()

        val originalUrl = request.url

        if (enableLogging) {
            val sanitizedUrl = sanitizeUrl(originalUrl.toString())
            Log.d(TAG, "HTTP ${request.method} -> $sanitizedUrl")
        }

        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "Falha na requisição HTTP: ${e.javaClass.simpleName} - ${e.message}")
            throw e
        }
        val tookMs = (System.nanoTime() - startNs) / 1e6

        if (enableLogging) {
            Log.d(TAG, "HTTP ${response.code} ${response.message} (${tookMs.toLong()}ms)")
        }

        return response
    }

    /**
     * Mascara valores de query parameters confidenciais como senhas e tokens.
     */
    fun sanitizeUrl(urlString: String): String {
        var result = urlString
        for (param in SENSITIVE_QUERY_PARAMS) {
            // Regex para capturar param=valor até o próximo & ou fim da string
            val regex = Regex("([?&]$param=)([^&]+)")
            result = regex.replace(result, "$1***REDACTED***")
        }
        return result
    }
}
