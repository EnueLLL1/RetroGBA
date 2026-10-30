# Contexto do Projeto: splendid-carson (RetroGBA Android)

Este documento serve como **ponto de partida e memória técnica** para novas conversas e para qualquer agente ou desenvolvedor que continue o projeto.

---

## 1. Visão Geral do Projeto
- **Nome/Tipo:** Emulador de Game Boy Advance (GBA) para Android.
- **Stack:**
  - **Linguagem Principal:** Kotlin + Jetpack Compose (Material 3).
  - **Core Nativo (C++):** mGBA emulado via NDK/CMake (`retrogba_jni.cpp`).
  - **Conquistas:** RetroAchievements integrado com a biblioteca nativa `rcheevos`.
  - **Arquitetura:** MVVM, Coroutines, StateFlow, ViewModel com ciclo de vida Android.

---

## 2. Histórico Recente e Último Commit
- **Branch Atual:** `main` (sincronizada com `origin/main`).
- **Último Commit Realizado:**
  `ea70569` - *fix(emulator): corrigir integracao JNI mGBA, threading e persistencia de saves*

### Problemas Críticos Resolvidos:
1. **Crash / Use-After-Free no mGBA:**
   - `core->deinit(core)` no mGBA libera internamente a estrutura (`free(core)`).
   - O código anterior chamava `mCoreConfigDeinit(&s_core->config)` depois do `deinit`, causando corrupção de memória. A desinicialização agora é estritamente ordenada.
2. **Concorrência e Thread Safety:**
   - Acesso simultâneo ao mGBA (vídeo, áudio, cheats, conquistas) agora protegido por `s_coreMutex`.
   - `currentButtons` convertido para `std::atomic<uint16_t>`.
   - Swap atômico de conquistas disparadas com `s_achievementsMutex`.
3. **Consumo Excessivo de RAM:**
   - `s_romData` (cópia C++ dos bytes da ROM) é liberada imediatamente (`clear()` + `shrink_to_fit()`) após ser passada para o `VFileMemChunk` do mGBA, economizando dezenas de megabytes.
4. **Perda de Save de Bateria (.sav):**
   - Ao sair do app, `viewModelScope` é cancelado imediatamente.
   - Implementado salvamento de bateria atômico com `withContext(NonCancellable)` e rotina bloqueante em `onCleared()` antes de `gbaCore.stop()`.
5. **Validação:**
   - `:app:assembleDebug` compilado com sucesso para ARM64-v8a e x86_64.
   - Suíte de testes unitários (`:app:testDebugUnitTest`) 100% aprovada.

---

## 3. Ambiente de Desenvolvimento e Regras Ativas
- **Sistema Operacional:** Windows.
- **Diretrizes e Regras Salvas em `.agents/rules/`:**
  - [`.agents/rules/advisor-protocol.md`](./.agents/rules/advisor-protocol.md): Protocolo de subagentes (3 exploradores + Advisor nas 3 travas).
  - [`.agents/rules/windows-android-build.md`](./.agents/rules/windows-android-build.md): Exclusões do Microsoft Defender (`C:\dev`, `.gradle`, SDK Android, `Documents\antigravity`), tratamento de atributos read-only (`attrib -r -s`) e integridade de lockfiles do Git.
- **Nota:** As regras na pasta `.agents/rules/` são carregadas **automaticamente** pelo Antigravity em qualquer nova conversa aberta neste projeto.

---

## 4. Estado Atual e Próximos Passos
1. **Commits e Git:**
   - A pasta `.agents/` contém os arquivos de configuração do agente e pode ser commitada com:
     ```bash
     git add .agents PROJECT_CONTEXT.md
     git commit -m "chore: adicionar regras de build e contexto do projeto"
     git push origin main
     ```
2. **Testes no Dispositivo Android:**
   - O APK de depuração atual compilado está pronto para instalação e teste de:
     - Carregamento e troca de ROMs.
     - Persistência e integridade dos saves `.sav` ao fechar e reabrir.
     - Responsividade dos inputs touch/físicos.
3. **Próximas Tarefas Arquiteturais (Backlog):**
   - Avaliar suporte a áudio via Oboe/AAudio para redução de latência se necessário.
   - Manter testes contínuos de estabilidade do mGBA.

