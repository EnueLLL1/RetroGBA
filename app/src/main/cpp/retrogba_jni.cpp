#include <algorithm>
#include <android/log.h>
#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <jni.h>
#include <mutex>
#include <string>
#include <vector>

// Headers do núcleo mGBA em C puro
#include <mgba-util/vfs.h>
#include <mgba/core/cheats.h>
#include <mgba/core/config.h>
#include <mgba/core/core.h>
#include <mgba/gba/core.h>
#include <mgba/gba/interface.h>
#include <mgba/internal/gba/cheats.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/memory.h>

// Headers da biblioteca oficial rcheevos (RetroAchievements em C)
#include <rc_consoles.h>
#include <rc_runtime.h>

#define LOG_TAG "RetroGbaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int GBA_WIDTH = GBA_VIDEO_HORIZONTAL_PIXELS; // 240
static constexpr int GBA_HEIGHT = GBA_VIDEO_VERTICAL_PIXELS;  // 160

// Mutex de sincronização para garantir acesso thread-safe ao núcleo mGBA
static std::mutex s_coreMutex;

// Instância singleton do núcleo de emulação mGBA
static struct mCore *s_core = nullptr;

// Instância singleton do runtime oficial rcheevos
static rc_runtime_t s_rcRuntime;
static bool s_rcInitialized = false;
static std::mutex s_achievementsMutex;
static std::vector<uint32_t> s_triggeredAchievements;

// Framebuffer nativo (RGBA_8888 / 240x160)
static uint32_t s_framebuffer[GBA_WIDTH * GBA_HEIGHT];
static jobject s_directVideoBuffer = nullptr;

// Buffer circular de áudio estéreo (PCM 16-bit estéreo)
static constexpr size_t AUDIO_BUFFER_CAPACITY = 16384; // 16K samples
static int16_t s_audioBuffer[AUDIO_BUFFER_CAPACITY];
static std::atomic<size_t> s_audioWritePos{0};
static std::atomic<size_t> s_audioReadPos{0};
static unsigned s_audioSampleRate = 32768;

static void audioRateChangedCb(struct mAVStream * /* stream */, unsigned rate) {
  s_audioSampleRate = rate;
  LOGI("mGBA taxa de amostragem de áudio alterada para %u Hz", rate);
}

static void postAudioFrameCb(struct mAVStream * /* stream */, int16_t left,
                             int16_t right) {
  size_t writePos = s_audioWritePos.load(std::memory_order_relaxed);
  size_t readPos = s_audioReadPos.load(std::memory_order_acquire);
  size_t nextLeft = (writePos + 1) % AUDIO_BUFFER_CAPACITY;
  size_t nextRight = (writePos + 2) % AUDIO_BUFFER_CAPACITY;
  if (nextRight != readPos && nextLeft != readPos) {
    s_audioBuffer[writePos] = left;
    s_audioBuffer[nextLeft] = right;
    s_audioWritePos.store(nextRight, std::memory_order_release);
  }
}

static struct mAVStream s_avStream = {
    nullptr, // videoDimensionsChanged
    audioRateChangedCb,
    nullptr, // postVideoFrame
    postAudioFrameCb,
    nullptr // postAudioBuffer
};

// Callback de leitura de memória do rcheevos com leitura direta via memcpy dos
// bancos do GBA
static uint32_t rc_read_memory_cb(uint32_t address, uint8_t *buffer,
                                  uint32_t num_bytes, void * /* ud */) {
  if (!s_core || !s_core->board || buffer == nullptr || num_bytes == 0)
    return 0;

  struct GBA *gba = reinterpret_cast<struct GBA *>(s_core->board);
  uint32_t bytes_read = 0;

  while (num_bytes > 0) {
    if (address < 0x008000) {
      // IWRAM (32KB): 0x000000 - 0x007FFF (no barramento: 0x03000000)
      if (!gba->memory.iwram)
        break;
      uint32_t offset = address;
      uint32_t avail = 0x008000 - offset;
      uint32_t to_read = (num_bytes < avail) ? num_bytes : avail;
      std::memcpy(buffer + bytes_read,
                  reinterpret_cast<const uint8_t *>(gba->memory.iwram) + offset,
                  to_read);
      address += to_read;
      bytes_read += to_read;
      num_bytes -= to_read;
    } else if (address < 0x048000) {
      // EWRAM (256KB): 0x008000 - 0x047FFF (no barramento: 0x02000000)
      if (!gba->memory.wram)
        break;
      uint32_t offset = address - 0x008000;
      uint32_t avail = 0x040000 - offset;
      uint32_t to_read = (num_bytes < avail) ? num_bytes : avail;
      std::memcpy(buffer + bytes_read,
                  reinterpret_cast<const uint8_t *>(gba->memory.wram) + offset,
                  to_read);
      address += to_read;
      bytes_read += to_read;
      num_bytes -= to_read;
    } else if (address < 0x058000) {
      // SRAM / Save RAM (64KB): 0x048000 - 0x057FFF (no barramento: 0x0E000000)
      if (!gba->memory.savedata.data)
        break;
      uint32_t offset = address - 0x048000;
      uint32_t avail = 0x010000 - offset;
      uint32_t to_read = (num_bytes < avail) ? num_bytes : avail;
      std::memcpy(buffer + bytes_read, gba->memory.savedata.data + offset,
                  to_read);
      address += to_read;
      bytes_read += to_read;
      num_bytes -= to_read;
    } else {
      // Endereço fora dos limites mapeados pelo rcheevos
      break;
    }
  }

  return bytes_read;
}

