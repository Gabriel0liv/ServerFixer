# Server Fixes

Um mod abrangente de performance, correção de exploits e utilitários administrativos para Minecraft 1.20.1 (Forge). Projetado especificamente para grandes modpacks e servidores dedicados para garantir estabilidade, justiça e capacidades profundas de diagnóstico.

---

## Principais Funcionalidades

### Proteção contra Exploits

- **Anti-Swap (Ninja Cancellation)**: Previne o "exploit de troca de arma", onde jogadores trocam de item no mesmo tick para aplicar atributos de alto dano ou velocidades de ataque em armas não pretendidas.
- **Malum Scythe Fix**: Corrige um bug específico no mod Malum, onde o dano mágico era aplicado incorretamente em dobro durante ataques de varredura (sweep) com a foice.
- **Backstabbing Fix**: Restringe o encantamento "Backstabbing" (Punhalada nas Costas) do Farmer's Delight apenas para ataques corpo a corpo físicos (`PLAYER_ATTACK`). Isso evita que o bônus seja aplicado indevidamente a magias e outros tipos de dano modificado.

### Seletores de Entidades Avançados

O mod estende os seletores vanilla (`@e`, `@a`, `@p`, `@r`, `@s`) com argumentos poderosos para filtragem técnica.

#### Filtros de Efeitos de Poção

- `effect=<id>`: Filtra entidades que possuem o efeito especificado (ex: `@e[effect=minecraft:speed]`).
- `effectlvl=<range>`: Filtra pelo nível (amplificador) do efeito anterior. Aceita intervalos (ex: `@e[effect=minecraft:strength,effectlvl=1..3]`).

#### Filtros de Inventário Curios

Se o mod **Curios API** estiver instalado, você pode filtrar jogadores por itens equipados em slots específicos:

- `curio=<item>` ou `curios=<item>`: Verifica se o item está em **qualquer** slot de Curios.
- **Slots Específicos**: Você pode usar o nome do slot diretamente:
  - `ring=<item>` (Anéis)
  - `belt=<item>` (Cinto)
  - `necklace=<item>` (Colar)
  - `charm=<item>` (Amuletos)
  - `head=<item>` / `body=<item>` / `back=<item>`
  - `hands=<item>` / `feet=<item>` / `bracelet=<item>`
- Exemplo de uso: `@a[ring=minecraft:iron_ingot,necklace=botania:amulet_base]`.

### Melhorias de Villagers

- **Brain Throttling**: Taxas de atualização (tick rates) configuráveis para a IA dos villagers, reduzindo o lag do servidor em salas de troca.
- **Trocas Infinitas**: Um sistema baseado em tags (padrão: `sf_infinite_trades`) que reseta automaticamente as trocas dos villagers para jogadores específicos, ideal para benefícios VIP ou recompensas de quests.

### Auditoria de Combate e Debug

- **Debugs por Jogador**: Administradores e jogadores podem ativar mensagens de debug pessoais que não poluem o chat dos outros.
- **Detalhamento de Dano**: Cálculo em tempo real da mitigação de dano, mostrando exatamente quanto dano foi reduzido por Armadura, Toughness, Resistência e Encantamentos (EPF).

### Throttling de Performance

Taxas de atualização customizáveis para blocos conhecidos por causar lag em mods populares:

- **Farm and Charm**: Throttling para Cooking Pots (Panelas) e Roasters (Torradores).
- **Vinery**: Throttling para Fermentation Barrels (Barris de Fermentação).

### Persistência de Atributos e Efeitos (Anti-Wipe)

Desenvolvido para combater o problema de "limpeza de NBT" em servidores híbridos (como Arclight/Mohist), o mod implementa um sistema de backup invisível:

- **Atributos Persistentes**: Sincroniza automaticamente tags de atributos (`AttributeModifiers` e `CurioAttributeModifiers`) com um backup interno. Se o servidor limpar os atributos ao carregar o item, o mod os restaura instantaneamente.
- **Efeitos em Itens**: Mantém a persistência de efeitos customizados de "Ao Atingir" e "Ao Ser Atingido".

### Editor de Atributos de Item

Um editor avançado via chat para modificar atributos de qualquer item na mão:

