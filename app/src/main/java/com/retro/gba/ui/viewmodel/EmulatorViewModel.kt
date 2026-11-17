package com.retro.gba.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.CheatCode
import com.retro.gba.data.model.CheatType
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.RomItem
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.data.repository.CheatRepository
import com.retro.gba.data.repository.RetroAchievementsRepository
import com.retro.gba.data.repository.RomRepository
import com.retro.gba.data.repository.SaveRepository
import com.retro.gba.data.repository.SaveStateInfo
import com.retro.gba.emulator.GbaInput
import com.retro.gba.emulator.IGbaEngine
import com.retro.gba.emulator.NativeGbaEngine
import com.retro.gba.emulator.SimulationGbaEngine
import com.retro.gba.model.ControlLayoutPresets
import com.retro.gba.model.ControlLayoutProfile
import com.retro.gba.model.OrientationLayoutConfig
import com.retro.gba.performance.IPowerProfileManager
import com.retro.gba.performance.PowerProfileManager
import com.retro.gba.security.RomSecurityValidator
import com.retro.gba.ui.haptics.HapticManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper compartilhado entre [EmulatorViewModel] e [ProfileViewModel] para carga,
 * criação, seleção, edição por orientação e exclusão de perfis de controle estilo My Boy!.
 */
internal object LayoutProfileHelper {

    fun loadMergedProfiles(rawJson: String?): List<ControlLayoutProfile> {
        val builtIns = ControlLayoutPresets.builtInProfiles()
        val parsed = ControlLayoutPresets.fromJsonList(rawJson)
        if (parsed.isEmpty()) return builtIns

        val parsedById = parsed.associateBy { it.id }
        val builtInIds = builtIns.map { it.id }.toSet()

        val mergedBuiltIns = builtIns.map { builtIn ->
            val override = parsedById[builtIn.id]
            if (override != null) {
                override.copy(
                    id = builtIn.id,
                    name = builtIn.name,
                    isBuiltIn = true,
                    portrait = override.portrait.clamped(),
                    landscape = override.landscape.clamped()
                )
            } else {
                builtIn
            }
        }

        val customProfiles = parsed
            .filter { !it.isBuiltIn && it.id !in builtInIds }
            .map {
                it.copy(
                    isBuiltIn = false,
                    portrait = it.portrait.clamped(),
                    landscape = it.landscape.clamped()
                )
            }

        return mergedBuiltIns + customProfiles
    }

    fun resolveActiveProfile(
        profiles: List<ControlLayoutProfile>,
        activeProfileId: String
    ): ControlLayoutProfile {
        return profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.firstOrNull()
            ?: ControlLayoutPresets.defaultProfile()
    }

    fun builtInDefaultFor(profileId: String): ControlLayoutProfile {
        return ControlLayoutPresets.builtInProfiles().firstOrNull { it.id == profileId }
            ?: ControlLayoutPresets.defaultProfile()
    }

    fun selectProfile(userPrefs: UserPreferences, profileId: String): List<ControlLayoutProfile> {
        val profiles = loadMergedProfiles(userPrefs.layoutProfilesJson)
        val exists = profiles.any { it.id == profileId }
        val resolvedId = if (exists) profileId else ControlLayoutPresets.defaultProfile().id
        userPrefs.activeLayoutProfileId = resolvedId
        return profiles
    }

    fun saveOrientationLayout(
        userPrefs: UserPreferences,
        activeProfileId: String,
        isLandscape: Boolean,
        config: OrientationLayoutConfig
    ): List<ControlLayoutProfile> {
        val clampedConfig = config.clamped()
        val currentProfiles = loadMergedProfiles(userPrefs.layoutProfilesJson)
        val targetId = currentProfiles.firstOrNull { it.id == activeProfileId }?.id
            ?: ControlLayoutPresets.defaultProfile().id
        val updated = currentProfiles.map { profile ->
            if (profile.id == targetId) {
                if (isLandscape) profile.copy(landscape = clampedConfig)
                else profile.copy(portrait = clampedConfig)
            } else {
                profile
            }
        }
        userPrefs.layoutProfilesJson = ControlLayoutPresets.toJsonList(updated)
        return updated
    }

