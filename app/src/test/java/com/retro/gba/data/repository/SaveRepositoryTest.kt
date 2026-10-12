package com.retro.gba.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var saveRepository: SaveRepository
    private val testHash = "d41d8cd98f00b204e9800998ecf8427e"

    @Before
    fun setUp() {
        saveRepository = SaveRepository(tempFolder.newFolder("test_saves"))
    }

    @Test
    fun testBatterySaveAtomicPersistenceAndRecovery() = runBlocking {
        assertFalse(saveRepository.hasBatteryRam(testHash))
        assertNull(saveRepository.loadBatteryRam(testHash))

        val dummySram = ByteArray(64 * 1024) { (it % 256).toByte() }

        // Salvar bateria de forma atômica
        val saved = saveRepository.saveBatteryRam(testHash, dummySram)
        assertTrue(saved)
        assertTrue(saveRepository.hasBatteryRam(testHash))

        // Recuperar e validar integridade byte a byte
        val recovered = saveRepository.loadBatteryRam(testHash)
        assertNotNull(recovered)
        assertEquals(dummySram.size, recovered!!.size)
        assertArrayEquals(dummySram, recovered)

        // Hash inválido deve ser rejeitado com segurança
        assertFalse(saveRepository.saveBatteryRam("", dummySram))
        assertNull(saveRepository.loadBatteryRam(""))
    }

    @Test
    fun testSaveStateSlotsManagement() = runBlocking {
        val dummyState = ByteArray(1024) { 0x55.toByte() }

        // Inicialmente todos os slots devem constar como vazios
        val initialSlots = saveRepository.listStateSlots(testHash, 5)
        assertEquals(5, initialSlots.size)
        assertTrue(initialSlots.all { !it.exists })

        // Salvar no slot 2
        val savedSlot2 = saveRepository.saveState(testHash, 2, dummyState)
        assertTrue(savedSlot2)

        val updatedSlots = saveRepository.listStateSlots(testHash, 5)
        assertFalse(updatedSlots[0].exists)
        assertFalse(updatedSlots[1].exists)
        assertTrue(updatedSlots[2].exists)
        assertEquals(dummyState.size.toLong(), updatedSlots[2].sizeBytes)

        // Carregar estado do slot 2
        val loadedState = saveRepository.loadState(testHash, 2)
        assertNotNull(loadedState)
        assertArrayEquals(dummyState, loadedState)

        // Deletar estado do slot 2
        assertTrue(saveRepository.deleteState(testHash, 2))
        assertFalse(saveRepository.listStateSlots(testHash, 5)[2].exists)
    }
}