- Suporte a slots Vanilla e slots do **Curios API**.
- Interface clicável para facilitar a edição.
- Comandos: `/serverfixes item attribute <list/set/remove>`.

### Modificadores de Efeito em Itens (On-Hit/On-Hurt)

Permite adicionar efeitos de poção que são aplicados dinamicamente durante o combate:

- `on_hit`: Aplica efeito ao atacar uma entidade.
- `on_hurt`: Aplica efeito ao receber dano de uma entidade.
- Configuração de chance (0-100%), duração, nível e alvo (Self ou Target).
- Comandos: `/serverfixes effect_modifier <add/list/remove>`.

---

## Catálogo de Comandos

O comando principal é `/serverfixes` (requer permissão nível 2).

### Geral e Status

| Comando                        | Descrição                                                                                          |
| :----------------------------- | :------------------------------------------------------------------------------------------------- |
| `/serverfixes status`          | Veja o estado operacional de todos os módulos do mod.                                              |
| `/serverfixes status <module>` | Get status detalhado para: `villagers`, `antiswap`, `infinitetrade`, `debug`, `throttle` ou `fix`. |

### Itens e Atributos (Novo!)

| Comando                                                      | Descrição                                                                                                            |
| :----------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------- |
| `/serverfixes item attribute list`                           | Lista todos os atributos do item na mão principal com links para edição.                                             |
| `/serverfixes item attribute set <attr> <valor> <op> <slot>` | Define um atributo em um slot específico (suporta Curios). Operações: `ADDITION`, `MULTIPLY_BASE`, `MULTIPLY_TOTAL`. |
| `/serverfixes item attribute remove <attr>`                  | Remove um atributo específico do item.                                                                               |

### Modificadores de Efeito (Novo!)

| Comando                                                                                                | Descrição                                             |
| :----------------------------------------------------------------------------------------------------- | :---------------------------------------------------- |
| `/serverfixes effect_modifier add <on_hit/on_hurt> <efeito> <segundos> <nível> <chance> [self/target]` | Adiciona um efeito de combate ao item.                |
| `/serverfixes effect_modifier list`                                                                    | Lista os efeitos de combate aplicados ao item na mão. |
| `/serverfixes effect_modifier remove <on_hit/on_hurt> <index>`                                         | Remove um modificador de efeito pelo seu índice.      |

### Correções e Ajustes (Fixes)

| Comando                                        | Descrição                                                        |
| :--------------------------------------------- | :--------------------------------------------------------------- |
| `/serverfixes fix backstabbing <true/false>`   | Ativa/desativa a correção do exploit de Backstabbing com magias. |
| `/serverfixes fix turtle enabled <true/false>` | Ativa/desativa o reequilíbrio da Poção de Mestre Tartaruga.      |

### Suporte a IDs de Texto em NBT (Novo!)

Agora você pode usar os IDs de texto dos efeitos (ex: `"minecraft:speed"`) em qualquer lugar que use NBT de efeitos (`MobEffectInstance`), como poções customizadas.

- Exemplo de comando: `/give @p potion{CustomPotionEffects:[{Id:"minecraft:regeneration",Duration:200,Amplifier:1}]}`
- Funciona com efeitos de **qualquer mod** sem precisar saber o ID numérico.
- `/serverfixes fix effectids <true/false>`: Ativa/desativa este suporte a IDs de texto.
- `/serverfixes item add_effect <id_do_efeito> <segundos> [nível]`: Adiciona um efeito ao item na sua mão (útil para criar poções customizadas sem editores externos).

| Comando                               | Descrição                                                              |
| :------------------------------------ | :--------------------------------------------------------------------- |
| `/serverfixes antiswap <true/false>`  | Toggle global para o cancelamento de ataque por troca de arma.         |
| `/serverfixes antiswap cooldown <ms>` | Define a duração (0-5000ms) do bloqueio de ataque após trocar de item. |
| `/serverfixes antiswap reset`         | Limpa o cache interno de snapshots de itens para todos os jogadores.   |

### Otimização de Villagers e Trocas

| Comando                                   | Descrição                                                                       |
| :---------------------------------------- | :------------------------------------------------------------------------------ |
| `/serverfixes villagerticks <num>`        | Define a taxa de tick da IA dos villagers (1-100). Valores altos reduzem o lag. |
| `/serverfixes infinitetrade <true/false>` | Toggle global para o sistema de trocas infinitas.                               |
| `/serverfixes infinitetrade tag <nome>`   | Define a tag de entidade necessária para um jogador ter trocas infinitas.       |

