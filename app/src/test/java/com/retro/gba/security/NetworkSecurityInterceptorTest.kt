package com.retro.gba.security

import org.junit.Assert.*
import org.junit.Test

class NetworkSecurityInterceptorTest {

    private val interceptor = NetworkSecurityInterceptor(enableLogging = false)

    @Test
    fun sanitizeUrl_masksPasswordParameter() {
        val url = "https://retroachievements.org/dorequest.php?r=login&u=player1&p=mySecretPass123"
        val sanitized = interceptor.sanitizeUrl(url)

        assertFalse(sanitized.contains("mySecretPass123"))
        assertTrue(sanitized.contains("p=***REDACTED***"))
        assertTrue(sanitized.contains("u=player1"))
    }

    @Test
    fun sanitizeUrl_masksTokenAndApiKeyParameters() {
        val url = "https://retroachievements.org/API/API_GetUserSummary.php?z=user&y=SecretApiKey456&t=Token789"
        val sanitized = interceptor.sanitizeUrl(url)

        assertFalse(sanitized.contains("SecretApiKey456"))
        assertFalse(sanitized.contains("Token789"))
        assertTrue(sanitized.contains("y=***REDACTED***"))
        assertTrue(sanitized.contains("t=***REDACTED***"))
    }

    @Test
    fun sanitizeUrl_preservesNonSensitiveParameters() {
        val url = "https://retroachievements.org/dorequest.php?r=gameid&m=0123456789abcdef0123456789abcdef"
        val sanitized = interceptor.sanitizeUrl(url)

        assertEquals(url, sanitized)
    }
}