// Callback de eventos de gatilho do rcheevos
static void rc_event_handler_cb(const rc_runtime_event_t *runtime_event) {
  if (runtime_event == nullptr)
    return;

  if (runtime_event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_TRIGGERED) {
    LOGI("rcheevos: Conquista disparada com sucesso! ID=%u", runtime_event->id);
    std::lock_guard<std::mutex> lock(s_achievementsMutex);
    s_triggeredAchievements.push_back(runtime_event->id);
  }
}

static void ensureRcInitialized() {
  if (!s_rcInitialized) {
    rc_runtime_init(&s_rcRuntime);
    s_rcInitialized = true;
    std::lock_guard<std::mutex> lock(s_achievementsMutex);
    s_triggeredAchievements.clear();
    LOGI("RetroGba: Runtime rcheevos inicializado com sucesso.");
  }
}

// Cópia em memória da ROM carregada
static std::vector<uint8_t> s_romData;
static std::atomic<uint16_t> s_currentButtons{0};
static bool s_isLoaded = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeInit(JNIEnv * /* env */,
                                                       jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core != nullptr) {
    if (s_isLoaded) {
      s_core->unloadROM(s_core);
      s_isLoaded = false;
    }
    // core->deinit() liberta o próprio core (free), por isso a config
    // tem de ser destruída ANTES, senão acedemos a memória já libertada.
    mCoreConfigDeinit(&s_core->config);
    s_core->deinit(s_core);
    s_core = nullptr;
  }

  s_core = GBACoreCreate();
  if (s_core == nullptr) {
    LOGE("Falha crítica ao instanciar GBACoreCreate() do mGBA");
    return JNI_FALSE;
  }

  mCoreInitConfig(s_core, nullptr);

  if (!s_core->init(s_core)) {
    LOGE("Falha ao inicializar struct mCore do mGBA");
    mCoreConfigDeinit(&s_core->config);
    s_core->deinit(s_core);
    s_core = nullptr;
    return JNI_FALSE;
  }

  // Vincula o buffer de vídeo nativo de 240x160 ao rasterizador PPU do mGBA
  s_core->setVideoBuffer(s_core, reinterpret_cast<color_t *>(s_framebuffer),
                         GBA_WIDTH);

  // Conecta o pipeline de áudio estéreo
  s_core->setAVStream(s_core, &s_avStream);

  std::memset(s_framebuffer, 0, sizeof(s_framebuffer));
  s_audioWritePos.store(0, std::memory_order_relaxed);
  s_audioReadPos.store(0, std::memory_order_relaxed);
  s_currentButtons.store(0, std::memory_order_relaxed);
  s_isLoaded = false;

  // Garante inicialização limpa do rcheevos
  if (s_rcInitialized) {
    rc_runtime_destroy(&s_rcRuntime);
    s_rcInitialized = false;
  }
  ensureRcInitialized();

  LOGI("RetroGba: Núcleo mGBA + motor oficial rcheevos inicializados com "
       "sucesso.");
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeLoadRom(JNIEnv *env,
                                                          jobject /* thiz */,
                                                          jbyteArray romBytes,
                                                          jint size) {
  static constexpr jint MAX_ROM_SIZE = 64 * 1024 * 1024; // 64 MB
  if (romBytes == nullptr) {
    LOGE("ROM inválida: ponteiro nulo");
    return JNI_FALSE;
  }

  jsize arrayLen = env->GetArrayLength(romBytes);
  if (size < 192 || size > MAX_ROM_SIZE || size > arrayLen) {
    LOGE("ROM inválida ou fora dos limites de segurança (192 bytes a 64MB, "
         "recebido: %d, arrayLen: %d)",
         size, arrayLen);
    return JNI_FALSE;
  }

  std::lock_guard<std::mutex> lock(s_coreMutex);

  if (s_core == nullptr) {
    s_core = GBACoreCreate();
    if (s_core == nullptr) {
      LOGE("Não foi possível preparar o núcleo mGBA para carregar a ROM");
      return JNI_FALSE;
    }
    mCoreInitConfig(s_core, nullptr);
    if (!s_core->init(s_core)) {
      LOGE("Não foi possível inicializar o núcleo mGBA para carregar a ROM");
      mCoreConfigDeinit(&s_core->config);
      s_core->deinit(s_core);
      s_core = nullptr;
      return JNI_FALSE;
    }
    s_core->setVideoBuffer(s_core, reinterpret_cast<color_t *>(s_framebuffer),
                           GBA_WIDTH);
    s_core->setAVStream(s_core, &s_avStream);
  } else if (s_isLoaded) {
    s_core->unloadROM(s_core);
    s_isLoaded = false;
  }

  s_romData.resize(size);
  env->GetByteArrayRegion(romBytes, 0, size,
                          reinterpret_cast<jbyte *>(s_romData.data()));
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    LOGE("Exceção JNI capturada ao copiar bytes da ROM");
    s_romData.clear();
    s_romData.shrink_to_fit();
    return JNI_FALSE;
  }

  struct VFile *romVf = VFileMemChunk(s_romData.data(), s_romData.size());
  if (romVf == nullptr) {
    LOGE("Falha ao criar descritor VFileMemChunk em memória para a ROM");
    s_romData.clear();
    s_romData.shrink_to_fit();
    return JNI_FALSE;
  }

  if (!s_core->loadROM(s_core, romVf)) {
    LOGE("mGBA rejeitou o carregamento da ROM (formato de imagem inválido)");
    romVf->close(romVf);
    s_romData.clear();
    s_romData.shrink_to_fit();
    return JNI_FALSE;
  }

  // VFileMemChunk copia o buffer internamente para o core mGBA;
  // liberamos s_romData para economizar até dezenas de megabytes de RAM.
  s_romData.clear();
  s_romData.shrink_to_fit();

  s_core->reset(s_core);
  s_audioWritePos.store(0, std::memory_order_relaxed);
  s_audioReadPos.store(0, std::memory_order_relaxed);
  s_isLoaded = true;

  // Inicialização determinística do rcheevos mesmo sem chamada prévia de
  // nativeInit
  ensureRcInitialized();
  rc_runtime_reset(&s_rcRuntime);
  s_triggeredAchievements.clear();

  char title[13] = {0};
  s_core->getGameTitle(s_core, title);
  LOGI("mGBA: ROM carregada com sucesso! Título: [%s] (%d bytes)", title, size);
  return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeRunFrame(JNIEnv * /* env */,
                                                           jobject /* thiz */) {
  std::unique_lock<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded)
    return;

  // 1. Atualiza o estado dos botões antes do ciclo de frame da CPU
  s_core->setKeys(s_core, s_currentButtons.load(std::memory_order_relaxed));

  // 2. Executa as instruções da CPU ARM7TDMI, rasterização PPU e síntese APU
  // para 1 frame (1/60s)
  s_core->runFrame(s_core);

  // 3. Garante canal Alpha opaco (0xFF000000) nos pixels para o Bitmap
  // ARGB_8888 do Android
  for (size_t i = 0; i < GBA_WIDTH * GBA_HEIGHT; ++i) {
    s_framebuffer[i] |= 0xFF000000u;
  }

  // 4. Avalia condições do RetroAchievements via biblioteca oficial rcheevos
  if (s_rcInitialized) {
    rc_runtime_do_frame(&s_rcRuntime, rc_event_handler_cb, rc_read_memory_cb,
                        nullptr, nullptr);
  }
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeSetButtons(JNIEnv * /* env */,
                                                             jobject /* thiz */,
                                                             jint buttonMask) {
  s_currentButtons.store(static_cast<uint16_t>(buttonMask & 0x03FF),
                         std::memory_order_relaxed);
}

