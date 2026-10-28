# Guia de Contexto Arquitetural: Integração mGBA + RetroAchievements (`rcheevos`) no Android

Este documento serve como **Fonte da Verdade Técnica** para o Agente de IA durante a auditoria e refatoração do emulador no Android Studio. Todas as implementações de C/C++, JNI e Kotlin devem obedecer às especificações oficiais do `mGBA` e da biblioteca `rcheevos` detalhadas abaixo.

---

## 1. Arquitetura Interna do mGBA (`libmgba`)

O mGBA utiliza uma camada de abstração chamada `mCore` (`#include <mgba/core/core.h>`), que encapsula o hardware específico do Game Boy Advance (`struct GBA`, definido em `#include <mgba/internal/gba/gba.h>`).

### 1.1. Ciclo de Vida do `mCore`
1. **Instanciação:** `struct mCore* core = mCoreFind(path);` ou `GBAACoreCreate()`.
2. **Inicialização:** `core->init(core);` (aloca as estruturas internas, incluindo `core->board`, que aponta para `struct GBA*`).
3. **Configuração de AV e Diretórios:** Configuração dos buffers de vídeo (`core->setVideoBuffer`), taxa de amostragem de áudio (`core->setAudioBufferSize`), e inicialização de `mCoreInitConfig(core, NULL)`.
4. **Carregamento da ROM:** `core->loadFile(core, vfile);`
5. **Reset Inicial:** `core->reset(core);` (**Atenção:** Os ponteiros de memória RAM da placa GBA são inicializados/realocados dentro do `reset`. Nunca faça cache permanente dos ponteiros de RAM antes de `core->reset(core)`!).
6. **Execução por Quadro:** `core->runFrame(core);`
   * O GBA real roda a exatos $\frac{16777216\text{ Hz}}{280896\text{ ciclos por frame}} \approx 59.7275\text{ FPS}$ ($\approx 16.74\text{ ms}$ por quadro).

### 1.2. Estrutura de Memória Interna do GBA no mGBA
Dentro do `mCore`, o ponteiro `core->board` pode ser convertido para `struct GBA*`:
```c
#include <mgba/core/core.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/memory.h>

struct GBA* gba = (struct GBA*) core->board;
```

Os bancos de memória dentro de `gba->memory` e `gba->sram` são:
* **IWRAM (Internal Work RAM):** `gba->memory.iwram` (Ponteiro `uint32_t*`, tamanho fixo de $32\text{ KB}$ = `0x8000` bytes = `SIZE_WORKING_IRAM`). No barramento do GBA, fica em `0x03000000 - 0x03007FFF`.
* **EWRAM (External Work RAM):** `gba->memory.wram` (Ponteiro `uint32_t*`, tamanho fixo de $256\text{ KB}$ = `0x40000` bytes = `SIZE_WORKING_RAM`). No barramento do GBA, fica em `0x02000000 - 0x0203FFFF`.
* **SRAM / Flash (Save Data):** `gba->sram` (Ponteiro `uint8_t*`, até $64\text{ KB}$ = `0x10000` bytes, gerenciado por `gba->memory.savedata`). No barramento do GBA, fica em `0x0E000000`.

> **Nota sobre Endianness:** Tanto a CPU ARM7TDMI do GBA quanto os dispositivos Android modernos (ARMv7 / ARM64 / x86_64) são **Little-Endian**. Portanto, fazer `memcpy` direto de `(const uint8_t*)gba->memory.iwram` ou `(const uint8_t*)gba->memory.wram` é perfeitamente seguro, rápido e não exige inversão de bytes.

---

## 2. O Mapeamento de Memória do RetroAchievements (`rcheevos`) para GBA

**ESTE É O ERRO MAIS COMUM EM INTEGRAÇÕES DO MODO GBA NO RETROACHIEVEMENTS.**

A biblioteca `rcheevos` utiliza um espaço de endereçamento virtual plano para cada console (definido em `rc_console_info.c` para `RC_CONSOLE_GAME_BOY_ADVANCE = 12`). Quando o `rcheevos` solicita a leitura de um endereço através do callback `read_memory`, ele **NÃO** envia `0x03000000` ou `0x02000000`. Ele envia os seguintes offsets virtuais:

| Faixa de Endereço Virtual (`rcheevos`) | Tamanho | Banco Físico do GBA | Ponteiro Correspondente no `mGBA` |
| :--- | :--- | :--- | :--- |
| `0x000000` – `0x007FFF` | $32\text{ KB}$ (`0x8000`) | **IWRAM** (`0x03000000`) | `(const uint8_t*)gba->memory.iwram` |
| `0x008000` – `0x047FFF` | $256\text{ KB}$ (`0x40000`) | **EWRAM** (`0x02000000`) | `(const uint8_t*)gba->memory.wram` |
| `0x048000` – `0x057FFF` | $64\text{ KB}$ (`0x10000`) | **SRAM** (`0x0E000000`) | `(const uint8_t*)gba->sram` |

