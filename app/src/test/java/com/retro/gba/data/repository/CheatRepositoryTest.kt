package com.retro.gba.data.repository

import com.retro.gba.data.model.CheatCode
import com.retro.gba.data.model.CheatType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CheatRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: CheatRepository
    private lateinit var cheatsDir: File

    @Before
    fun setUp() {
        cheatsDir = tempFolder.newFolder("cheats_test")
        repository = CheatRepository(cheatsDir)
    }

    @Test
    fun saveAndLoadCheats_persistsListCorrectly() = runBlocking {
        val hash = "0123456789abcdef0123456789abcdef"
        val cheatList = listOf(
            CheatCode(
                description = "Dinheiro Infinito",
                code = "82003884 0063",
                type = CheatType.GAMESHARK,
                isEnabled = true
            ),
            CheatCode(
                description = "Master Ball x99",
                code = "03003000:00FF",
                type = CheatType.PRO_ACTION_REPLAY,
                isEnabled = false
            )
        )

        val saved = repository.saveCheats(hash, cheatList)
        assertTrue(saved)

        val loaded = repository.loadCheats(hash)
        assertEquals(2, loaded.size)
        assertEquals("Dinheiro Infinito", loaded[0].description)
        assertEquals("82003884 0063", loaded[0].code)
        assertEquals(CheatType.GAMESHARK, loaded[0].type)
        assertTrue(loaded[0].isEnabled)

        assertEquals("Master Ball x99", loaded[1].description)
        assertEquals("03003000:00FF", loaded[1].code)
        assertEquals(CheatType.PRO_ACTION_REPLAY, loaded[1].type)
        assertFalse(loaded[1].isEnabled)
    }

    @Test
    fun loadCheats_forNonExistentRom_returnsEmptyList() = runBlocking {
        val loaded = repository.loadCheats("non_existent_hash")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun deleteCheats_removesFileFromDisk() = runBlocking {
        val hash = "abcde12345"
        val list = listOf(CheatCode(description = "Teste", code = "1234 5678"))
        repository.saveCheats(hash, list)

        val beforeDelete = repository.loadCheats(hash)
        assertEquals(1, beforeDelete.size)

        val deleted = repository.deleteCheats(hash)
        assertTrue(deleted)

        val afterDelete = repository.loadCheats(hash)
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun pathTraversalAttempts_areSanitizedAndKeptConfined() = runBlocking {
        val maliciousHash = "../../etc/malicious"
        val list = listOf(CheatCode(description = "Exploit", code = "0000 0000"))

        val saved = repository.saveCheats(maliciousHash, list)
        assertTrue(saved)

        // O arquivo deve ter sido salvo dentro do diretório cheats_test com nome sanitizado
        val files = cheatsDir.listFiles()
        assertNotNull(files)
        assertEquals(1, files!!.size)
        assertEquals("etcmalicious.json", files[0].name)
    }
}