JNIEXPORT jobject JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetVideoBuffer(
    JNIEnv *env, jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_directVideoBuffer == nullptr) {
    jobject localBuffer =
        env->NewDirectByteBuffer(s_framebuffer, sizeof(s_framebuffer));
    if (localBuffer != nullptr) {
      s_directVideoBuffer = env->NewGlobalRef(localBuffer);
      env->DeleteLocalRef(localBuffer);
    }
  }
  return s_directVideoBuffer;
}

JNIEXPORT jint JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetAudioSamples(
    JNIEnv *env, jobject /* thiz */, jshortArray buffer, jint maxSamples) {
  if (buffer == nullptr || maxSamples <= 0)
    return 0;

  jsize len = env->GetArrayLength(buffer);
  int toRead = std::min(static_cast<int>(len), static_cast<int>(maxSamples));

  int16_t stackBuf[4096];
  int chunk = std::min(toRead, 4096);
  int copied = 0;

  size_t readPos = s_audioReadPos.load(std::memory_order_relaxed);
  size_t writePos = s_audioWritePos.load(std::memory_order_acquire);

  while (copied < chunk && readPos != writePos) {
    stackBuf[copied++] = s_audioBuffer[readPos];
    readPos = (readPos + 1) % AUDIO_BUFFER_CAPACITY;
  }

  if (copied > 0) {
    s_audioReadPos.store(readPos, std::memory_order_release);
    env->SetShortArrayRegion(buffer, 0, copied,
                             reinterpret_cast<const jshort *>(stackBuf));
  }
  return copied;
}