### Implementação de Referência do Callback `read_memory` (C/C++)
O agente de IA deve garantir que a implementação de leitura siga esta lógica exata (suportando inclusive leituras que cruzem limites ou fiquem contidas em cada bloco):

```c
uint32_t gba_rc_read_memory(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t* client) {
    struct mCore* core = (struct mCore*)rc_client_get_userdata(client);
    if (!core || !core->board) return 0;

    struct GBA* gba = (struct GBA*)core->board;
    uint32_t bytes_read = 0;

    while (num_bytes > 0) {
        if (address < 0x008000) {
            // IWRAM: 0x000000 - 0x007FFF (32 KB)
            if (!gba->memory.iwram) break;
            uint32_t offset = address;
            uint32_t avail = 0x008000 - offset;
            uint32_t to_read = (num_bytes < avail) ? num_bytes : avail;
            memcpy(buffer + bytes_read, ((const uint8_t*)gba->memory.iwram) + offset, to_read);
            address += to_read;
            bytes_read += to_read;
            num_bytes -= to_read;
        } else if (address < 0x048000) {
            // EWRAM: 0x008000 - 0x047FFF (256 KB)
            if (!gba->memory.wram) break;
            uint32_t offset = address - 0x008000;
            uint32_t avail = 0x040000 - offset;
            uint32_t to_read = (num_bytes < avail) ? num_bytes : avail;
            memcpy(buffer + bytes_read, ((const uint8_t*)gba->memory.wram) + offset, to_read);
            address += to_read;
            bytes_read += to_read;
            num_bytes -= to_read;
        } else if (address < 0x058000) {
            // SRAM / Save RAM: 0x048000 - 0x057FFF (64 KB)
            if (!gba->sram) break;
            uint32_t offset = address - 0x048000;
            uint32_t avail = 0x010000 - offset;
            uint32_t to_read = (num_bytes < avail) ? num_bytes : avail;
            memcpy(buffer + bytes_read, ((const uint8_t*)gba->sram) + offset, to_read);
            address += to_read;
            bytes_read += to_read;
            num_bytes -= to_read;
        } else {
            // Endereço fora do mapa suportado pelo GBA no rcheevos
            break;
        }
    }

    return bytes_read;
}
```

---

## 3. Ciclo de Vida e Rede da API `rc_client` (`rcheevos`)

A documentação oficial do RetroAchievements recomenda o uso da interface unificada `rc_client_t` (`#include <rc_client.h>`) em vez da antiga manipulação manual de `rc_runtime_t` e `rc_api_request_t`.

### 3.1. Inicialização e Callbacks Obrigatórios
```c
rc_client_t* client = rc_client_create(gba_rc_read_memory, android_http_server_call);
rc_client_set_userdata(client, core);
rc_client_set_event_handler(client, gba_rc_event_handler);
rc_client_set_hardcore_enabled(client, is_hardcore);
```

### 3.2. Regras de Ouro para o `server_call` no Android
1. **Assincronicidade Total:** A assinatura é:
   ```c
   void android_http_server_call(const rc_api_request_t* request,
                                 rc_client_server_callback_t callback,
                                 void* callback_data,
                                 rc_client_t* client);
   ```
   Esta função é chamada pelo `rcheevos` para fazer login, carregar dados do jogo, enviar desbloqueio de conquistas (*award achievement*) e enviar *pings* de Rich Presence.
2. **NUNCA bloqueie a thread que invocou `android_http_server_call`:**
   * Copie `request->url`, `request->post_data` e `request->content_type`, além dos ponteiros `callback` e `callback_data`.
   * Envie para uma fila de rede assíncrona (por exemplo, disparando um método Kotlin via JNI que usa `CoroutineScope(Dispatchers.IO)` + `OkHttpClient`).
   * **User-Agent Obrigatório:** O servidor do RetroAchievements bloqueia ou restringe requisições com User-Agent genérico. Defina um cabeçalho `User-Agent` claro no formato: `NomeDoSeuEmulador/1.0 (Android; mGBA)`.
3. **Retorno Thread-Safe da Resposta HTTP:**
   * Quando o `OkHttpClient` terminar a requisição na *thread* de IO do Kotlin, ele não deve chamar `callback(&server_response, callback_data)` diretamente de uma *thread* arbitrária enquanto a *thread* de emulação está rodando `rc_client_do_frame(client)` sem sincronização!
   * **Padrão Recomendado:** Enfileire a resposta HTTP em uma fila protegida por `std::mutex` e processe as respostas pendentes no início do ciclo da *thread* de emulação (antes de `rc_client_do_frame`), **OU** proteja todas as interações com `rc_client` (`rc_client_do_frame`, `rc_client_idle` e a execução do `callback` de rede) usando um `std::mutex` compartilhado (desde que o mutex só envolva o parse rápido do JSON do callback e **nunca** a espera de I/O de rede).

