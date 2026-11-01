# Contexto do Projeto: splendid-carson (RetroGBA Android)

Este documento serve como **ponto de partida e memória técnica** para novas conversas e para qualquer agente ou desenvolvedor que continue o projeto.

---

## 1. Visão Geral do Projeto
- **Nome/Tipo:** Emulador de Game Boy Advance (GBA) para Android.
- **Stack:**
  - **Linguagem Principal:** Kotlin + Jetpack Compose (Material 3).
  - **Core Nativo (C++):** mGBA emulado via NDK/CMake (`retrogba_jni.cpp`).
  - **Resampling de Áudio:** Sintetizador band-limited nativo `blip_buf` do mGBA para Android `AudioTrack` em modo streaming a 32768 Hz.
  - **Conquistas:** RetroAchievements integrado com a biblioteca nativa `rcheevos`.
  - **Arquitetura:** MVVM, Coroutines, StateFlow, ViewModel com ciclo de vida Android.

---

## 2. Histórico Recente e Mudanças Críticas Realizadas

### 2.1. Correção Definitiva do Pipeline de Áudio (mGBA + Android AudioTrack)
1. **Causa Raiz Resolvida:**
   - ROMs de GBA alteram dinamicamente a resolução do registrador `REG_SOUNDBIAS` no boot (de 32768 Hz para 65536 Hz, 131072 Hz ou 262144 Hz).
   - O código legado usava `postAudioFrameCb` (que recebia ticks brutos de subamostras sem resampling), causando distorção no pitch (música grave/lenta), overflow de buffer e cortes severos de áudio.
2. **Nova Arquitetura com `blip_buf`:**
   - Eliminados `s_avStream`, `postAudioFrameCb`, `audioRateChangedCb` e o ring buffer manual intermediário `s_audioBuffer`.
   - O áudio agora é configurado diretamente nos canais de síntese band-limited nativos do mGBA (`s_core->getAudioChannel(s_core, 0/1)`) via `blip_set_rates(ch, s_core->frequency(s_core), 32768)` em `nativeInit` e após `s_core->reset` em `nativeLoadRom`.
   - `nativeGetAudioSamples` drena diretamente os buffers `blip_t*` via `blip_read_samples` intercalado em estéreo de forma síncrona sob `s_coreMutex`.
   - `AUDIO_CHUNK_SIZE` em `NativeGbaEngine.kt` ampliado para 4096 shorts.
   - Buffer do `AudioTrack` em `GbaAudioPlayer.kt` ampliado para `minBufferSize * 4` para blindagem contra jitter de agendamento de threads do Android.
   - **Resultado Testado em Hardware:** Verificado em dispositivo físico com 0 underruns (`z:0`) e áudio perfeito.

### 2.2. Correção de Concorrência, JNI e Motores de Emulação
1. **Polaridade dos Botões no GBA:**
   - O GBA usa lógica ativa baixa (0 = pressionado, 1 = solto). Em `NativeGbaEngine.kt`, corrigida a inversão de polaridade com `currentButtons xor GbaInput.ALL_RELEASED`.
2. **Ciclo de Vida e Desinicialização JNI:**
   - `mCoreConfigDeinit(&s_core->config)` chamado estritamente ANTES de `s_core->deinit(s_core)` (evitando Use-After-Free no `free(core)`).
   - `s_romData.clear()` e `shrink_to_fit()` executados logo após `VFileMemChunk` para liberar memória RAM.
3. **Double-Buffering e Simulação:**
   - Corrigido congelamento de tela no `SimulationGbaEngine.kt` utilizando alternância de 2 bitmaps (`frameBuffers`).
   - Removido arquivo morto legado `GbaCore.kt` (-355 linhas) e limpas suas referências.
4. **RetroAchievements (`rcheevos`):**
   - Corrigida desativação de conquistas após desbloqueio (`nativeDeactivateAchievement(id)`).
   - Ajustados os testes unitários do `AchievementEvaluatorTest` e `GbaEngineTest`.

---

## 3. Diretrizes de Build e Ambiente Windows
- **Arquivo de Regras do Projeto:** [`AGENTS.md`](./AGENTS.md)
  - Microsoft Defender exclusions: `C:\dev`, `.gradle`, SDK Android e pasta do agente.
  - Atributos somente-leitura: tratar com `attrib -r -s` se necessário.
  - Gradle Daemon no Windows Sandbox: rodar compilação de APKs com NDK/CMake (`assembleDebug`) com sandbox bypass para permitir socket loopback local da daemon do Gradle.

---

## 4. Comandos de Validação Rápida
- **Testes Unitários:** `.\gradlew.bat testDebugUnitTest`
- **Build do APK Debug:** `.\gradlew.bat assembleDebug`
- **Caminho do APK:** `app\build\outputs\apk\debug\app-debug.apk`
