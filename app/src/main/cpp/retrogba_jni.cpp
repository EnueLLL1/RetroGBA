#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <vector>

// Headers do núcleo mGBA em C puro
#include <mgba/core/core.h>
#include <mgba/gba/core.h>
#include <mgba/gba/interface.h>
#include <mgba-util/vfs.h>

// Headers da biblioteca oficial rcheevos (RetroAchievements em C)
#include <rc_runtime.h>
#include <rc_consoles.h>

#define LOG_TAG "RetroGbaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int GBA_WIDTH = GBA_VIDEO_HORIZONTAL_PIXELS; // 240
static constexpr int GBA_HEIGHT = GBA_VIDEO_VERTICAL_PIXELS;  // 160

// Instância singleton do núcleo de emulação mGBA
static struct mCore* s_core = nullptr;

// Instância singleton do runtime oficial rcheevos
static rc_runtime_t s_rcRuntime;
static bool s_rcInitialized = false;
static std::vector<uint32_t> s_triggeredAchievements;

// Framebuffer nativo (RGBA_8888 / 240x160)
static uint32_t s_framebuffer[GBA_WIDTH * GBA_HEIGHT];
static jobject s_directVideoBuffer = nullptr;

// Buffer circular de áudio estéreo (PCM 16-bit estéreo)
static constexpr size_t AUDIO_BUFFER_CAPACITY = 16384; // 16K samples
static int16_t s_audioBuffer[AUDIO_BUFFER_CAPACITY];
static size_t s_audioWritePos = 0;
static size_t s_audioReadPos = 0;
static unsigned s_audioSampleRate = 32768;

static void audioRateChangedCb(struct mAVStream* /* stream */, unsigned rate) {
    s_audioSampleRate = rate;
    LOGI("mGBA taxa de amostragem de áudio alterada para %u Hz", rate);
}

static void postAudioFrameCb(struct mAVStream* /* stream */, int16_t left, int16_t right) {
    size_t nextLeft = (s_audioWritePos + 1) % AUDIO_BUFFER_CAPACITY;
    size_t nextRight = (s_audioWritePos + 2) % AUDIO_BUFFER_CAPACITY;
    if (nextRight != s_audioReadPos && nextLeft != s_audioReadPos) {
        s_audioBuffer[s_audioWritePos] = left;
        s_audioBuffer[nextLeft] = right;
        s_audioWritePos = nextRight;
    }
}

static struct mAVStream s_avStream = {
    nullptr, // videoDimensionsChanged
    audioRateChangedCb,
    nullptr, // postVideoFrame
    postAudioFrameCb,
    nullptr  // postAudioBuffer
};

// Callback de leitura de memória do rcheevos mapeado para o barramento do Game Boy Advance
static uint32_t rc_read_memory_cb(uint32_t address, uint8_t* buffer, uint32_t num_bytes, void* /* ud */) {
    if (!s_core || buffer == nullptr || num_bytes == 0) return 0;

    for (uint32_t i = 0; i < num_bytes; ++i) {
        uint32_t addr = address + i;
        uint32_t gbaAddr;
        if (addr >= 0x02000000) {
            gbaAddr = addr;
        } else if (addr <= 0x007FFF) {
            // IWRAM (32KB): 0x03000000
            gbaAddr = 0x03000000 + addr;
        } else if (addr <= 0x047FFF) {
            // EWRAM (256KB): 0x02000000
            gbaAddr = 0x02000000 + (addr - 0x008000);
        } else if (addr <= 0x057FFF) {
            // Game Pak SRAM / Flash: 0x0E000000
            gbaAddr = 0x0E000000 + (addr - 0x048000);
        } else {
            gbaAddr = addr;
        }
        buffer[i] = static_cast<uint8_t>(s_core->busRead8(s_core, gbaAddr));
    }
    return num_bytes;
}

// Callback de eventos de gatilho do rcheevos
static void rc_event_handler_cb(const rc_runtime_event_t* runtime_event) {
    if (runtime_event == nullptr) return;

    if (runtime_event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_TRIGGERED) {
        LOGI("rcheevos: Conquista disparada com sucesso! ID=%u", runtime_event->id);
        s_triggeredAchievements.push_back(runtime_event->id);
    }
}

