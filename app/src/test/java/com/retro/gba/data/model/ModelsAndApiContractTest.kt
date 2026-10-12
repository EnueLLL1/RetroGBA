package com.retro.gba.data.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsAndApiContractTest {

    private val gson = Gson()

    @Test
    fun testLoginSuccessResponseContract() {
        val jsonPayload = """
            {
                "Success": true,
                "User": "RetroPlayer",
                "Token": "abc123secrettoken"
            }
        """.trimIndent()

        val json = gson.fromJson(jsonPayload, JsonObject::class.java)
        assertTrue(json.get("Success").asBoolean)
        assertEquals("RetroPlayer", json.get("User").asString)
        assertEquals("abc123secrettoken", json.get("Token").asString)
    }

    @Test
    fun testPatchAchievementsResponseParsing() {
        val patchPayload = """
            {
                "Success": true,
                "PatchData": {
                    "ID": 1234,
                    "Title": "Pokemon FireRed",
                    "ImageIcon": "/Images/012345.png",
                    "Achievements": [
                        {
                            "ID": 101,
                            "Title": "Champion",
                            "Description": "Defeat the Elite Four",
                            "Points": 25,
                            "BadgeName": "00101",
                            "MemAddr": "0xH02000000=1"
                        },
                        {
                            "ID": 102,
                            "Title": "First Catch",
                            "Description": "Catch your first Pokemon",
                            "Points": 5,
                            "BadgeName": "00102",
                            "MemAddr": "0xH02000001=1"
                        }
                    ]
                }
            }
        """.trimIndent()

        val json = gson.fromJson(patchPayload, JsonObject::class.java)
        assertTrue(json.get("Success").asBoolean)
        val patchData = json.getAsJsonObject("PatchData")
        val title = patchData.get("Title").asString
        val achArray = patchData.getAsJsonArray("Achievements")

        assertEquals("Pokemon FireRed", title)
        assertEquals(2, achArray.size())

        val achievements = mutableListOf<Achievement>()
        for (elem in achArray) {
            val obj = elem.asJsonObject
            achievements.add(
                Achievement(
                    id = obj.get("ID").asLong,
                    title = obj.get("Title").asString,
                    description = obj.get("Description").asString,
                    points = obj.get("Points").asInt,
                    badgeName = obj.get("BadgeName").asString,
                    memAddr = obj.get("MemAddr")?.asString
                )
            )
        }

        assertEquals(2, achievements.size)
        assertEquals(30, achievements.sumOf { it.points })
        assertEquals("Champion", achievements[0].title)
    }

    @Test
    fun testAwardAchievementResponseContract() {
        val successPayload = """{"Success": true, "Score": 1500}"""
        val errorPayload = """{"Success": false, "Error": "Invalid credentials or token"}"""

        val successJson = gson.fromJson(successPayload, JsonObject::class.java)
        assertTrue(successJson.get("Success").asBoolean)

        val errorJson = gson.fromJson(errorPayload, JsonObject::class.java)
        assertEquals(false, errorJson.get("Success").asBoolean)
        assertEquals("Invalid credentials or token", errorJson.get("Error").asString)
    }
}
