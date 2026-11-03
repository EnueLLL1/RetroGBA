package com.retro.gba.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.ControlElementState
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.RomItem
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.data.repository.RetroAchievementsRepository
import com.retro.gba.data.repository.RomRepository
import com.retro.gba.data.repository.SaveRepository
import com.retro.gba.data.repository.SaveStateInfo
import com.retro.gba.emulator.GbaInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferences(application)
    private val raApi = RetroAchievementsApi()
    val raRepository = RetroAchievementsRepository(raApi, userPrefs)
    private val romRepo = RomRepository(application)
    val saveRepository = SaveRepository(application)
    val cheatRepository = com.retro.gba.data.repository.CheatRepository(application)
    val powerManager = com.retro.gba.performance.PowerProfileManager(application)

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

    private val _isEcoMode = MutableStateFlow(false)
    val isEcoMode: StateFlow<Boolean> = _isEcoMode.asStateFlow()

    private val _currentFps = MutableStateFlow(60)
    val currentFps: StateFlow<Int> = _currentFps.asStateFlow()

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

    private val _controllerScale = MutableStateFlow(userPrefs.controllerScale)
    val controllerScale: StateFlow<Float> = _controllerScale.asStateFlow()

    private val _controllerTheme = MutableStateFlow(userPrefs.controllerTheme)
    val controllerTheme: StateFlow<String> = _controllerTheme.asStateFlow()

    private val _showQuickSaveLoad = MutableStateFlow(userPrefs.showQuickSaveLoad)
    val showQuickSaveLoad: StateFlow<Boolean> = _showQuickSaveLoad.asStateFlow()

    private val _showAbCombo = MutableStateFlow(userPrefs.showAbCombo)
    val showAbCombo: StateFlow<Boolean> = _showAbCombo.asStateFlow()

    private val _showTurboButton = MutableStateFlow(userPrefs.showTurboButton)
    val showTurboButton: StateFlow<Boolean> = _showTurboButton.asStateFlow()

    private val _showFpsCounter = MutableStateFlow(userPrefs.showFpsCounter)
    val showFpsCounter: StateFlow<Boolean> = _showFpsCounter.asStateFlow()

    private val _stretchToFill = MutableStateFlow(userPrefs.stretchToFill)
    val stretchToFill: StateFlow<Boolean> = _stretchToFill.asStateFlow()

    private val _isLayoutEditMode = MutableStateFlow(false)
    val isLayoutEditMode: StateFlow<Boolean> = _isLayoutEditMode.asStateFlow()

    private val _portraitControls = MutableStateFlow(userPrefs.getControlStates(isLandscape = false))
    val portraitControls: StateFlow<Map<String, ControlElementState>> = _portraitControls.asStateFlow()

    private val _landscapeControls = MutableStateFlow(userPrefs.getControlStates(isLandscape = true))
    val landscapeControls: StateFlow<Map<String, ControlElementState>> = _landscapeControls.asStateFlow()


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

    init {
        viewModelScope.launch {
            var pingCounter = 0
            while (true) {
                delay(1000)
                if (gbaCore.isRunning && !gbaCore.isPaused) {
                    _currentFps.value = gbaCore.currentFps
                    updateFrameskip()

                    // Heartbeat periódico de ping a cada 120 segundos (2 minutos)
                    pingCounter++
                    if (pingCounter >= 120) {
                        pingCounter = 0
                        _currentGame.value?.let { game ->
                            if (raRepository.isLoggedIn()) {
                                raRepository.ping(game.id, "Jogando ${game.title}")
                            }
                        }
                    }
                } else {
                    pingCounter = 0
                }
            }
        }
    }

    fun setEcoMode(enabled: Boolean) {
        _isEcoMode.value = enabled
        updateFrameskip()
        _statusMessage.value = if (enabled) "Modo Eco de Bateria ativado (Frameskip adaptativo)" else "Modo Eco desativado (60 FPS pleno)"
    }

    fun updateFrameskip() {
        val recommended = powerManager.calculateRecommendedFrameskip(_isEcoMode.value)
        gbaCore.frameskip = recommended
    }

    fun setMenuPaused(paused: Boolean) {
        gbaCore.setButtonPressed(GbaInput.ALL_RELEASED, false)
        if (!gbaCore.isRunning) return
        if (paused && !gbaCore.isPaused) {
            gbaCore.pause()
        } else if (!paused && gbaCore.isPaused && !_isLayoutEditMode.value) {
            gbaCore.resume()
            updateFrameskip()
        }
    }

    fun onAppPause() {
        gbaCore.setButtonPressed(GbaInput.ALL_RELEASED, false)
        if (gbaCore.isRunning && !gbaCore.isPaused) {
            gbaCore.pause()
            persistBatterySave()
            _currentGame.value?.let { game ->
                viewModelScope.launch {
                    raRepository.ping(game.id, "Pausado")
                }
            }
        }
    }

    fun onAppResume() {
        if (gbaCore.isRunning && gbaCore.isPaused && !_isLayoutEditMode.value) {
            gbaCore.resume()
            updateFrameskip()
            _currentGame.value?.let { game ->
                viewModelScope.launch {
                    raRepository.ping(game.id, "Jogando ${game.title}")
                }
            }
        }
    }

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

                        // Inicia sessão oficial de jogo e obtém conquistas já salvas no servidor
                        val sessionResult = raRepository.startSession(info.id, md5)
                        val unlockedIds = sessionResult.getOrDefault(emptySet())

                        val updatedList = list.map { ach ->
                            if (unlockedIds.contains(ach.id)) ach.copy(isUnlocked = true) else ach
                        }

                        _achievements.value = updatedList
                        gameAchievements = updatedList
                        _statusMessage.value = "RA: ${info.title} (${list.size} troféus)"

                        // Salva o ícone/capa do jogo e dados de conquistas atualizados no RomItem
                        val updatedRomItem = romItem.copy(
                            gameId = info.id,
                            title = if (info.title.isNotBlank()) info.title else romItem.title,
                            totalAchievements = list.size,
                            totalPoints = list.sumOf { it.points },
                            badgeIconUrl = info.iconUrl
                        )
                        romRepo.saveRomItem(updatedRomItem)
                    }.onFailure {
                        _statusMessage.value = "Conquistas indisponíveis: ${it.localizedMessage}"
                    }
                }.onFailure {
                    _statusMessage.value = "Jogo não catalogado no RetroAchievements."
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
                val loadedSuccessfully = gbaCore.loadRom(finalRom, gameAchievements, existingSave)
                if (loadedSuccessfully) {
                    if (!userPrefs.isHardcoreMode) {
                        applyCheatsToEngine(savedCheats)
                    }
                    gbaCore.start()
                } else {
                    _statusMessage.value = "Falha ao carregar a ROM no motor de emulação."
                }
            }.onFailure {
                _statusMessage.value = "Erro ao carregar ROM: ${it.localizedMessage}"
            }
        }
    }

    fun getSavedRoms(): List<RomItem> {
        return romRepo.getSavedRoms()
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

    fun setControllerScale(scale: Float) {
        val clamped = scale.coerceIn(0.75f, 1.35f)
        _controllerScale.value = clamped
        userPrefs.controllerScale = clamped
    }

    fun setControllerTheme(theme: String) {
        _controllerTheme.value = theme
        userPrefs.controllerTheme = theme
    }

    fun setShowQuickSaveLoad(enabled: Boolean) {
        _showQuickSaveLoad.value = enabled
        userPrefs.showQuickSaveLoad = enabled
    }

    fun setShowAbCombo(enabled: Boolean) {
        _showAbCombo.value = enabled
        userPrefs.showAbCombo = enabled
    }

    fun setShowTurboButton(enabled: Boolean) {
        _showTurboButton.value = enabled
        userPrefs.showTurboButton = enabled
    }

    fun setShowFpsCounter(enabled: Boolean) {
        _showFpsCounter.value = enabled
        userPrefs.showFpsCounter = enabled
    }

    fun setStretchToFill(enabled: Boolean) {
        _stretchToFill.value = enabled
        userPrefs.stretchToFill = enabled
    }

    fun setLayoutEditMode(enabled: Boolean) {
        gbaCore.setButtonPressed(GbaInput.ALL_RELEASED, false)
        _isLayoutEditMode.value = enabled
        if (enabled && gbaCore.isRunning && !gbaCore.isPaused) {
            gbaCore.pause()
        } else if (!enabled && gbaCore.isRunning && gbaCore.isPaused) {
            gbaCore.resume()
            updateFrameskip()
        }
    }

    fun updateControlOffset(id: String, isLandscape: Boolean, dxDp: Float, dyDp: Float) {
        val clampedDx = dxDp.coerceIn(-360f, 360f)
        val clampedDy = dyDp.coerceIn(-260f, 260f)
        if (isLandscape) {
            val updated = _landscapeControls.value.toMutableMap()
            val existing = updated[id] ?: ControlElementState()
            updated[id] = existing.copy(dx = clampedDx, dy = clampedDy)
            _landscapeControls.value = updated
            userPrefs.setControlStates(isLandscape = true, updated)
        } else {
            val updated = _portraitControls.value.toMutableMap()
            val existing = updated[id] ?: ControlElementState()
            updated[id] = existing.copy(dx = clampedDx, dy = clampedDy)
            _portraitControls.value = updated
            userPrefs.setControlStates(isLandscape = false, updated)
        }
    }

    fun updateControlScale(id: String, isLandscape: Boolean, scale: Float) {
        val clampedScale = scale.coerceIn(0.50f, 1.80f)
        if (isLandscape) {
            val updated = _landscapeControls.value.toMutableMap()
            val existing = updated[id] ?: ControlElementState()
            updated[id] = existing.copy(scale = clampedScale)
            _landscapeControls.value = updated
            userPrefs.setControlStates(isLandscape = true, updated)
        } else {
            val updated = _portraitControls.value.toMutableMap()
            val existing = updated[id] ?: ControlElementState()
            updated[id] = existing.copy(scale = clampedScale)
            _portraitControls.value = updated
            userPrefs.setControlStates(isLandscape = false, updated)
        }
    }

    fun resetSingleControl(id: String, isLandscape: Boolean) {
        userPrefs.resetSingleControl(id, isLandscape)
        if (isLandscape) {
            val updated = _landscapeControls.value.toMutableMap()
            updated.remove(id)
            _landscapeControls.value = updated
        } else {
            val updated = _portraitControls.value.toMutableMap()
            updated.remove(id)
            _portraitControls.value = updated
        }
        _statusMessage.value = "Item restaurado para o padrão"
    }

    fun resetLayoutOffsets(isLandscape: Boolean) {
        userPrefs.resetControlStates(isLandscape)
        if (isLandscape) {
            _landscapeControls.value = emptyMap()
        } else {
            _portraitControls.value = emptyMap()
        }
        _statusMessage.value = "Layout restaurado para o padrão"
    }

    fun quickSave() {
        saveState(_activeStateSlot.value)
    }

    fun quickLoad() {
        loadState(_activeStateSlot.value)
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
            withContext(NonCancellable) {
                val saved = saveRepository.saveBatteryRam(hash, saveRam)
                if (saved) {
                    _statusMessage.value = "Bateria (.SAV) gravada com sucesso!"
                }
            }
        }
    }

    private fun persistBatterySaveBlocking() {
        val hash = currentRomHash ?: return
        val saveRam = engine.getSaveRam() ?: return
        if (saveRam.isEmpty()) return

        try {
            runBlocking {
                saveRepository.saveBatteryRam(hash, saveRam)
            }
        } catch (t: Throwable) {
            android.util.Log.e("EmulatorViewModel", "Erro ao persistir save de bateria no onCleared", t)
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
                    _statusMessage.value = "Salvo no Slot $slot"
                } else {
                    _statusMessage.value = "Falha ao gravar Save State"
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
                _statusMessage.value = "Slot $slot vazio"
                return@launch
            }
            val success = engine.loadState(bytes)
            _statusMessage.value = if (success) "Slot $slot carregado!" else "Falha ao restaurar Save State"
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
            _statusMessage.value = "Cheats desativados no Modo Hardcore!"
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
            _statusMessage.value = "Cheat '${newCheat.description}' ativado!"
        }
    }

    fun toggleCheat(cheatId: String) {
        if (userPrefs.isHardcoreMode) {
            _statusMessage.value = "Cheats desativados no Modo Hardcore!"
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
        persistBatterySaveBlocking()
        gbaCore.stop()
    }
}
