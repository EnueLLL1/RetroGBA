# 🎮 RetroGBA - Emulador de Game Boy Advance para Android com RetroAchievements

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![mGBA Core](https://img.shields.io/badge/mGBA-0.10.5%20(MPL--2.0)-blue.svg)](https://mgba.io)
[![rcheevos](https://img.shields.io/badge/rcheevos-C99%20Official-orange.svg)](https://github.com/RetroAchievements/rcheevos)
[![Android](https://img.shields.io/badge/Android-SDK%2026%2B%20(Oreo%2B)-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

O **RetroGBA** é um emulador moderno, de alta fidelidade e código aberto de **Game Boy Advance** para Android. Ele combina uma interface reativa em **Jetpack Compose / Material 3** com um núcleo de execução de hardware em C puro de alta precisão (**mGBA**) e avaliação nativa oficial de conquistas do **RetroAchievements** (**rcheevos**).

---

## ✨ Principais Funcionalidades

* 🚀 **Núcleo mGBA Real (C++ / NDK):** Emulação oficial do processador ARM7TDMI (ARMv4T / Thumb), coprocessadores, timers, DMA e memória do GBA em C puro sem aproximações.
* 🏆 **RetroAchievements Oficial (`rcheevos`):** Avaliação de condições legítimas de conquistas via biblioteca em C oficial conectada aos barramentos de IWRAM, EWRAM e SRAM com suporte a Hit Counts, PauseIf, ResetIf e Alt Groups.
* 🎨 **Renderização Double-Buffering (59.73 FPS):** Pipeline de vídeo com zero alocações no Garbage Collector, eliminando pausas e travamentos durante o jogo.
* 🔊 **Áudio PCM Estéreo de Baixa Latência:** Síntese de som pela APU do mGBA transmitida em tempo real via `AudioTrack` em 32.768 Hz / 16-bit com suporte a Fast-Forward (Turbo).
* 💾 **Persistência Atômica de Save RAM (.SAV):** Gravação segura e atômica da memória de cartucho (Flash/EEPROM/SRAM) protegendo o progresso contra corrupção.
* ⏱️ **Slots de Save States (.ss0 a .ss4):** Gerenciamento instantâneo de estados com seleção visual de slots e bloqueio inteligente no Modo Hardcore.
* 📱 **Controles Táteis com Multitouch e Háptica:** D-Pad virtual com feedback vibratório e detecção simultânea de toques.

---

## 🏛️ Arquitetura do Sistema

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. Camada de Apresentação (Jetpack Compose / Material 3)              │
│    MainActivity, Screens (Library, Emulator, Achievements, Profile),   │
│    VirtualController (Multitouch/Haptics), GbaScreenCanvas (3:2 240x160)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ UI Events / StateFlow
┌───────────────────────────────────▼────────────────────────────────────┐
│ 2. Camada de Aplicação (MVVM & Ciclo de Vida)                          │
│    EmulatorViewModel, ProfileViewModel, RomRepository (SAF/MD5)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ IGbaEngine Contract
┌───────────────────────────────────▼────────────────────────────────────┐
│ 3. Camada de Emulação (Abstração & Orquestração)                       │
│    IGbaEngine (Interface Comum)                                        │
│    ├── NativeGbaEngine (Núcleo Real de Emulação mGBA)                  │
│    └── SimulationGbaEngine (Modo Demonstração Isolado)                 │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ JNI Calls                      │ Memória / Triggers
┌───────────────────▼─────────────┐   ┌──────────────▼───────────────────┐
│ 4. Camada Nativa (C/C++ NDK)     │   │ 5. Camada RetroAchievements      │
│    retrogba_jni.cpp             │   │    RetroAchievementsApi (OkHttp) │
│    mGBA Core 0.10.5 (MPL-2.0)   │   │    RetroAchievementsRepository   │
│    rcheevos Runtime (C99)       │   │    rcheevos Triggers & Evaluator │
│    Audio (PCM 16-bit estéreo)   │   │    Modos Hardcore / Softcore     │
└───────────────────┬─────────────┘   └──────────────────────────────────┘
                    │ Leitura / Escrita em Disco
┌───────────────────▼────────────────────────────────────────────────────┐
│ 6. Camada de Persistência                                              │
│    SaveRepository: .sav (atômico), Save States (.ss0..4), UserPrefs    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Como Clonar e Compilar

### 1. Clonar com Submódulos (Obrigatório)

O projeto utiliza os submódulos oficiais do **mGBA** e do **rcheevos**. Clone com o parâmetro `--recurse-submodules`:

```bash
git clone --recurse-submodules <URL_DO_REPOSITORIO>
cd splendid-carson
```

Se já clonou sem o parâmetro, inicialize os submódulos com:
```bash
git submodule update --init --recursive
```

### 2. Requisitos de Compilação

* **Android Studio:** Versão Iguana / Ladybug ou superior
* **Android SDK:** Compile SDK 35 (Android 15) / Min SDK 26 (Android 8.0)
* **Android NDK:** NDK 27 (`27.0.12077973` ou superior)
* **CMake:** Versão 3.22.1+

### 3. Compilação via Linha de Comando (Gradle)

```bash
# Executar a suíte completa de testes unitários
./gradlew testDebugUnitTest

# Compilar os binários nativos C++ (mGBA + rcheevos)
./gradlew externalNativeBuildDebug

# Gerar o pacote APK de depuração
./gradlew assembleDebug
```

O arquivo gerado estará localizado em:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 Licenças e Créditos

* **mGBA Core:** [Mozilla Public License 2.0 (MPL-2.0)](https://github.com/mgba-emu/mgba/blob/master/LICENSE) — Copyright © Jeffrey Pfau.
* **rcheevos:** [MIT License](https://github.com/RetroAchievements/rcheevos/blob/master/LICENSE) — Copyright © RetroAchievements.
* **RetroGBA App:** Código Android em Kotlin / Jetpack Compose.
