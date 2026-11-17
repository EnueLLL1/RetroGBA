# 🎮 RetroGBA — Emulador de Game Boy Advance para Android com RetroAchievements

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![mGBA Core](https://img.shields.io/badge/mGBA-0.10.5%20(MPL--2.0)-blue.svg)](https://mgba.io)
[![rcheevos](https://img.shields.io/badge/rcheevos-C99%20Official-orange.svg)](https://github.com/RetroAchievements/rcheevos)
[![Android](https://img.shields.io/badge/Android-SDK%2026%2B%20(Oreo%2B)-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Licença](https://img.shields.io/badge/License-MPL--2.0%20%2F%20MIT-lightgrey.svg)](LICENSE)

O **RetroGBA** é um emulador de alta fidelidade e código aberto de **Game Boy Advance** para a plataforma Android. Ele combina uma interface moderna em **Jetpack Compose / Material 3** com um núcleo de execução de hardware em C puro de alta precisão (**mGBA 0.10.5**), avaliação nativa de troféus do **RetroAchievements** (**rcheevos**), persistência atômica e proteções de hardware e segurança de nível corporativo.

---

## 📋 Status do Roadmap — 10 Fases Concluídas (100%)

| Fase | Título | Status | Principais Tecnologias / Módulos |
| :---: | :--- | :---: | :--- |
| **1** | **Fundação e Estrutura do Projeto** | ✅ Concluído | Gradle KTS, Jetpack Compose, Material 3, Arquitetura MVVM, Navegação |
| **2** | **Integração do Núcleo mGBA C++** | ✅ Concluído | mGBA 0.10.5 submodule, CMake NDK 27, JNI C++, ARM7TDMI, ABI arm64-v8a e x86_64 |
| **3** | **Pipeline de Vídeo & Áudio de Baixa Latência** | ✅ Concluído | Double-Buffering zero GC, 59.73 FPS, Áudio PCM Estéreo 32.768 Hz via `AudioTrack` |
| **4** | **Saves Atômicos & Save States** | ✅ Concluído | SRAM/Flash/EEPROM atômico com troca de arquivo temporário, Slots `.ss0`..`.ss4` |
| **5** | **RetroAchievements Oficial (`rcheevos`)** | ✅ Concluído | Runtime C99 oficial, IWRAM/EWRAM/SRAM memory snoopers, Modo Hardcore vs Softcore |
| **6** | **Segurança, Hardening & Auditoria** | ✅ Concluído | Android KeyStore AES-256-GCM, RomSecurityValidator, Mascaramento de Tokens em Rede |
| **7** | **Feedback Háptico & Controles Customizáveis** | ✅ Concluído | `VibrationEffect` tátil, Opacidade configurável (20%-100%), Multitouch D-Pad |
| **8** | **Motor de Cheats GameShark & Action Replay** | ✅ Concluído | `mCheatDevice` mGBA, Cheats em C++, JSON atômico, Bloqueio rígido no Hardcore |
| **9** | **Bateria, Gerenciamento Térmico & Profiling** | ✅ Concluído | `PowerProfileManager`, Frameskip dinâmico, 0% CPU background pause, HUD de FPS |
| **10**| **Documentação de Release & Build Final** | ✅ Concluído | ProGuard/R8, Builds Debug e Release, Documentação técnica unificada |
| **11**| **Overhaul `v1.1.0` (My Boy! Controls, mGBA JNI & RA)** | ✅ Concluído | Editor estilo My Boy! Portrait/Landscape, Multi-Touch 8-way + `B↔A`, polaridade `setKeys` + `blip_buf`, RA `login2`/`Flags==3`/Rich Presence/Fila Offline |

---

## ✨ Principais Funcionalidades

* 🚀 **Núcleo mGBA 0.10.5 Oficial (C/C++ NDK):** Emulação de ciclo preciso do processador ARM7TDMI (ARMv4T / Thumb), coprocessadores, timers, DMA e memória sem atalhos ou hacks de aproximação.
* 🏆 **RetroAchievements Oficial (`rcheevos`):** Conectado aos barramentos de memória do GBA em tempo real, avaliando triggers, hit counts, pausas e resets com total fidelidade às regras oficiais.
* 🛡️ **Segurança Rigorosa & Hardcore Mode:**
  - O **Modo Hardcore** impede trapaças (cheats) e bloqueia o carregamento de save states para manter a legitimidade das conquistas.
  - Credenciais e tokens de API armazenados com criptografia **AES-256-GCM** via hardware **Android KeyStore**.
  - Validação estrita de ROMs contra Path Traversal e estouros de buffer.
  - Logs de rede higienizados com mascaramento de parâmetros sensíveis (`p=***`, `t=***`, `y=***`).
* 🎨 **Pipeline de Vídeo com Double-Buffering (59.73 FPS):** Renderização direta do framebuffer nativo com zero alocações na Garbage Collection durante o loop do jogo.
* 🔊 **Áudio Estéreo PCM de Baixa Latência:** Síntese sonora direta pela APU do mGBA canalizada via `AudioTrack` em 32.768 Hz / 16-bit com sincronização de buffer.
* 💾 **Persistência Atômica:** Gravações de Save RAM (`.sav`) usando escrita em arquivo temporário com rename atômico do sistema operacional, eliminando corrupção de save caso ocorra queda de energia.
* 🎮 **Controles Virtuais & Editor Estilo My Boy!:** D-Pad contínuo de 8 direções com diagonais reais, deslize fluido `B ↔ A`, multi-touch com `actionMasked`, combos (`A+B`, `L+R`, `Quick Save/Load`), perfis Portrait/Landscape independentes com editor visual de arrastar e redimensionar (tela `3:2` e botões) e suporte a controles físicos Bluetooth/USB.
* 🏆 **RetroAchievements Oficial Completo (`rcheevos`):** Fluxo `login2`, filtro estrito de conquistas oficiais (`Flags == 3`), Rich Presence nativo, validações `GameID > 0`, parâmetros `m`/`o`/`h`/`x` e fila persistente offline para desbloqueios.
* 👾 **Motor de Trapaças (Cheats):** Adição e ativação em tempo real de códigos **GameShark**, **Action Replay** e **CodeBreaker** integrados diretamente ao subsistema de cheats do mGBA.
* 🔋 **Gerenciamento de Energia e Térmica:** Detecção do modo de economia do Android e estresse térmico com aplicação de **frameskip adaptativo inteligente** e pausa total (0% consumo) ao minimizar o app.

---

## 🏛️ Arquitetura do Sistema

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. Camada de Apresentação (Jetpack Compose / Material 3)              │
│    MainActivity, Screens (Library, Emulator, Achievements, Profile),   │
│    VirtualController (Multitouch/Haptics), GbaScreenCanvas (3:2 240x160)│
│    HUD de FPS & Modo Eco, Modais de Cheats e Save States               │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ UI Events / StateFlow
┌───────────────────────────────────▼────────────────────────────────────┐
│ 2. Camada de Aplicação & Negócio (MVVM & Ciclo de Vida)                │
│    EmulatorViewModel, ProfileViewModel, PowerProfileManager,           │
│    RomSecurityValidator, CryptoManager (Android KeyStore)              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ IGbaEngine Contract
┌───────────────────────────────────▼────────────────────────────────────┐
│ 3. Camada de Abstração de Emulação                                    │
│    IGbaEngine (Interface Comum)                                        │
│    ├── NativeGbaEngine (Núcleo Real de Emulação mGBA via JNI)          │
│    └── SimulationGbaEngine (Modo Demonstração Isolado para Testes)     │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ Chamadas JNI                   │ Memória / Triggers
┌───────────────────▼─────────────┐   ┌──────────────▼───────────────────┐
│ 4. Camada Nativa (C/C++ NDK)     │   │ 5. Camada RetroAchievements      │
│    retrogba_jni.cpp             │   │    RetroAchievementsApi (OkHttp) │
│    mGBA Core 0.10.5 (MPL-2.0)   │   │    RetroAchievementsRepository   │
│    rcheevos Runtime (C99)       │   │    rcheevos Triggers & Evaluator │
│    Áudio PCM 16-bit estéreo     │   │    Hardcore vs Softcore Locks    │
│    mCheatDevice (GameShark/AR)  │   │                                  │
└───────────────────┬─────────────┘   └──────────────────────────────────┘
                    │ Leitura / Escrita em Disco
┌───────────────────▼────────────────────────────────────────────────────┐
│ 6. Camada de Persistência & Repositórios                               │
│    SaveRepository: .sav (atômico), Save States (.ss0..4)               │
│    CheatRepository: cheats.json (atômico)                              │
│    UserPreferences: DataStore + KeyStore Seguro                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📂 Estrutura de Diretórios do Projeto

```
splendid-carson/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                           # Código nativo C/C++
│   │   │   │   ├── CMakeLists.txt             # Script de build NDK
│   │   │   │   ├── retrogba_jni.cpp           # Camada de ponte JNI (mGBA + rcheevos)
│   │   │   │   ├── mgba/                      # Submódulo oficial mGBA 0.10.5
│   │   │   │   └── rcheevos/                  # Submódulo oficial rcheevos C99
│   │   │   ├── java/com/retro/gba/
│   │   │   │   ├── data/
│   │   │   │   │   ├── model/                 # Entidades: RomItem, Achievement, CheatCode
│   │   │   │   │   ├── prefs/                 # UserPreferences (DataStore)
│   │   │   │   │   └── repository/            # SaveRepository, CheatRepository, etc.
│   │   │   │   ├── emulator/                  # IGbaEngine, NativeGbaEngine, SimulationGbaEngine
│   │   │   │   ├── performance/               # PowerProfileManager (Bateria, Térmica, FPS)
│   │   │   │   ├── security/                  # CryptoManager, RomSecurityValidator
│   │   │   │   └── ui/
│   │   │   │       ├── components/            # GbaScreenCanvas, VirtualController
│   │   │   │       ├── haptics/               # HapticManager (VibrationEffect)
│   │   │   │       ├── screens/               # EmulatorScreen, LibraryScreen, ProfileScreen
│   │   │   │       ├── theme/                 # Paleta Material 3 e Tipografia
│   │   │   │       └── viewmodel/             # EmulatorViewModel, ProfileViewModel
│   │   │   └── AndroidManifest.xml
│   │   └── test/                              # Suíte completa de testes unitários JUnit
│   └── build.gradle.kts                       # Configuração do módulo Android
├── gradle/                                    # Gradle Wrapper
├── build.gradle.kts                           # Root build script
├── settings.gradle.kts                        # Configuração de repositórios e plugins
└── README.md
```

---

## 🛠️ Como Clonar e Compilar

### 1. Clonar com Submódulos Oficiais (Requisito Obrigatório)

O projeto requer os submódulos oficiais do **mGBA** e do **rcheevos**:

```bash
git clone --recurse-submodules https://github.com/EnueLLL1/RetroGBA.git
cd RetroGBA
```

Se o repositório já foi clonado sem submódulos:
```bash
git submodule update --init --recursive
```

### 2. Pré-requisitos de Desenvolvimento

* **Android Studio:** Ladybug (2024.2+) ou versão recente.
* **JDK:** Java 17 (OpenJDK / Azul Zulu / Android Studio bundled).
* **Android SDK:** Compile SDK 35 (Android 15) / Min SDK 26 (Android 8.0 Oreo).
* **Android NDK:** Versão `27.0.12077973` ou superior.
* **CMake:** Versão 3.22.1+.

### 3. Compilação via Linha de Comando

```powershell
# Executar a suíte de testes unitários
./gradlew testDebugUnitTest --no-daemon

# Compilar o APK de depuração (Debug)
./gradlew assembleDebug --no-daemon

# Compilar o APK de produção otimizado com ProGuard/R8 (Release)
./gradlew assembleRelease --no-daemon
```

### 4. Localização dos Binários & Releases

* **Download Pronto (`v1.1.0`):** Acesse a aba [Releases no GitHub](https://github.com/EnueLLL1/RetroGBA/releases/tag/v1.1.0) para baixar o APK assinado (`RetroGBA-v1.1.0-release-signed.apk`) diretamente no Android.
* **APK de Debug:** `app/build/outputs/apk/debug/app-debug.apk`
* **APK de Release (Unsigned):** `app/build/outputs/apk/release/app-release-unsigned.apk` (~15 MB)

---

## 🤖 Fluxo de Planejamento & Engenharia com IA (`/swarm` + TDD)

Este projeto também documenta na prática como problemas reais observados durante o uso no celular — controles que travavam ao tocar, personalização limitada, áudio distorcido e desvios sutis de protocolo no RetroAchievements — foram diagnosticados e corrigidos de forma estruturada mesmo sem tempo disponível para codificar tudo manualmente do zero:

1. **Diagnóstico em Frente Única antes de Codificar (Auditoria Paralela):**
   Subagentes especializados auditaram separadamente a camada nativa (`mGBA`/JNI), o protocolo do RetroAchievements e a interface Jetpack Compose antes de qualquer edição.
2. **Execução Coordenada via `/swarm` (Contratos Isolados por Domínio):**
   Em vez de alterações soltas em arquivos gigantes, o trabalho foi dividido em quatro frentes sem conflito de arquivos — `engine-jni`, `retro-achievements`, `controls-myboy` e `ui-screens-previews` — guiadas por contratos exatos de interface (`ControlLayoutModels`, `IGbaEngine`, `UserPreferences`).
3. **Verificação Independente (`/ecc-tdd` + Code Review + Compose Preview):**
   Um agente auditor separado (`verifier`) validou o diff completo, executou a suíte com 110 testes unitários JUnit, compilou as ABIs nativas (`arm64-v8a` e `x86_64`) e renderizou 12 `@Preview`s visuais no painel antes do release.

### 💡 Principais Aprendizados Técnicos da Revisão

* **Polaridade de Botões no mGBA vs Hardware GBA:** Enquanto o registrador físico `REG_KEYINPUT` (`0x04000130`) opera em *active-low* (`0` = pressionado, `0x03FF` = solto), `mCore::setKeys` no mGBA espera máscara *active-high* porque o próprio núcleo aplica `0x3FF ^ input` em `io.c`. Enviar `0x03FF` direto fazia todos os botões parecerem pressionados após o primeiro toque. A ponte JNI (`retrogba_jni.cpp`) agora converte explicitamente antes de chamar `setKeys`, mantendo a consistência com o `SimulationGbaEngine`.
* **Áudio Nativo com `blip_buf` (32.768 Hz):** Substituir buffers circulares manuais pelo `blip_t` nativo do mGBA (`configureAudio()` + `blip_read_samples`) elimina estalos e distorções causados por mudanças dinâmicas de `SOUNDBIAS`.
* **Segurança de Memória e Estado no `rcheevos`:** Jogos de GBA variam de EEPROM 512B/8KB a Flash 128KB e SRAM 32KB; leituras da região `0x0E000000` devem ser limitadas por `GBASavedataSize(&gba->memory.savedata)` para evitar `SIGSEGV`, e `nativeLoadState` precisa invocar `rc_runtime_reset(&s_rcRuntime)` para não disparar falsos desbloqueios.
* **Multi-Touch Real e Geometria no Compose:** Ler `event.actionMasked` e `event.actionIndex` dentro do escopo de `BoxWithConstraints` evita botões presos com múltiplos dedos e permite D-Pad contínuo de 8 direções (`tan(22.5°)`) mais deslize direto entre `B` e `A`.
* **Estabilidade de Build NDK no Windows e Saves Atômicos:** Limitar o paralelismo (`--max-workers=1`, `CMAKE_BUILD_PARALLEL_LEVEL=2`) evita picos térmicos durante a compilação C++ para múltiplas ABIs, enquanto `AtomicFile` + `withContext(NonCancellable)` em `onAppPause()` protegem o `.sav` da bateria contra perda ao alternar de aba ou fechar o app.

---

## 📄 Licenças e Atribuições

* **mGBA Core:** [Mozilla Public License 2.0 (MPL-2.0)](https://github.com/mgba-emu/mgba/blob/master/LICENSE) — Copyright © Jeffrey Pfau.
* **rcheevos:** [MIT License](https://github.com/RetroAchievements/rcheevos/blob/master/LICENSE) — Copyright © RetroAchievements.
* **RetroGBA:** Desenvolvido sob licença de código aberto para a comunidade de preservação de jogos retrô.