### 3.3. Carregamento de Jogo e Hash de GBA
Para identificar o jogo no RetroAchievements:
* Utilize `rc_client_begin_identify_and_load_game(client, RC_CONSOLE_GAME_BOY_ADVANCE, file_path, rom_data, rom_size, load_callback, userdata)` (se estiver com `rcheevos` compilado com `RC_CLIENT_SUPPORTS_HASH`), **OU** gere o hash MD5 com `rc_hash_generate_from_buffer(hash_buffer, RC_CONSOLE_GAME_BOY_ADVANCE, rom_data, rom_size)` e chame `rc_client_begin_load_game(client, hash_buffer, load_callback, userdata)`.
* **Regra Importante:** Só inicie o loop de `rc_client_do_frame(client)` após o callback de `begin_load_game` retornar `RC_OK`.

---

## 4. Sincronia de Emulação, Áudio e JNI no Android Studio

Quando o agente de IA estiver investigando lentidão, áudio picotando (*crackling*) ou travamentos (*crashes*), ele deve verificar estes 4 pontos na ponte Android/Nativo:

### 4.1. Desacoplamento de Áudio e Vídeo no mGBA
* O mGBA produz áudio na taxa nativa do GBA (tipicamente reamostrado pelo `blip_buf` interno do mGBA para $44100\text{ Hz}$ ou $48000\text{ Hz}$).
* Se a *thread* de emulação sofrer variações de tempo (*jitter*) por causa de Garbage Collection do Android, locks de JNI ou chamadas do RetroAchievements, o buffer de áudio ficará vazio (*underrun*).
* **Solução:** O buffer de áudio entre o `mCore` (`core->getAudioChannel` / `mAudioBuffer`) e o backend de áudio (`Oboe` / `OpenSL ES` / `AudioTrack` em modo `WRITE_NON_BLOCKING`) deve usar um *Ring Buffer* lock-free (ou com lock mínimo) com capacidade para pelo menos $2$ a $3\text{ frames}$ ($\approx 40\text{ ms}$ a $60\text{ ms}$) para absorver oscilações.

### 4.2. Prevenção de Vazamento de Referências Locais JNI (`ReferenceTable overflow`)
* Toda vez que o código C/C++ chama `env->NewStringUTF(...)`, `env->FindClass(...)` ou `env->NewByteArray(...)` (por exemplo, ao enviar dados de conquistas, placares ou requisições HTTP para o Kotlin), o Android cria uma *Local Reference*.
* Se a chamada ocorrer dentro da *thread* de emulação nativa (que roda um loop `while (running)` infinito e **não** retorna ao Java a cada frame), as *Local References* **nunca são limpas automaticamente**! Após $512$ alocações, o aplicativo fecha com `JNI DETECTED ERROR IN APPLICATION: local reference table overflow (max=512)`.
* **Solução:** Sempre chame `env->DeleteLocalRef(...)` imediatamente após usar qualquer objeto JNI criado na *thread* nativa, ou envolva o bloco com `env->PushLocalFrame(16)` e `env->PopLocalFrame( nullptr )`.

### 4.3. Regras do Modo Hardcore do RetroAchievements
Se o usuário ativar o modo Hardcore (`rc_client_get_hardcore_enabled(client)`):
1. O carregamento de Save States (`mCoreLoadState`) deve ser bloqueado (ou exigir confirmação de saída do modo Hardcore via `rc_client_set_hardcore_enabled(client, 0)`). Note que *salvar* estados (`mCoreSaveState`) é permitido, apenas *carregar* é proibido em Hardcore.
2. Fast-Forward (aceleração de FPS) é permitido, mas **Slow-Motion** (reduzir a velocidade do jogo abaixo de $1\times$), **Rewind** (`mCoreRewind`) e **Cheats** (`mCheatDevice`) devem ser estritamente desativados.

### 4.4. Checklist de Arquivos para o Agente Inspecionar no Repositório
1. **`CMakeLists.txt` / `build.gradle(.kts)`**: Verificar se os fontes do `rcheevos` (`src/rcheevos/*.c`, `src/rapi/*.c`, `src/rc_client.c`, `src/rhash/*.c`) estão compilados com `-DRC_CLIENT_SUPPORTS_HASH` e se o `mGBA` está compilado com otimizações (`-O2` / `-O3`).
2. **Camada JNI do Emulador (`*jni*.cpp`, `*native*.cpp`)**: Verificar o loop de `runFrame`, o `JNI_OnLoad` e a limpeza de referências JNI.
3. **Camada Nativa do RetroAchievements (`*cheevos*.cpp`, `*ra*.cpp`, `*retro*.cpp`)**: Verificar `read_memory`, `server_call`, `event_handler` e serialização de save states.
4. **Camada Kotlin de Rede e UI (`*RetroAchievements*.kt`, `*Network*.kt`, `*Emulator*.kt`)**: Verificar o `OkHttpClient`, gerenciamento de Token/Sessão no `SharedPreferences`/`DataStore` e exibição de *overlays/toasts* na *Main Thread* (`Dispatchers.Main`) quando o evento `RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED` é disparado.