    fun resetOrientationLayout(
        userPrefs: UserPreferences,
        activeProfileId: String,
        isLandscape: Boolean
    ): List<ControlLayoutProfile> {
        val baseline = builtInDefaultFor(activeProfileId)
        val defaultOrientation = if (isLandscape) baseline.landscape else baseline.portrait
        return saveOrientationLayout(userPrefs, activeProfileId, isLandscape, defaultOrientation)
    }

    fun createCustomProfile(
        userPrefs: UserPreferences,
        name: String,
        baseProfile: ControlLayoutProfile
    ): Pair<List<ControlLayoutProfile>, ControlLayoutProfile>? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        val currentProfiles = loadMergedProfiles(userPrefs.layoutProfilesJson)
        val newProfile = ControlLayoutProfile(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = trimmed,
            isBuiltIn = false,
            portrait = baseProfile.portrait.clamped(),
            landscape = baseProfile.landscape.clamped()
        )
        val updated = currentProfiles + newProfile
        userPrefs.layoutProfilesJson = ControlLayoutPresets.toJsonList(updated)
        userPrefs.activeLayoutProfileId = newProfile.id
        return updated to newProfile
    }

    fun deleteCustomProfile(
        userPrefs: UserPreferences,
        profileId: String
    ): List<ControlLayoutProfile> {
        val currentProfiles = loadMergedProfiles(userPrefs.layoutProfilesJson)
        val target = currentProfiles.firstOrNull { it.id == profileId } ?: return currentProfiles
        if (target.isBuiltIn) return currentProfiles

        val updated = currentProfiles.filterNot { it.id == profileId }
        userPrefs.layoutProfilesJson = ControlLayoutPresets.toJsonList(updated)
        if (userPrefs.activeLayoutProfileId == profileId) {
            userPrefs.activeLayoutProfileId = ControlLayoutPresets.defaultProfile().id
        }
        return updated
    }
}

