package com.retro.gba.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.data.repository.RetroAchievementsRepository
import com.retro.gba.data.repository.RomRepository
import com.retro.gba.data.repository.SaveRepository
import com.retro.gba.data.repository.SaveStateInfo
import com.retro.gba.emulator.GbaCore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferences(application)
    private val raApi = RetroAchievementsApi()
    val raRepository = RetroAchievementsRepository(raApi, userPrefs)
    private val romRepo = RomRepository(application)
    val saveRepository = SaveRepository(application)
    val cheatRepository = com.retro.gba.data.repository.CheatRepository(application)

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _currentGame = MutableStateFlow<GameInfo?>(null)
    val currentGame: StateFlow<GameInfo?> = _currentGame.asStateFlow()

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _cheats = MutableStateFlow<List<com.retro.gba.data.model.CheatCode>>(emptyList())
    val cheats: StateFlow<List<com.retro.gba.data.model.CheatCode>> = _cheats.asStateFlow()

    private val _recentUnlockedAchievement = MutableStateFlow<Achievement?>(null)
    val recentUnlockedAchievement: StateFlow<Achievement?> = _recentUnlockedAchievement.asStateFlow()

    private val _isTurboActive = MutableStateFlow(false)
    val isTurboActive: StateFlow<Boolean> = _isTurboActive.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentRomHash: String? = null

    private val _activeStateSlot = MutableStateFlow(0)
    val activeStateSlot: StateFlow<Int> = _activeStateSlot.asStateFlow()

    private val _saveSlots = MutableStateFlow<List<SaveStateInfo>>(emptyList())
    val saveSlots: StateFlow<List<SaveStateInfo>> = _saveSlots.asStateFlow()

    private val hapticManager = com.retro.gba.ui.haptics.HapticManager(application)

    private val _controllerOpacity = MutableStateFlow(userPrefs.controllerOpacity)
    val controllerOpacity: StateFlow<Float> = _controllerOpacity.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(userPrefs.hapticFeedbackEnabled)
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _fastForwardSpeed = MutableStateFlow(userPrefs.fastForwardSpeed)
    val fastForwardSpeed: StateFlow<Float> = _fastForwardSpeed.asStateFlow()

    val engine: com.retro.gba.emulator.IGbaEngine = if (com.retro.gba.emulator.NativeGbaEngine.isNativeAvailable()) {
        com.retro.gba.emulator.NativeGbaEngine(
            onFrameReady = { bmp -> _currentFrame.value = bmp },
            onAchievementTriggered = { ach -> handleAchievementUnlock(ach) }
        )
    } else {
        com.retro.gba.emulator.SimulationGbaEngine(
            onFrameReady = { bmp -> _currentFrame.value = bmp },
            onAchievementTriggered = { ach -> handleAchievementUnlock(ach) }
        )
    }

    val gbaCore: com.retro.gba.emulator.IGbaEngine get() = engine

    private val _isSimulationMode = MutableStateFlow(engine.isSimulationMode)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    fun loadRomFromUri(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _statusMessage.value = "Identificando ROM no RetroAchievements..."
            val romBytes = romRepo.readRomBytes(uri)
            val validation = com.retro.gba.security.RomSecurityValidator.validateRomBytes(romBytes)
            if (validation is com.retro.gba.security.RomSecurityValidator.ValidationResult.Invalid) {
                _statusMessage.value = "Arquivo inválido: ${validation.reason}"
                return@launch
            }

            val parseResult = romRepo.parseRom(uri, fileName)

            parseResult.onSuccess { romItem ->
                val md5 = romItem.md5Hash
                currentRomHash = md5

                // 1. Identificar jogo no RetroAchievements
                val gameIdResult = raRepository.findGameByRomHash(md5)
                var gameAchievements = emptyList<Achievement>()

                gameIdResult.onSuccess { gameId ->
                    val achResult = raRepository.getAchievements(gameId)
                    achResult.onSuccess { (info, list) ->
                        _currentGame.value = info
                        _achievements.value = list
                        gameAchievements = list
                        _statusMessage.value = "Conectado ao RetroAchievements: ${info.title} (${list.size} conquistas)"
                    }.onFailure {
                        _statusMessage.value = "Conquistas indisponíveis: ${it.localizedMessage}"
                    }
                }.onFailure {
                    _statusMessage.value = "Jogo não catalogado no RetroAchievements para esta ROM."
                }

                // 2. Buscar Save RAM persistida em disco (.sav)
                val existingSave = saveRepository.loadBatteryRam(md5)
                if (existingSave != null && existingSave.isNotEmpty()) {
                    _statusMessage.value = "Progresso salvo recuperado (${existingSave.size} bytes)!"
                }

                // 3. Atualizar slots de save states
                refreshSaveSlots()

                // 4. Carregar Cheats configurados para a ROM
                val savedCheats = cheatRepository.loadCheats(md5)
                _cheats.value = savedCheats

                // 5. Iniciar motor de emulação com os bytes da ROM e o Save RAM
                val finalRom = if (romBytes.isNotEmpty()) romBytes else ByteArray(1024)
                gbaCore.loadRom(finalRom, gameAchievements, existingSave)
                if (!userPrefs.isHardcoreMode) {
                    applyCheatsToEngine(savedCheats)
                }
                gbaCore.start()
            }.onFailure {
                _statusMessage.value = "Erro ao carregar ROM: ${it.localizedMessage}"
            }
        }
    }

    private fun handleAchievementUnlock(achievement: Achievement) {
        viewModelScope.launch {
            _achievements.value = _achievements.value.map {
                if (it.id == achievement.id) it.copy(isUnlocked = true) else it
            }

            _recentUnlockedAchievement.value = achievement
            hapticManager.performAchievementUnlock(_hapticFeedbackEnabled.value)

            if (raRepository.isLoggedIn()) {
                raRepository.unlockAchievement(achievement)
            }

            delay(4000)
            if (_recentUnlockedAchievement.value?.id == achievement.id) {
                _recentUnlockedAchievement.value = null
            }
        }
    }

    fun toggleTurbo() {
        val newState = !_isTurboActive.value
        _isTurboActive.value = newState
        val speed = if (newState) _fastForwardSpeed.value else 1.0f
        engine.setSpeedMultiplier(speed)
    }

    fun setControllerOpacity(value: Float) {
        val clamped = value.coerceIn(0.2f, 1.0f)
        _controllerOpacity.value = clamped
        userPrefs.controllerOpacity = clamped
    }

    fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedbackEnabled.value = enabled
        userPrefs.hapticFeedbackEnabled = enabled
    }

    fun setFastForwardSpeed(speed: Float) {
        val clamped = speed.coerceIn(1.5f, 5.0f)
        _fastForwardSpeed.value = clamped
        userPrefs.fastForwardSpeed = clamped
        if (_isTurboActive.value) {
            engine.setSpeedMultiplier(clamped)
        }
    }

    fun selectSlot(slot: Int) {
        if (slot in 0..4) {
            _activeStateSlot.value = slot
        }
    }

    private suspend fun refreshSaveSlots() {
        val hash = currentRomHash ?: return
        _saveSlots.value = saveRepository.listStateSlots(hash)
    }

    fun persistBatterySave() {
        val hash = currentRomHash ?: return
        val saveRam = engine.getSaveRam() ?: return
        if (saveRam.isEmpty()) return

        viewModelScope.launch {
            val saved = saveRepository.saveBatteryRam(hash, saveRam)
            if (saved) {
                _statusMessage.value = "Progresso do cartucho salvo com sucesso!"
            }
        }
    }

    fun saveState(slot: Int = _activeStateSlot.value) {
        if (userPrefs.isHardcoreMode) {
            _statusMessage.value = "Save State desativado no Modo Hardcore!"
            return
        }
        val hash = currentRomHash
        if (hash == null) {
            _statusMessage.value = "Nenhum jogo ativo para salvar estado"
            return
        }

        val stateBytes = engine.saveState()
        if (stateBytes != null && stateBytes.isNotEmpty()) {
            viewModelScope.launch {
                val ok = saveRepository.saveState(hash, slot, stateBytes)
                if (ok) {
                    refreshSaveSlots()
                    _statusMessage.value = "Estado salvo no Slot $slot (${stateBytes.size} bytes)!"
                } else {
                    _statusMessage.value = "Falha ao gravar arquivo de Save State no disco"
                }
            }
        } else {
            _statusMessage.value = "Falha ao capturar estado do emulador"
        }
    }

    fun loadState(slot: Int = _activeStateSlot.value) {
        if (userPrefs.isHardcoreMode) {
            _statusMessage.value = "Load State desativado no Modo Hardcore!"
            return
        }
        val hash = currentRomHash
        if (hash == null) {
            _statusMessage.value = "Nenhum jogo ativo para carregar estado"
            return
        }

        viewModelScope.launch {
            val bytes = saveRepository.loadState(hash, slot)
            if (bytes == null || bytes.isEmpty()) {
                _statusMessage.value = "Nenhum estado salvo encontrado no Slot $slot"
                return@launch
            }
            val success = engine.loadState(bytes)
            _statusMessage.value = if (success) "Estado restaurado com sucesso do Slot $slot!" else "Falha ao restaurar Save State"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun applyCheatsToEngine(cheatList: List<com.retro.gba.data.model.CheatCode>) {
        gbaCore.clearCheats()
        if (userPrefs.isHardcoreMode) return

        cheatList.filter { it.isEnabled }.forEach { cheat ->
            gbaCore.addCheat(cheat.description, cheat.code, cheat.type.typeId, true)
        }
    }

    fun addCheat(
        description: String,
        code: String,
        type: com.retro.gba.data.model.CheatType = com.retro.gba.data.model.CheatType.AUTODETECT
    ) {
        if (userPrefs.isHardcoreMode) {
            _statusMessage.value = "Cheats desativados no Modo Hardcore do RetroAchievements!"
            return
        }
        if (code.isBlank()) {
            _statusMessage.value = "O código do cheat não pode estar vazio."
            return
        }
        val hash = currentRomHash
        if (hash == null) {
            _statusMessage.value = "Nenhum jogo ativo para vincular o cheat."
            return
        }

        val newCheat = com.retro.gba.data.model.CheatCode(
            description = description.ifBlank { "Trapaça" },
            code = code.trim(),
            type = type,
            isEnabled = true
        )
        val updated = _cheats.value + newCheat
        _cheats.value = updated

        viewModelScope.launch {
            cheatRepository.saveCheats(hash, updated)
            applyCheatsToEngine(updated)
            _statusMessage.value = "Cheat '${newCheat.description}' ativado com sucesso!"
        }
    }

    fun toggleCheat(cheatId: String) {
        if (userPrefs.isHardcoreMode) {
            _statusMessage.value = "Cheats desativados no Modo Hardcore do RetroAchievements!"
            return
        }
        val hash = currentRomHash ?: return
        val updated = _cheats.value.map {
            if (it.id == cheatId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _cheats.value = updated

        viewModelScope.launch {
            cheatRepository.saveCheats(hash, updated)
            applyCheatsToEngine(updated)
        }
    }

    fun removeCheat(cheatId: String) {
        val hash = currentRomHash ?: return
        val updated = _cheats.value.filter { it.id != cheatId }
        _cheats.value = updated

        viewModelScope.launch {
            cheatRepository.saveCheats(hash, updated)
            applyCheatsToEngine(updated)
            _statusMessage.value = "Cheat removido."
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistBatterySave()
        gbaCore.stop()
    }
}