JNIEXPORT jint JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetAudioSampleRate(
    JNIEnv * /* env */, jobject /* thiz */) {
  return static_cast<jint>(s_audioSampleRate);
}

// Métodos de ativação e verificação de conquistas via rcheevos
JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeActivateAchievement(
    JNIEnv *env, jobject /* thiz */, jlong id, jstring memaddr) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  ensureRcInitialized();
  if (memaddr == nullptr)
    return JNI_FALSE;

  jsize strLen = env->GetStringLength(memaddr);
  if (strLen == 0 || strLen > 4096) {
    LOGE("rcheevos: Parâmetro memaddr inválido ou excede 4096 caracteres");
    return JNI_FALSE;
  }

  const char *str = env->GetStringUTFChars(memaddr, nullptr);
  if (str == nullptr)
    return JNI_FALSE;

  int result = rc_runtime_activate_achievement(
      &s_rcRuntime, static_cast<uint32_t>(id), str, nullptr, 0);
  env->ReleaseStringUTFChars(memaddr, str);

  if (result != RC_OK) {
    LOGE("rcheevos: Erro ao ativar conquista ID=%lld (código: %d)",
         static_cast<long long>(id), result);
    return JNI_FALSE;
  }

  LOGI("rcheevos: Conquista ID=%lld ativada com sucesso.",
       static_cast<long long>(id));
  return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeDeactivateAchievement(
    JNIEnv * /* env */, jobject /* thiz */, jlong id) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_rcInitialized) {
    rc_runtime_deactivate_achievement(&s_rcRuntime, static_cast<uint32_t>(id));
  }
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeClearAchievements(
    JNIEnv * /* env */, jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_rcInitialized) {
    rc_runtime_destroy(&s_rcRuntime);
    s_rcInitialized = false;
  }
  ensureRcInitialized();
  std::lock_guard<std::mutex> achLock(s_achievementsMutex);
  s_triggeredAchievements.clear();
}

JNIEXPORT jlongArray JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetTriggeredAchievements(
    JNIEnv *env, jobject /* thiz */) {
  std::vector<uint32_t> triggeredCopy;
  {
    std::lock_guard<std::mutex> lock(s_achievementsMutex);
    if (s_triggeredAchievements.empty())
      return nullptr;
    triggeredCopy.swap(s_triggeredAchievements);
  }

  jsize count = static_cast<jsize>(triggeredCopy.size());
  jlongArray arr = env->NewLongArray(count);
  if (arr != nullptr) {
    std::vector<jlong> copy(count);
    for (jsize i = 0; i < count; ++i) {
      copy[i] = static_cast<jlong>(triggeredCopy[i]);
    }
    env->SetLongArrayRegion(arr, 0, count, copy.data());
  }
  return arr;
}