### Debug Pessoal (Por Jogador)

Estes comandos afetam apenas o jogador que os executa. As mensagens de debug só serão mostradas para quem tiver o respectivo marcador ativado.

| Comando                                            | Descrição                                                                 |
| :------------------------------------------------- | :------------------------------------------------------------------------ |
| `/serverfixes debug damage received <true/false>`  | Ativa auditoria de combate pessoal para dano recebido.                    |
| `/serverfixes debug damage dealt <true/false>`     | Ativa auditoria de combate pessoal para dano causado.                     |
| `/serverfixes debug damage breakdown <true/false>` | (Global) Ativa o detalhamento de cálculos (Armadura, EPF) nas auditorias. |
| `/serverfixes debug villagers <true/false>`        | Ativa mensagens de debug pessoais para interações com villagers.          |
| `/serverfixes debug antiswap <true/false>`         | Ativa mensagens de debug pessoais para cancelamentos do Anti-Swap.        |

### Throttling de Performance (Blocos de Mods)

| Comando                                           | Descrição                                                  |
| :------------------------------------------------ | :--------------------------------------------------------- |
| `/serverfixes throttle farmandcharm <true/false>` | Ativa o throttling para blocos do mod Farm and Charm.      |
| `/serverfixes throttle cookingpot <taxa>`         | Define a taxa de tick (1-1000) para o Cooking Pot.         |
| `/serverfixes throttle roaster <taxa>`            | Define a taxa de tick (1-1000) para o Roaster.             |
| `/serverfixes throttle vinery <true/false>`       | Ativa o throttling para blocos do mod Vinery.              |
| `/serverfixes throttle barrel <taxa>`             | Define a taxa de tick (1-1000) para o Fermentation Barrel. |

### Utilitários de Efeitos

| Comando                                                              | Descrição                                                                 |
| :------------------------------------------------------------------- | :------------------------------------------------------------------------ |
| `/serverfixes effect info <alvos>`                                   | Lista info detalhada (Efeito, Nível, Tempo Restante) para o(s) alvo(s).   |
| `/serverfixes effect modify <alvos> <efeito> <segundos> [amp_delta]` | Modifica um efeito ativo adicionando/subtraindo tempo ou níveis de poder. |

---

## Configuração

O mod gera um arquivo `server_fixes-server.toml` na pasta `serverconfig` do mundo. A maioria das configurações pode ser ajustada em tempo real via comandos, mas o arquivo de config permite definir os valores base permanentes.

## Requisitos

- **Minecraft**: 1.20.1
- **Forge**: 47.4.10+
- **Curios API**: (Opcional, mas recomendado para suporte total aos seletores)

## Hotswap Avançado (Grátis e Só Dev)

O projeto agora suporta hotswap avançado de forma opcional, sem impactar `build`/`jar` de release.

### Como funciona

- Só ativa se você passar `-PsfEnableAdvancedHotswap=true` no comando.
- Usa `-javaagent` apenas no `runClient`.
- Não altera artefato final, CI ou publicação quando desativado (comportamento padrão).

### Pré-requisitos

- `hotswap-agent.jar` (gratuito, open-source).
- (Opcional, recomendado para mudanças estruturais) JDK com DCEVM.

### Comando de uso (Windows / PowerShell)

```powershell
./gradlew runClient -PsfEnableAdvancedHotswap=true -PsfHotswapAgentPath="C:/tools/hotswap-agent.jar"
```

### Configuração opcional

- Arquivo padrão de config do agente: `dev/hotswap/hotswap-agent.properties`
- Se quiser apontar outro arquivo:

```powershell
./gradlew runClient -PsfEnableAdvancedHotswap=true -PsfHotswapAgentPath="C:/tools/hotswap-agent.jar" -PsfHotswapConfigPath="C:/meu-caminho/hotswap-agent.properties"
```

### Importante

- Se você não passar as flags acima, nada muda no fluxo normal.
- Se ativar hotswap sem `sfHotswapAgentPath`, o Gradle falha com mensagem clara para evitar configuração incompleta.
