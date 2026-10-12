package com.retro.gba.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validação rigorosa do mecanismo de parsing seguro da API RetroAchievements,
 * garantindo que respostas em formatos primitivos (JsonPrimitive), texto puro,
 * HTML ou erros não serializados em JsonObject nunca causem quebras de execução.
 */
class RetroAchievementsApiSafeParsingTest {

    private val api = RetroAchievementsApi()

    @Test
    fun parseSafeJson_whenBodyIsPrimitiveString_returnsJsonPrimitiveWithoutCrashing() {
        // Cenário exato do BUG 1: servidor retorna string bruta em vez de JsonObject
        val rawPrimitiveBody = "\"Invalid user/password combination\""

        val element = api.parseSafeJson(rawPrimitiveBody)

        assertNotNull(element)
        assertTrue("Deve ser reconhecido como JsonPrimitive", element!!.isJsonPrimitive)
        assertEquals("Invalid user/password combination", element.asString)
    }

    @Test
    fun parseSafeJson_whenBodyIsRawPlainText_handlesGracefully() {
        val plainText = "Invalid user/password"

        val element = api.parseSafeJson(plainText)

        assertNotNull(element)
        assertTrue(element!!.isJsonPrimitive)
        assertEquals("Invalid user/password", element.asString)
    }

    @Test
    fun parseSafeJson_whenBodyIsBooleanOrNumber_doesNotCrash() {
        val boolElement = api.parseSafeJson("false")
        assertNotNull(boolElement)
        assertTrue(boolElement!!.isJsonPrimitive)
        assertEquals("false", boolElement.asString)

        val numElement = api.parseSafeJson("401")
        assertNotNull(numElement)
        assertTrue(numElement!!.isJsonPrimitive)
        assertEquals("401", numElement.asString)
    }

    @Test
    fun parseSafeJson_whenBodyIsEmptyOrNullOrBlank_returnsNull() {
        assertNull(api.parseSafeJson(null))
        assertNull(api.parseSafeJson(""))
        assertNull(api.parseSafeJson("   "))
    }

    @Test
    fun parseSafeJson_whenBodyIsInvalidHtml_returnsNullGracefully() {
        val htmlError = "<html><body>502 Bad Gateway</body></html>"
        val element = api.parseSafeJson(htmlError)
        // Não deve lançar nenhuma exceção não tratada
    }

    @Test
    fun parseSafeJson_whenBodyIsValidJsonObject_returnsJsonObject() {
        val jsonString = """{"Success":true,"User":"GbaMaster","Token":"abc123token"}"""

        val element = api.parseSafeJson(jsonString)

        assertNotNull(element)
        assertTrue(element!!.isJsonObject)
        val obj = element.asJsonObject
        assertTrue(obj.get("Success").asBoolean)
        assertEquals("GbaMaster", obj.get("User").asString)
        assertEquals("abc123token", obj.get("Token").asString)
    }

    @Test
    fun extractErrorMessage_fromPrimitiveString_returnsCleanMessage() {
        val primitive = JsonParser.parseString("\"Invalid User/Password\"")
        val msg = api.extractErrorMessage(primitive, "Fallback error")

        assertEquals("Invalid User/Password", msg)
    }

    @Test
    fun extractErrorMessage_fromJsonObjectWithErrorField_returnsErrorValue() {
        val json = JsonParser.parseString("""{"Success":false,"Error":"User not registered"}""")
        val msg = api.extractErrorMessage(json, "Fallback error")

        assertEquals("User not registered", msg)
    }

    @Test
    fun extractErrorMessage_fromJsonObjectWithMessageField_returnsMessageValue() {
        val json = JsonParser.parseString("""{"message":"Unauthenticated."}""")
        val msg = api.extractErrorMessage(json, "Fallback error")

        assertEquals("Unauthenticated.", msg)
    }

    @Test
    fun extractErrorMessage_fromJsonObjectWithCodeField_returnsFormattedCode() {
        val json = JsonParser.parseString("""{"Code":"invalid_credentials"}""")
        val msg = api.extractErrorMessage(json, "Fallback error")

        assertEquals("Erro: invalid_credentials", msg)
    }

    @Test
    fun extractErrorMessage_whenElementIsNull_returnsDefaultMessage() {
        val msg = api.extractErrorMessage(null, "Falha padrão")
        assertEquals("Falha padrão", msg)
    }
}