JNIEXPORT jbyteArray JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeGetSaveRam(
    JNIEnv *env, jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded)
    return nullptr;

  void *sramData = nullptr;
  size_t sramSize = s_core->savedataClone(s_core, &sramData);
  if (sramSize == 0 || sramData == nullptr) {
    return nullptr;
  }

  jbyteArray arr = env->NewByteArray(static_cast<jsize>(sramSize));
  if (arr != nullptr) {
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(sramSize),
                            reinterpret_cast<const jbyte *>(sramData));
  }
  free(sramData);
  return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeLoadSaveRam(
    JNIEnv *env, jobject /* thiz */, jbyteArray saveBytes) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded || saveBytes == nullptr)
    return JNI_FALSE;

  jsize len = env->GetArrayLength(saveBytes);
  static constexpr jsize MAX_SAVE_RAM_SIZE = 1024 * 1024; // 1 MB
  if (len == 0 || len > MAX_SAVE_RAM_SIZE)
    return JNI_FALSE;

  std::vector<uint8_t> buffer(len);
  env->GetByteArrayRegion(saveBytes, 0, len,
                          reinterpret_cast<jbyte *>(buffer.data()));

  bool ok = s_core->savedataRestore(s_core, buffer.data(), len, true);
  LOGI("mGBA: Save RAM restaurada (%d bytes, sucesso=%d).", len, ok);
  return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeSaveState(
    JNIEnv *env, jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded)
    return nullptr;

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
    env->SetByteArrayRegion(
        arr, 0, static_cast<jsize>(stateSize),
        reinterpret_cast<const jbyte *>(stateBuffer.data()));
  }
  LOGI("mGBA: Save State capturado com sucesso (%zu bytes).", stateSize);
  return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeLoadState(
    JNIEnv *env, jobject /* thiz */, jbyteArray stateBytes) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded || stateBytes == nullptr)
    return JNI_FALSE;

  jsize len = env->GetArrayLength(stateBytes);
  static constexpr jsize MAX_STATE_SIZE = 8 * 1024 * 1024; // 8 MB
  if (len == 0 || len > MAX_STATE_SIZE)
    return JNI_FALSE;

  std::vector<uint8_t> stateBuffer(len);
  env->GetByteArrayRegion(stateBytes, 0, len,
                          reinterpret_cast<jbyte *>(stateBuffer.data()));

  bool ok = s_core->loadState(s_core, stateBuffer.data());
  if (!ok) {
    LOGE("mGBA rejeitou os dados do Save State");
    return JNI_FALSE;
  }

  LOGI("mGBA: Save State restaurado com sucesso (%d bytes).", len);
  return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeReadMemory(JNIEnv * /* env */,
                                                             jobject /* thiz */,
                                                             jlong address,
                                                             jint size) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded)
    return 0;

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

