package com.retro.gba.emulator

import android.graphics.Bitmap
import com.retro.gba.data.model.Achievement

/**
 * Contrato oficial de abstração do motor de execução do Game Boy Advance.
 * Desacopla a interface de usuário (Jetpack Compose) do núcleo real de emulação.
 */
interface IGbaEngine {
    val isRunning: Boolean
    val isPaused: Boolean
    val isSimulationMode: Boolean
    var frameskip: Int
    val currentFps: Int

    /**
     * Carrega os bytes binários da ROM e dados persistentes de save (SRAM/Flash/EEPROM).
     * Retorna true se a ROM foi validada e inicializada com sucesso.
     */
    fun loadRom(romBytes: ByteArray, achievements: List<Achievement> = emptyList(), saveBytes: ByteArray? = null): Boolean

    /**
     * Inicia o loop de execução da emulação.
     */
    fun start()

    /**
     * Pausa temporariamente a execução (ex: app em background).
     */
    fun pause()

    /**
     * Retoma a execução pausada.
     */
    fun resume()

    /**
     * Encerra a emulação e libera recursos alocados.
     */
    fun stop()

    /**
     * Atualiza o estado de um botão (bit do registrador KEYINPUT).
     */
    fun setButtonPressed(buttonMask: Int, pressed: Boolean)

    /**
     * Ajusta multiplicador de velocidade (1.0x normal, >1.0x turbo).
     */
    fun setSpeedMultiplier(multiplier: Float)

    /**
     * Obtém os dados de Save RAM persistente do cartucho.
     */
    fun getSaveRam(): ByteArray?

    /**
     * Captura um Save State instantâneo da sessão atual.
     */
    fun saveState(): ByteArray?

    /**
     * Restaura a sessão a partir de um Save State previamente salvo.
     */
    fun loadState(stateBytes: ByteArray): Boolean

    /**
     * Lê endereço de memória emulado para inspeção do RetroAchievements.
     */
    fun readMemory(address: Long, size: Int): Long

    /**
     * Adiciona código de trapaça (GameShark, Action Replay, CodeBreaker).
     */
    fun addCheat(description: String, code: String, type: Int = 0, enabled: Boolean = true): Boolean

    /**
     * Remove todos os códigos de trapaça ativos.
     */
    fun clearCheats()

    /**
     * Altera o estado (ativo/inativo) de um cheat registrado.
     */
    fun setCheatEnabled(index: Int, enabled: Boolean)
}
