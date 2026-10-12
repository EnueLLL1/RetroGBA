# 🎮 RetroGBA - Emulador de Game Boy Advance para Android com RetroAchievements

Este projeto combina um **guia completo dos melhores emuladores já existentes para Android com RetroAchievements** e o **código-fonte de um aplicativo Android nativo customizado** em Kotlin e Jetpack Compose.

---

## 🏆 Parte 1: Melhores Emuladores Prontos para Android com RetroAchievements

Se você deseja começar a jogar imediatamente no seu smartphone ou tablet Android com suporte a conquistas, estas são as 3 melhores opções disponíveis:

### 1. RetroArch (Recomendação #1 - Padrão Ouro da Comunidade)
* **Preço:** Gratuito e Código Aberto (FOSS)
* **Onde baixar:** [retroarch.com](https://www.retroarch.com/) (recomenda-se a versão `.apk` direta do site ou F-Droid, pois a versão da Play Store possui limitações de atualização de cores).
* **Melhores Cores de GBA para RetroAchievements:**
  * **`mGBA` (Altamente recomendado):** O core mais preciso, suporta 100% dos sets de conquistas do RetroAchievements, relógio em tempo real (RTC para Pokémon Ruby/Sapphire/Emerald) e ótima performance.
  * **`gpSP`:** Ideal apenas se o seu aparelho for muito antigo ou fraco.
* **Como Configurar o RetroAchievements no RetroArch:**
  1. Abra o RetroArch e vá em **Configurações (Settings) > Conquistas (Achievements)**.
  2. Ative a opção **Conquistas (Achievements: ON)**.
  3. Insira seu **Nome de Usuário** e **Senha** (ou Web API Key gerada no site).
  4. **Modo Hardcore:**
     * *Ativado (Padrão):* Desativa Save States, cheats e câmera lenta (slow motion) para que seus pontos contem no ranking oficial do RetroAchievements.
     * *Desativado:* Permite usar Save States e cheats (conquistas computadas como *Softcore*).
  5. Vá em **Menu Principal > Atualizador Online > Baixar Cores** e instale o **Nintendo - Game Boy Advance (mGBA)**.
  6. Carregue sua ROM de GBA. Ao iniciar o jogo, você verá uma notificação no canto da tela informando quantas conquistas existem e seu progresso!

---

### 2. Lemuroid (Mais Amigável e Fácil de Usar)
* **Preço:** Gratuito e Sem Anúncios (Disponível na Google Play Store).
* **Vantagens:** Interface moderna, limpa e automática; reconhece capas dos jogos automaticamente.
* **Como Ativar o RetroAchievements:**
  1. Abra o Lemuroid e toque na engrenagem de **Configurações**.
  2. Role até a seção **RetroAchievements**.
  3. Faça login com seu usuário e senha.
  4. Escolha se deseja o **Modo Hardcore**.
  5. Pronto! O Lemuroid usará o core do libretro e sincronizará suas conquistas automaticamente.

---

### 3. Pizza Boy GBA Pro / A Basic
* **Preço:** Disponível na Play Store (versões gratuitas e pagas).
* **Vantagens:** O emulador standalone mais bonito e otimizado para telas sensíveis ao toque, temas retrô de carcaça do GBA e suporte integrado a login do RetroAchievements nas opções avançadas.

---

## 🛠️ Parte 2: O Projeto Android RetroGBA (Desenvolvido Neste Repositório)

Neste repositório foi construída a base completa de um aplicativo Android nativo moderno com **Jetpack Compose**, **Material 3** e integração direta com a **API oficial do RetroAchievements**.

### 📁 Estrutura do Projeto

```
splendid-carson/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/retro/gba/
│           ├── RetroGbaApplication.kt
│           ├── MainActivity.kt               # Navegação e menu inferior (Tabs)
│           ├── data/
│           │   ├── api/
│           │   │   └── RetroAchievementsApi.kt # Chamadas HTTP oficiais (login, hash, patch, award)
│           │   ├── model/
│           │   │   └── Models.kt               # Achievement, GameInfo, UserProfile, RomItem
│           │   ├── prefs/
│           │   │   └── UserPreferences.kt      # Persistência de credenciais e modo hardcore
│           │   └── repository/
│           │       ├── RetroAchievementsRepository.kt
│           │       └── RomRepository.kt        # Leitor de ROM, Header GBA e cálculo de MD5
│           ├── emulator/
│           │   ├── GbaInput.kt                 # Mapeamento de botões (A, B, D-Pad, L, R, Turbo)
│           │   ├── GbaMemory.kt                # Barramento de memória (EWRAM, IWRAM, VRAM, I/O)
│           │   ├── AchievementEvaluator.kt     # Motor de checagem de condições do RA a cada frame
│           │   └── GbaCore.kt                  # Loop de execução a 60 FPS e buffers de vídeo
│           └── ui/
│               ├── components/
│               │   ├── VirtualController.kt    # D-Pad tátil e botões com resposta vibratória
│               │   ├── AchievementOverlay.kt   # Notificação flutuante de conquista desbloqueada
│               │   └── GbaScreenCanvas.kt      # Renderização na proporção clássica 3:2 (240x160)
│               ├── screens/
│               │   ├── LibraryScreen.kt        # Seletor de ROMs (.gba) via SAF
│               │   ├── EmulatorScreen.kt       # Tela de jogo com controles e quick menu
│               │   ├── AchievementsScreen.kt   # Lista detalhada de troféus, badges e pontos
│               │   └── ProfileScreen.kt        # Login RA, ranking e alternador de Modo Hardcore
│               └── viewmodel/
│                   ├── EmulatorViewModel.kt
│                   └── ProfileViewModel.kt
├── gradle/
│   └── libs.versions.toml                      # Version catalog do Gradle
├── build.gradle.kts
└── settings.gradle.kts
```

---

## ⚡ Como Funciona a Tecnologia do RetroAchievements no App

1. **Identificação da ROM por Hash MD5:**
   * O RetroAchievements não identifica jogos pelo nome do arquivo, mas sim pelo hash MD5 do conteúdo binário da ROM. O `RomRepository` calcula esse hash e consulta o endpoint `dorequest.php?r=gameid&m={md5}`.
2. **Download do Set de Conquistas (Patch):**
   * O endpoint `dorequest.php?r=patch&g={gameId}` traz a lista de troféus com títulos, pontuações, ícones (badges) e as fórmulas de memória (`MemAddr`).
3. **Avaliação em Tempo Real:**
   * No `AchievementEvaluator`, a cada frame (60 vezes por segundo), o emulador lê os endereços de memória (como a EWRAM `0x02000000`) e verifica se a condição do jogo foi satisfeita (ex: derrotar um chefe, zerar uma fase, coletar 100 moedas).
4. **Envio e Feedback ao Jogador:**
   * Quando uma condição é atendida, o pop-up animado `AchievementOverlay` surge na tela com o badge colorido e os pontos ganhos, enquanto uma requisição assíncrona envia o desbloqueio oficial para o servidor do RetroAchievements via `dorequest.php?r=awardachievement`.

---

## 🚀 Como Abrir e Compilar o Projeto

1. Abra o **Android Studio** (versão Ladybug / Iguana ou superior).
2. Selecione **File > Open** e aponte para esta pasta:
   ```
   c:\Users\Emanuel\Documents\antigravity\splendid-carson
   ```
3. O Android Studio sincronizará automaticamente as dependências definidas em `gradle/libs.versions.toml`.
4. Conecte um aparelho Android via USB ou inicie um emulador Android (AVD).
5. Clique em **Run ▶ (Shift + F10)** para instalar o APK!