JNIEXPORT jboolean JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeAddCheat(
    JNIEnv *env, jobject /* thiz */, jstring description, jstring code,
    jint type, jboolean enabled) {
  if (code == nullptr)
    return JNI_FALSE;

  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core == nullptr || !s_isLoaded)
    return JNI_FALSE;

  struct mCheatDevice *dev = s_core->cheatDevice(s_core);
  if (dev == nullptr) {
    LOGE("mGBA: cheatDevice não inicializado no core");
    return JNI_FALSE;
  }

  const char *descStr = nullptr;
  if (description != nullptr) {
    descStr = env->GetStringUTFChars(description, nullptr);
    if (descStr == nullptr) {
      descStr = "Cheat";
    }
  } else {
    descStr = "Cheat";
  }

  const char *codeStr = env->GetStringUTFChars(code, nullptr);
  if (codeStr == nullptr) {
    if (description != nullptr && descStr != nullptr && strcmp(descStr, "Cheat") != 0) {
      env->ReleaseStringUTFChars(description, descStr);
    }
    return JNI_FALSE;
  }

  struct mCheatSet *set = dev->createSet(dev, descStr);
  if (set == nullptr) {
    LOGE("mGBA: Falha ao criar mCheatSet para '%s'", descStr);
    env->ReleaseStringUTFChars(code, codeStr);
    if (description != nullptr && descStr != nullptr && strcmp(descStr, "Cheat") != 0) {
      env->ReleaseStringUTFChars(description, descStr);
    }
    return JNI_FALSE;
  }

  set->enabled = (enabled == JNI_TRUE);

  int mgbaType = 0;
  if (type == 1)
    mgbaType = GBA_CHEAT_GAMESHARK;
  else if (type == 2)
    mgbaType = GBA_CHEAT_PRO_ACTION_REPLAY;
  else if (type == 3)
    mgbaType = GBA_CHEAT_CODEBREAKER;
  else if (type == 4)
    mgbaType = GBA_CHEAT_VBA;

  std::string fullCode(codeStr);
  std::vector<std::string> lines;
  size_t start = 0;
  for (size_t i = 0; i <= fullCode.length(); ++i) {
    if (i == fullCode.length() || fullCode[i] == '\n' || fullCode[i] == '\r' ||
        fullCode[i] == ';') {
      if (i > start) {
        std::string line = fullCode.substr(start, i - start);
        line.erase(0, line.find_first_not_of(" \t"));
        line.erase(line.find_last_not_of(" \t") + 1);
        if (!line.empty()) {
          lines.push_back(line);
        }
      }
      start = i + 1;
    }
  }

  bool allLinesAdded = true;
  for (const auto &line : lines) {
    if (!mCheatAddLine(set, line.c_str(), mgbaType)) {
      if (mgbaType != 0 && mCheatAddLine(set, line.c_str(), 0)) {
        // Sucesso no fallback de autodeteção
      } else {
        allLinesAdded = false;
        LOGE("mGBA: Código de cheat rejeitado: '%s'", line.c_str());
      }
    }
  }

  if (allLinesAdded && !lines.empty()) {
    mCheatAddSet(dev, set);
    mCheatRefresh(dev, set);
    LOGI("mGBA: Cheat '%s' registrado com sucesso (%zu linhas, ativo=%d)",
         descStr, lines.size(), set->enabled);
  } else {
    set->deinit(set);
    free(set);
  }

  env->ReleaseStringUTFChars(code, codeStr);
  if (description != nullptr && descStr != nullptr && strcmp(descStr, "Cheat") != 0) {
    env->ReleaseStringUTFChars(description, descStr);
  }

  return allLinesAdded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeClearCheats(
    JNIEnv * /* env */, jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core != nullptr) {
    struct mCheatDevice *dev = s_core->cheatDevice(s_core);
    if (dev != nullptr) {
      mCheatDeviceClear(dev);
      LOGI("mGBA: Todos os cheats limpos.");
    }
  }
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeSetCheatEnabled(
    JNIEnv * /* env */, jobject /* thiz */, jint index, jboolean enabled) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_core != nullptr && index >= 0) {
    struct mCheatDevice *dev = s_core->cheatDevice(s_core);
    if (dev != nullptr &&
        static_cast<size_t>(index) < mCheatSetsSize(&dev->cheats)) {
      struct mCheatSet *set =
          *mCheatSetsGetPointer(&dev->cheats, static_cast<size_t>(index));
      if (set != nullptr) {
        set->enabled = (enabled == JNI_TRUE);
        mCheatRefresh(dev, set);
        LOGI("mGBA: Cheat [%d] estado=%d", index, set->enabled);
      }
    }
  }
}

JNIEXPORT void JNICALL
Java_com_retro_gba_emulator_NativeGbaEngine_nativeDestroy(JNIEnv *env,
                                                          jobject /* thiz */) {
  std::lock_guard<std::mutex> lock(s_coreMutex);
  if (s_rcInitialized) {
    rc_runtime_destroy(&s_rcRuntime);
    s_rcInitialized = false;
    std::lock_guard<std::mutex> achLock(s_achievementsMutex);
    s_triggeredAchievements.clear();
  }

  if (s_core != nullptr) {
    struct mCheatDevice *dev = s_core->cheatDevice(s_core);
    if (dev != nullptr) {
      mCheatDeviceClear(dev);
    }
    if (s_isLoaded) {
      s_core->unloadROM(s_core);
      s_isLoaded = false;
    }
    // Config antes do deinit: core->deinit() faz free(core).
    mCoreConfigDeinit(&s_core->config);
    s_core->deinit(s_core);
    s_core = nullptr;
  }

  if (s_directVideoBuffer != nullptr) {
    env->DeleteGlobalRef(s_directVideoBuffer);
    s_directVideoBuffer = nullptr;
  }

  s_romData.clear();
  s_audioWritePos.store(0, std::memory_order_relaxed);
  s_audioReadPos.store(0, std::memory_order_relaxed);
  s_isLoaded = false;
  LOGI("RetroGba: Núcleo mGBA, cheats e rcheevos destruídos com sucesso.");
}

} // extern "C"