// Cópia em memória da ROM carregada
static std::vector<uint8_t> s_romData;
static uint16_t s_currentButtons = 0;
static bool s_isLoaded = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeInit(JNIEnv* /* env */, jobject /* thiz */) {
    if (s_core != nullptr) {
        s_core->deinit(s_core);
        s_core = nullptr;
    }

    s_core = GBACoreCreate();
    if (s_core == nullptr) {
        LOGE("Falha crítica ao instanciar GBACoreCreate() do mGBA");
        return JNI_FALSE;
    }

    if (!s_core->init(s_core)) {
        LOGE("Falha ao inicializar struct mCore do mGBA");
        s_core = nullptr;
        return JNI_FALSE;
    }

    // Vincula o buffer de vídeo nativo de 240x160 ao rasterizador PPU do mGBA
    s_core->setVideoBuffer(s_core, reinterpret_cast<color_t*>(s_framebuffer), GBA_WIDTH);

    // Conecta o pipeline de áudio estéreo
    s_core->setAVStream(s_core, &s_avStream);

    std::memset(s_framebuffer, 0, sizeof(s_framebuffer));
    s_audioWritePos = 0;
    s_audioReadPos = 0;
    s_currentButtons = 0;
    s_isLoaded = false;

    // Inicializa runtime oficial do rcheevos
    if (s_rcInitialized) {
        rc_runtime_destroy(&s_rcRuntime);
    }
    rc_runtime_init(&s_rcRuntime);
    s_rcInitialized = true;
    s_triggeredAchievements.clear();

    LOGI("RetroGba: Núcleo mGBA + motor oficial rcheevos inicializados com sucesso.");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeLoadRom(JNIEnv* env, jobject /* thiz */, jbyteArray romBytes, jint size) {
    static constexpr jint MAX_ROM_SIZE = 64 * 1024 * 1024; // 64 MB
    if (romBytes == nullptr || size < 192 || size > MAX_ROM_SIZE) {
        LOGE("ROM inválida ou fora dos limites de segurança (192 bytes a 64MB, recebido: %d)", size);
        return JNI_FALSE;
    }

    if (s_core == nullptr) {
        s_core = GBACoreCreate();
        if (s_core == nullptr || !s_core->init(s_core)) {
            LOGE("Não foi possível preparar o núcleo mGBA para carregar a ROM");
            return JNI_FALSE;
        }
        s_core->setVideoBuffer(s_core, reinterpret_cast<color_t*>(s_framebuffer), GBA_WIDTH);
        s_core->setAVStream(s_core, &s_avStream);
    }

    s_romData.resize(size);
    env->GetByteArrayRegion(romBytes, 0, size, reinterpret_cast<jbyte*>(s_romData.data()));

    struct VFile* romVf = VFileMemChunk(s_romData.data(), s_romData.size());
    if (romVf == nullptr) {
        LOGE("Falha ao criar descritor VFileMemChunk em memória para a ROM");
        return JNI_FALSE;
    }

    if (!s_core->loadROM(s_core, romVf)) {
        LOGE("mGBA rejeitou o carregamento da ROM (formato de imagem inválido)");
        return JNI_FALSE;
    }

    s_core->reset(s_core);
    s_audioWritePos = 0;
    s_audioReadPos = 0;
    s_isLoaded = true;

    if (s_rcInitialized) {
        rc_runtime_reset(&s_rcRuntime);
    }
    s_triggeredAchievements.clear();

    char title[13] = {0};
    s_core->getGameTitle(s_core, title);
    LOGI("mGBA: ROM carregada com sucesso! Título: [%s] (%d bytes)", title, size);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeRunFrame(JNIEnv* /* env */, jobject /* thiz */) {
    if (s_core == nullptr || !s_isLoaded) return;

    // 1. Atualiza o estado dos botões antes do ciclo de frame da CPU
    s_core->setKeys(s_core, s_currentButtons);

    // 2. Executa as instruções da CPU ARM7TDMI, rasterização PPU e síntese APU para 1 frame (1/60s)
    s_core->runFrame(s_core);

    // 3. Avalia condições do RetroAchievements via biblioteca oficial rcheevos
    if (s_rcInitialized) {
        rc_runtime_do_frame(&s_rcRuntime, rc_event_handler_cb, rc_read_memory_cb, nullptr, nullptr);
    }
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeSetButtons(JNIEnv* /* env */, jobject /* thiz */, jint buttonMask) {
    s_currentButtons = static_cast<uint16_t>(buttonMask & 0x03FF);
}

JNIEXPORT jobject JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetVideoBuffer(JNIEnv* env, jobject /* thiz */) {
    if (s_directVideoBuffer == nullptr) {
        jobject localBuffer = env->NewDirectByteBuffer(s_framebuffer, sizeof(s_framebuffer));
        if (localBuffer != nullptr) {
            s_directVideoBuffer = env->NewGlobalRef(localBuffer);
            env->DeleteLocalRef(localBuffer);
        }
    }
    return s_directVideoBuffer;
}

JNIEXPORT jint JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetAudioSamples(JNIEnv* env, jobject /* thiz */, jshortArray buffer, jint maxSamples) {
    if (buffer == nullptr || maxSamples <= 0) return 0;

    jsize len = env->GetArrayLength(buffer);
    int toRead = std::min(static_cast<int>(len), static_cast<int>(maxSamples));

    std::vector<int16_t> temp(toRead);
    int copied = 0;
    while (copied < toRead && s_audioReadPos != s_audioWritePos) {
        temp[copied++] = s_audioBuffer[s_audioReadPos];
        s_audioReadPos = (s_audioReadPos + 1) % AUDIO_BUFFER_CAPACITY;
    }

    if (copied > 0) {
        env->SetShortArrayRegion(buffer, 0, copied, reinterpret_cast<const jshort*>(temp.data()));
    }
    return copied;
}

JNIEXPORT jint JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetAudioSampleRate(JNIEnv* /* env */, jobject /* thiz */) {
    return static_cast<jint>(s_audioSampleRate);
}

// Métodos de ativação e verificação de conquistas via rcheevos
JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeActivateAchievement(JNIEnv* env, jobject /* thiz */, jlong id, jstring memaddr) {
    if (!s_rcInitialized || memaddr == nullptr) return JNI_FALSE;

    jsize strLen = env->GetStringLength(memaddr);
    if (strLen == 0 || strLen > 4096) {
        LOGE("rcheevos: Parâmetro memaddr inválido ou excede 4096 caracteres");
        return JNI_FALSE;
    }

    const char* str = env->GetStringUTFChars(memaddr, nullptr);
    if (str == nullptr) return JNI_FALSE;

    int result = rc_runtime_activate_achievement(&s_rcRuntime, static_cast<uint32_t>(id), str, nullptr, 0);
    env->ReleaseStringUTFChars(memaddr, str);

    if (result != RC_OK) {
        LOGE("rcheevos: Erro ao ativar conquista ID=%lld (código: %d)", static_cast<long long>(id), result);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeDeactivateAchievement(JNIEnv* /* env */, jobject /* thiz */, jlong id) {
    if (s_rcInitialized) {
        rc_runtime_deactivate_achievement(&s_rcRuntime, static_cast<uint32_t>(id));
    }
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeClearAchievements(JNIEnv* /* env */, jobject /* thiz */) {
    if (s_rcInitialized) {
        rc_runtime_destroy(&s_rcRuntime);
        rc_runtime_init(&s_rcRuntime);
        s_triggeredAchievements.clear();
    }
}

JNIEXPORT jlongArray JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetTriggeredAchievements(JNIEnv* env, jobject /* thiz */) {
    if (s_triggeredAchievements.empty()) return nullptr;

    jsize count = static_cast<jsize>(s_triggeredAchievements.size());
    jlongArray arr = env->NewLongArray(count);
    if (arr != nullptr) {
        std::vector<jlong> copy(count);
        for (jsize i = 0; i < count; ++i) {
            copy[i] = static_cast<jlong>(s_triggeredAchievements[i]);
        }
        env->SetLongArrayRegion(arr, 0, count, copy.data());
    }
    s_triggeredAchievements.clear();
    return arr;
}

JNIEXPORT jbyteArray JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetSaveRam(JNIEnv* env, jobject /* thiz */) {
    if (s_core == nullptr || !s_isLoaded) return nullptr;

    void* sramData = nullptr;
    size_t sramSize = s_core->savedataClone(s_core, &sramData);
    if (sramSize == 0 || sramData == nullptr) {
        return nullptr;
    }

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(sramSize));
    if (arr != nullptr) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(sramSize), reinterpret_cast<const jbyte*>(sramData));
    }
    free(sramData);
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeLoadSaveRam(JNIEnv* env, jobject /* thiz */, jbyteArray saveBytes) {
    if (s_core == nullptr || !s_isLoaded || saveBytes == nullptr) return JNI_FALSE;

    jsize len = env->GetArrayLength(saveBytes);
    static constexpr jsize MAX_SAVE_RAM_SIZE = 1024 * 1024; // 1 MB
    if (len == 0 || len > MAX_SAVE_RAM_SIZE) return JNI_FALSE;

    std::vector<uint8_t> buffer(len);
    env->GetByteArrayRegion(saveBytes, 0, len, reinterpret_cast<jbyte*>(buffer.data()));

    bool ok = s_core->savedataRestore(s_core, buffer.data(), len, true);
    LOGI("mGBA: Save RAM restaurada (%d bytes, sucesso=%d).", len, ok);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeSaveState(JNIEnv* env, jobject /* thiz */) {
    if (s_core == nullptr || !s_isLoaded) return nullptr;

    size_t stateSize = s_core->stateSize(s_core);
    if (stateSize == 0) {
        LOGE("mGBA informou stateSize == 0");
        return nullptr;
    }

    std::vector<uint8_t> stateBuffer(stateSize);
    if (!s_core->saveState(s_core, stateBuffer.data())) {
        LOGE("Falha ao serializar o save state");
        return nullptr;
    }

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(stateSize));
    if (arr != nullptr) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(stateSize), reinterpret_cast<const jbyte*>(stateBuffer.data()));
    }
    LOGI("mGBA: Save State capturado com sucesso (%zu bytes).", stateSize);
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeLoadState(JNIEnv* env, jobject /* thiz */, jbyteArray stateBytes) {
    if (s_core == nullptr || !s_isLoaded || stateBytes == nullptr) return JNI_FALSE;

    jsize len = env->GetArrayLength(stateBytes);
    static constexpr jsize MAX_STATE_SIZE = 8 * 1024 * 1024; // 8 MB
    if (len == 0 || len > MAX_STATE_SIZE) return JNI_FALSE;

    std::vector<uint8_t> stateBuffer(len);
    env->GetByteArrayRegion(stateBytes, 0, len, reinterpret_cast<jbyte*>(stateBuffer.data()));

    bool ok = s_core->loadState(s_core, stateBuffer.data());
    if (!ok) {
        LOGE("mGBA rejeitou os dados do Save State");
        return JNI_FALSE;
    }

    LOGI("mGBA: Save State restaurado com sucesso (%d bytes).", len);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeReadMemory(JNIEnv* /* env */, jobject /* thiz */, jlong address, jint size) {
    if (s_core == nullptr || !s_isLoaded) return 0;

    uint32_t addr = static_cast<uint32_t>(address);
    if (size == 1) {
        return s_core->busRead8(s_core, addr);
    } else if (size == 2) {
        return s_core->busRead16(s_core, addr);
    } else if (size == 4) {
        return s_core->busRead32(s_core, addr);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeDestroy(JNIEnv* env, jobject /* thiz */) {
    if (s_rcInitialized) {
        rc_runtime_destroy(&s_rcRuntime);
        s_rcInitialized = false;
        s_triggeredAchievements.clear();
    }

    if (s_core != nullptr) {
        s_core->deinit(s_core);
        s_core = nullptr;
    }

    if (s_directVideoBuffer != nullptr) {
        env->DeleteGlobalRef(s_directVideoBuffer);
        s_directVideoBuffer = nullptr;
    }

    s_romData.clear();
    s_audioWritePos = 0;
    s_audioReadPos = 0;
    s_isLoaded = false;
    LOGI("RetroGba: Núcleo mGBA e rcheevos destruídos com sucesso.");
}

} // extern "C"