class EmulatorViewModel internal constructor(
    application: Application,
    private val userPrefs: UserPreferences,
    val raRepository: RetroAchievementsRepository,
    private val romRepo: RomRepository,
    val saveRepository: SaveRepository,
    val cheatRepository: CheatRepository,
    val powerManager: IPowerProfileManager,
    private val hapticManager: HapticManager?,
    engineFactory: ((onFrameReady: (Bitmap) -> Unit, onAchievementTriggered: (Achievement) -> Unit) -> IGbaEngine)? = null,
    startBackgroundTicker: Boolean = true
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        userPrefs = UserPreferences(application),
        raRepository = RetroAchievementsRepository(RetroAchievementsApi(), UserPreferences(application)),
        romRepo = RomRepository(application),
        saveRepository = SaveRepository(application),
        cheatRepository = CheatRepository(application),
        powerManager = PowerProfileManager(application),
        hapticManager = HapticManager(application),
        engineFactory = null,
        startBackgroundTicker = true
    )

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _currentGame = MutableStateFlow<GameInfo?>(null)
    val currentGame: StateFlow<GameInfo?> = _currentGame.asStateFlow()

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _cheats = MutableStateFlow<List<CheatCode>>(emptyList())
    val cheats: StateFlow<List<CheatCode>> = _cheats.asStateFlow()

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
    private var currentRomUri: Uri? = null
    private var currentRomFileName: String? = null
    private var isRomSessionLoaded: Boolean = false
    private var richPresenceActivatedForGameId: Long? = null
    private var wasPausedBeforeEditing: Boolean = false

    private val _sessionHardcoreActive = MutableStateFlow(userPrefs.isHardcoreMode)
    val sessionHardcoreActive: StateFlow<Boolean> = _sessionHardcoreActive.asStateFlow()

    private val _activeStateSlot = MutableStateFlow(0)
    val activeStateSlot: StateFlow<Int> = _activeStateSlot.asStateFlow()

    private val _saveSlots = MutableStateFlow<List<SaveStateInfo>>(emptyList())
    val saveSlots: StateFlow<List<SaveStateInfo>> = _saveSlots.asStateFlow()

    // Preferências reativas compartilhadas com ProfileViewModel via UserPreferences
    val controllerOpacity: StateFlow<Float> = userPrefs.controllerOpacityFlow
    val hapticFeedbackEnabled: StateFlow<Boolean> = userPrefs.hapticFeedbackEnabledFlow
    val fastForwardSpeed: StateFlow<Float> = userPrefs.fastForwardSpeedFlow
    val hideControls: StateFlow<Boolean> = userPrefs.hideControlsFlow

    private val initialProfiles = LayoutProfileHelper.loadMergedProfiles(userPrefs.layoutProfilesJson)
    private val _layoutProfiles = MutableStateFlow(initialProfiles)
    val layoutProfiles: StateFlow<List<ControlLayoutProfile>> = _layoutProfiles.asStateFlow()

    private val _activeLayoutProfile = MutableStateFlow(
        LayoutProfileHelper.resolveActiveProfile(initialProfiles, userPrefs.activeLayoutProfileId)
    )
    val activeLayoutProfile: StateFlow<ControlLayoutProfile> = _activeLayoutProfile.asStateFlow()

    private val _isEditingLayout = MutableStateFlow(false)
    val isEditingLayout: StateFlow<Boolean> = _isEditingLayout.asStateFlow()

    private val _isOverlayModalOpen = MutableStateFlow(false)
    val isOverlayModalOpen: StateFlow<Boolean> = _isOverlayModalOpen.asStateFlow()

    // Máscaras active-high independentes para toque, botões físicos e eixos/hat analógicos
    private val touchButtonsMask = AtomicInteger(0)
    private val gamepadButtonsMask = AtomicInteger(0)
    private val gamepadAxisMask = AtomicInteger(0)

    private val achievementOverlayChannel = Channel<Achievement>(Channel.BUFFERED)
    internal var lastSaveJob: Job? = null
    internal var lastStateJob: Job? = null

    val engine: IGbaEngine = engineFactory?.invoke(
        { bmp -> _currentFrame.value = bmp },
        { ach -> handleAchievementUnlock(ach) }
    ) ?: if (NativeGbaEngine.isNativeAvailable()) {
        NativeGbaEngine(
            onFrameReady = { bmp -> _currentFrame.value = bmp },
            onAchievementTriggered = { ach -> handleAchievementUnlock(ach) }
        )
    } else {
        SimulationGbaEngine(
            onFrameReady = { bmp -> _currentFrame.value = bmp },
            onAchievementTriggered = { ach -> handleAchievementUnlock(ach) }
        )
    }

    val gbaCore: IGbaEngine get() = engine

    private val _isSimulationMode = MutableStateFlow(engine.isSimulationMode)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    init {
        observeSharedPreferences()
        startAchievementBannerConsumer()
        if (startBackgroundTicker) {
            startEmulationStatusAndPingLoop()
        }
    }

    private fun observeSharedPreferences() {
        viewModelScope.launch {
            userPrefs.fastForwardSpeedFlow.collect { speed ->
                if (_isTurboActive.value) {
                    engine.setSpeedMultiplier(speed)
                }
            }
        }

        viewModelScope.launch {
            userPrefs.isHardcoreModeFlow.collect { prefHardcore ->
                if (!isRomSessionLoaded) {
                    _sessionHardcoreActive.value = prefHardcore
                } else if (!prefHardcore && _sessionHardcoreActive.value) {
                    // Rebaixamento irreversível até o próximo loadRom() / reset()
                    _sessionHardcoreActive.value = false
                    _statusMessage.value =
                        "Modo Hardcore desligado: sessão rebaixada para Softcore até o próximo loadRom/reset."
                }
            }
        }

        viewModelScope.launch {
            combine(
                userPrefs.layoutProfilesJsonFlow,
                userPrefs.activeLayoutProfileIdFlow
            ) { profilesJson, activeId ->
                val merged = LayoutProfileHelper.loadMergedProfiles(profilesJson)
                merged to LayoutProfileHelper.resolveActiveProfile(merged, activeId)
            }.collect { (merged, active) ->
                _layoutProfiles.value = merged
                _activeLayoutProfile.value = active
            }
        }
    }

    private fun startAchievementBannerConsumer() {
        viewModelScope.launch {
            for (ach in achievementOverlayChannel) {
                _recentUnlockedAchievement.value = ach
                hapticManager?.performAchievementUnlock(hapticFeedbackEnabled.value)
                delay(3800)
                _recentUnlockedAchievement.value = null
                delay(220)
            }
        }
    }

    private fun startEmulationStatusAndPingLoop() {
        viewModelScope.launch {
            var pingCounter = 0
            while (true) {
                delay(1000)
                if (gbaCore.isRunning && !gbaCore.isPaused) {
                    _currentFps.value = gbaCore.currentFps
                    updateFrameskip()

                    pingCounter++
                    if (pingCounter >= 30) {
                        pingCounter = 0
                        sendHeartbeatPingAndFlush()
                    }
                } else {
                    pingCounter = 0
                }
            }
        }
    }

    private suspend fun sendHeartbeatPingAndFlush() {
        val game = _currentGame.value ?: return
        if (!raRepository.isLoggedIn()) return

        if (game.richPresencePatch.isNotBlank()) {
            engine.activateRichPresence(game.richPresencePatch)
            richPresenceActivatedForGameId = game.id
        }
        val richPresence = engine.getRichPresence().takeIf { it.isNotBlank() }
            ?: "Jogando ${game.title}"

        raRepository.sendPing(
            gameId = game.id,
            richPresence = richPresence,
            hardcore = _sessionHardcoreActive.value,
            gameHash = currentRomHash.orEmpty()
        )
        raRepository.flushPendingUnlocks()
    }

    fun setTouchButtonsMask(mask: Int) {
        touchButtonsMask.set(mask and GbaInput.ALL_RELEASED)
        syncCombinedButtonsMask()
    }

    fun onPhysicalButtonEvent(mask: Int, pressed: Boolean) {
        val normalized = mask and GbaInput.ALL_RELEASED
        gamepadButtonsMask.updateAndGet { current ->
            if (pressed) {
                (current or normalized) and GbaInput.ALL_RELEASED
            } else {
                (current and normalized.inv()) and GbaInput.ALL_RELEASED
            }
        }
        syncCombinedButtonsMask()
    }

    fun onPhysicalAxisEvent(axisMask: Int) {
        gamepadAxisMask.set(axisMask and GbaInput.ALL_RELEASED)
        syncCombinedButtonsMask()
    }

    fun onButtonStateChanged(mask: Int, pressed: Boolean) {
        val normalized = mask and GbaInput.ALL_RELEASED
        touchButtonsMask.updateAndGet { current ->
            if (pressed) {
                (current or normalized) and GbaInput.ALL_RELEASED
            } else {
                (current and normalized.inv()) and GbaInput.ALL_RELEASED
            }
        }
        syncCombinedButtonsMask()
    }

    fun clearPhysicalInputs() {
        gamepadButtonsMask.set(0)
        gamepadAxisMask.set(0)
        syncCombinedButtonsMask()
    }

    fun clearAllInputs() {
        touchButtonsMask.set(0)
        gamepadButtonsMask.set(0)
        gamepadAxisMask.set(0)
        engine.resetAllButtons()
    }

    private fun syncCombinedButtonsMask() {
        val combined = (touchButtonsMask.get() or gamepadButtonsMask.get() or gamepadAxisMask.get()) and
            GbaInput.ALL_RELEASED
        engine.setButtonsMask(combined)
    }

    fun setOverlayModalOpen(open: Boolean) {
        _isOverlayModalOpen.value = open
        if (open) {
            clearPhysicalInputs()
        }
    }

    fun enterLayoutEditor() {
        if (_isEditingLayout.value) return
        wasPausedBeforeEditing = gbaCore.isPaused
        if (gbaCore.isRunning && !gbaCore.isPaused) {
            gbaCore.pause()
        }
        clearAllInputs()
        _isEditingLayout.value = true
    }

    fun exitLayoutEditor() {
        if (!_isEditingLayout.value) return
        _isEditingLayout.value = false
        if (gbaCore.isRunning && !wasPausedBeforeEditing) {
            gbaCore.resume()
            updateFrameskip()
        }
    }

    fun saveOrientationLayout(isLandscape: Boolean, config: OrientationLayoutConfig) {
        val activeId = _activeLayoutProfile.value.id
        val updated = LayoutProfileHelper.saveOrientationLayout(userPrefs, activeId, isLandscape, config)
        _layoutProfiles.value = updated
        _activeLayoutProfile.value = LayoutProfileHelper.resolveActiveProfile(updated, userPrefs.activeLayoutProfileId)
        _statusMessage.value = if (isLandscape) {
            "Layout Landscape salvo no perfil '${_activeLayoutProfile.value.name}'!"
        } else {
            "Layout Portrait salvo no perfil '${_activeLayoutProfile.value.name}'!"
        }
    }

    fun selectLayoutProfile(profileId: String) {
        val profiles = LayoutProfileHelper.selectProfile(userPrefs, profileId)
        _layoutProfiles.value = profiles
        _activeLayoutProfile.value = LayoutProfileHelper.resolveActiveProfile(profiles, userPrefs.activeLayoutProfileId)
    }

    fun createCustomLayoutProfile(name: String) {
        val result = LayoutProfileHelper.createCustomProfile(userPrefs, name, _activeLayoutProfile.value)
            ?: return
        val (updated, created) = result
        _layoutProfiles.value = updated
        _activeLayoutProfile.value = created
        _statusMessage.value = "Perfil '${created.name}' criado e ativado."
    }

    fun deleteCustomLayoutProfile(profileId: String) {
        val updated = LayoutProfileHelper.deleteCustomProfile(userPrefs, profileId)
        _layoutProfiles.value = updated
        _activeLayoutProfile.value = LayoutProfileHelper.resolveActiveProfile(updated, userPrefs.activeLayoutProfileId)
    }

    fun resetCurrentOrientationLayout(isLandscape: Boolean) {
        val activeId = _activeLayoutProfile.value.id
        val updated = LayoutProfileHelper.resetOrientationLayout(userPrefs, activeId, isLandscape)
        _layoutProfiles.value = updated
        _activeLayoutProfile.value = LayoutProfileHelper.resolveActiveProfile(updated, userPrefs.activeLayoutProfileId)
        _statusMessage.value = "Layout restaurado para o padrão."
    }

    fun setEcoMode(enabled: Boolean) {
        _isEcoMode.value = enabled
        updateFrameskip()
        _statusMessage.value = if (enabled) {
            "Modo Eco de Bateria ativado (Frameskip adaptativo)"
        } else {
            "Modo Eco desativado (60 FPS pleno)"
        }
    }

    fun updateFrameskip() {
        val recommended = powerManager.calculateRecommendedFrameskip(_isEcoMode.value)
        gbaCore.frameskip = recommended
    }

    fun onAppPause() {
        clearAllInputs()
        persistBatterySave()
        if (gbaCore.isRunning && !gbaCore.isPaused) {
            gbaCore.pause()
            _currentGame.value?.let { game ->
                if (raRepository.isLoggedIn()) {
                    viewModelScope.launch {
                        raRepository.sendPing(
                            gameId = game.id,
                            richPresence = "Pausado",
                            hardcore = _sessionHardcoreActive.value,
                            gameHash = currentRomHash.orEmpty()
                        )
                    }
                }
            }
        }
    }

    fun onAppResume() {
        if (_isEditingLayout.value) return
        if (gbaCore.isRunning && gbaCore.isPaused) {
            gbaCore.resume()
            updateFrameskip()
            _currentGame.value?.let { game ->
                if (raRepository.isLoggedIn()) {
                    viewModelScope.launch {
                        val rp = engine.getRichPresence().takeIf { it.isNotBlank() }
                            ?: "Jogando ${game.title}"
                        raRepository.sendPing(
                            gameId = game.id,
                            richPresence = rp,
                            hardcore = _sessionHardcoreActive.value,
                            gameHash = currentRomHash.orEmpty()
                        )
                    }
                }
            }
        }
    }

    fun loadRomFromUri(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _statusMessage.value = "Identificando ROM no RetroAchievements..."
            val romBytes = romRepo.readRomBytes(uri)
            val validation = RomSecurityValidator.validateRomBytes(romBytes)
            if (validation is RomSecurityValidator.ValidationResult.Invalid) {
                _statusMessage.value = "Arquivo inválido: ${validation.reason}"
                return@launch
            }

            val parseResult = romRepo.parseRom(uri, fileName)

            parseResult.onSuccess { romItem ->
                currentRomUri = uri
                currentRomFileName = romItem.fileName
                val md5 = romItem.md5Hash
                currentRomHash = md5
                richPresenceActivatedForGameId = null
                _sessionHardcoreActive.value = userPrefs.isHardcoreMode

                var gameAchievements = emptyList<Achievement>()
                var loadedGameInfo: GameInfo? = null

                val gameIdResult = raRepository.findGameByRomHash(md5)
                gameIdResult.onSuccess { gameId ->
                    val achResult = raRepository.getAchievements(gameId)
                    achResult.onSuccess { (info, list) ->
                        loadedGameInfo = info
                        _currentGame.value = info

                        val sessionResult = raRepository.startSession(
                            gameId = info.id,
                            romHash = md5,
                            hardcore = _sessionHardcoreActive.value
                        )
                        val unlockedIds = sessionResult.getOrDefault(emptySet())
                        if (raRepository.isLoggedIn()) {
                            raRepository.flushPendingUnlocks()
                        }

                        val updatedList = list.map { ach ->
                            if (unlockedIds.contains(ach.id)) ach.copy(isUnlocked = true) else ach
                        }

                        _achievements.value = updatedList
                        gameAchievements = updatedList
                        _statusMessage.value =
                            "Conectado ao RetroAchievements: ${info.title} (${list.size} conquistas)"

                        val updatedRomItem = romItem.copy(
                            gameId = info.id,
                            title = if (info.title.isNotBlank()) info.title else romItem.title,
                            unlockedAchievements = updatedList.count { it.isUnlocked },
                            totalAchievements = list.size,
                            totalPoints = list.sumOf { it.points },
                            badgeIconUrl = info.iconUrl
                        )
                        romRepo.saveRomItem(updatedRomItem)
                    }.onFailure {
                        _statusMessage.value = "Conquistas indisponíveis: ${it.localizedMessage}"
                    }
                }.onFailure {
                    _currentGame.value = null
                    _achievements.value = emptyList()
                    _statusMessage.value = "Jogo não catalogado no RetroAchievements para esta ROM."
                }

                val existingSave = saveRepository.loadBatteryRam(md5)
                if (existingSave != null && existingSave.isNotEmpty()) {
                    _statusMessage.value = "Progresso salvo recuperado (${existingSave.size} bytes)!"
                }

                refreshSaveSlots()

                val savedCheats = cheatRepository.loadCheats(md5)
                _cheats.value = savedCheats

                val finalRom = if (romBytes.isNotEmpty()) romBytes else ByteArray(1024)
                val loadedSuccessfully = loadRom(
                    romBytes = finalRom,
                    romHash = md5,
                    gameAchievements = gameAchievements,
                    saveBytes = existingSave,
                    richPresencePatch = loadedGameInfo?.richPresencePatch.orEmpty(),
                    gameId = loadedGameInfo?.id
                )
                if (!loadedSuccessfully) {
                    _statusMessage.value = "Falha ao carregar a ROM no motor de emulação."
                }
            }.onFailure {
                _statusMessage.value = "Erro ao carregar ROM: ${it.localizedMessage}"
            }
        }
    }

    /**
     * Carrega a ROM diretamente no motor de emulação, reiniciando o estado de Hardcore da sessão
     * conforme a preferência atual e aplicando Rich Presence e Cheats elegíveis.
     */
    fun loadRom(
        romBytes: ByteArray,
        romHash: String,
        gameAchievements: List<Achievement> = emptyList(),
        saveBytes: ByteArray? = null,
        richPresencePatch: String = "",
        gameId: Long? = null
    ): Boolean {
        currentRomHash = romHash
        richPresenceActivatedForGameId = null
        _sessionHardcoreActive.value = userPrefs.isHardcoreMode
        clearAllInputs()

        val loadedSuccessfully = gbaCore.loadRom(romBytes, gameAchievements, saveBytes)
        if (!loadedSuccessfully) {
            isRomSessionLoaded = false
            return false
        }

        isRomSessionLoaded = true
        if (richPresencePatch.isNotBlank()) {
            engine.activateRichPresence(richPresencePatch)
            richPresenceActivatedForGameId = gameId
        }
        applyCheatsToEngine(_cheats.value)
        gbaCore.start()
        return true
    }

    /**
     * Reinicia a sessão atual, reavaliando Hardcore da preferência global e recarregando a ROM
     * quando uma URI ativa estiver associada.
     */
    fun reset() {
        clearAllInputs()
        _sessionHardcoreActive.value = userPrefs.isHardcoreMode
        richPresenceActivatedForGameId = null

        val uri = currentRomUri
        val fileName = currentRomFileName
        if (uri != null && fileName != null) {
            persistBatterySave()
            gbaCore.stop()
            isRomSessionLoaded = false
            loadRomFromUri(uri, fileName)
        } else {
            applyCheatsToEngine(_cheats.value)
        }
    }

    fun getSavedRoms(): List<RomItem> = romRepo.getSavedRoms()

    internal fun handleAchievementUnlock(achievement: Achievement) {
        val alreadyUnlocked = _achievements.value.firstOrNull { it.id == achievement.id }?.isUnlocked == true
        if (alreadyUnlocked) return

        _achievements.value = _achievements.value.map {
            if (it.id == achievement.id) it.copy(isUnlocked = true) else it
        }
        achievementOverlayChannel.trySend(achievement.copy(isUnlocked = true))

        if (raRepository.isLoggedIn()) {
            val hardcoreSnapshot = _sessionHardcoreActive.value
            val hashSnapshot = currentRomHash.orEmpty()
            viewModelScope.launch {
                raRepository.unlockAchievement(
                    achievement = achievement,
                    hardcore = hardcoreSnapshot,
                    gameHash = hashSnapshot,
                    secondsSinceUnlock = 0L
                )
            }
        }
    }

    fun toggleTurbo() {
        val newState = !_isTurboActive.value
        _isTurboActive.value = newState
        val speed = if (newState) fastForwardSpeed.value else 1.0f
        engine.setSpeedMultiplier(speed)
    }

    fun setControllerOpacity(value: Float) {
        userPrefs.controllerOpacity = value.coerceIn(0.2f, 1.0f)
    }

    fun setHapticFeedback(enabled: Boolean) {
        userPrefs.hapticFeedbackEnabled = enabled
    }

    fun setFastForwardSpeed(speed: Float) {
        val clamped = speed.coerceIn(1.5f, 5.0f)
        userPrefs.fastForwardSpeed = clamped
        if (_isTurboActive.value) {
            engine.setSpeedMultiplier(clamped)
        }
    }

    fun setHideControls(hidden: Boolean) {
        userPrefs.hideControls = hidden
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

        lastSaveJob = viewModelScope.launch {
            withContext(NonCancellable) {
                val saved = saveRepository.saveBatteryRam(hash, saveRam)
                if (saved) {
                    _statusMessage.value = "Progresso do cartucho salvo com sucesso!"
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
        // Save State é permitido mesmo no Modo Hardcore; apenas Load State e Cheats são bloqueados.
        val hash = currentRomHash
        if (hash == null) {
            _statusMessage.value = "Nenhum jogo ativo para salvar estado"
            return
        }

        val stateBytes = engine.saveState()
        if (stateBytes != null && stateBytes.isNotEmpty()) {
            lastStateJob = viewModelScope.launch {
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
        if (_sessionHardcoreActive.value) {
            _statusMessage.value = "Load State desativado no Modo Hardcore!"
            return
        }
        val hash = currentRomHash
        if (hash == null) {
            _statusMessage.value = "Nenhum jogo ativo para carregar estado"
            return
        }

        lastStateJob = viewModelScope.launch {
            val bytes = saveRepository.loadState(hash, slot)
            if (bytes == null || bytes.isEmpty()) {
                _statusMessage.value = "Nenhum estado salvo encontrado no Slot $slot"
                return@launch
            }
            val success = engine.loadState(bytes)
            _statusMessage.value = if (success) {
                "Estado restaurado com sucesso do Slot $slot!"
            } else {
                "Falha ao restaurar Save State"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun applyCheatsToEngine(cheatList: List<CheatCode>) {
        gbaCore.clearCheats()
        if (_sessionHardcoreActive.value) return

        cheatList.filter { it.isEnabled }.forEach { cheat ->
            gbaCore.addCheat(cheat.description, cheat.code, cheat.type.typeId, true)
        }
    }

    fun addCheat(
        description: String,
        code: String,
        type: CheatType = CheatType.AUTODETECT
    ) {
        if (_sessionHardcoreActive.value) {
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

        val newCheat = CheatCode(
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
        if (_sessionHardcoreActive.value) {
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
        achievementOverlayChannel.close()
        persistBatterySaveBlocking()
        gbaCore.stop()
        isRomSessionLoaded = false
    }
}
