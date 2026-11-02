# Diretrizes de Build e Ambiente Windows

## 1. Concorrência e File Locks no Windows
- **Exclusões de Antivírus:** O Microsoft Defender monitora ativamente diretórios de build. As pastas `C:\dev`, `.gradle`, SDK Android e `Documents\antigravity` estão configuradas nas exclusões. Sempre priorize compilar e rodar tarefas intensivas de NDK/CMake nesses caminhos seguros.
- **Tratamento de Arquivos Read-Only:** Ao espelhar, sincronizar ou restaurar repositórios no Windows, certifique-se de que os arquivos não possuam atributo somente-leitura executando `attrib -r -s <dir>\*.* /s /d`.
- **Git Lockfiles:** Se ocorrer `fatal: Unable to create '.git/index.lock': Permission denied`, certifique-se de que não há processos travando o arquivo e use sandbox bypass se necessário para concluir a operação de versionamento.

## 2. Cuidados Críticos com JNI e mGBA
- **Ordem de Desinicialização:** O `mGBA` desaloca a estrutura `core` em `core->deinit(core)` (`free(core)`). Nunca acesse `core->config` ou `core->opts` após o deinit.
- **Economia de RAM:** `VFileMemChunk` duplica o buffer da ROM. Imediatamente após carregá-la no mGBA, libere o vetor de bytes temporário em C++ (`s_romData.clear()`; `s_romData.shrink_to_fit()`).
- **Persistência de Saves (.sav):** Não dependa apenas de coroutines assíncronas de escopo padrão para gravação de bateria ao fechar o app. Garanta gravação atômica antes do encerramento com `withContext(NonCancellable)` ou rotina bloqueante em `onCleared()`.

## 3. Feedback Visual Imediato (UI / Jetpack Compose)
- **Visualização Imediata Obrigatória:** Sempre que qualquer alteração visual, layout ou estilo for feita no frontend (Jetpack Compose), renderize ou gere o preview imediatamente para que o usuário possa ver o resultado na hora.
- **Compose Previews:** Garanta que componentes e telas modificados possuam anotações `@Preview` funcionais e acione a renderização de preview (`compose-preview` ou comando equivalente do Android CLI) logo após salvar as alterações, notificando o usuário com o resultado visual.

