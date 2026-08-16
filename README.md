# GMC Flip Tracker

RuneLite plugin for [GetMasterCrafter](https://getmastercrafter.com). Lê suas
próprias transações da Grand Exchange e envia para o seu GMC Profit Tracker.

**Status: testado ao vivo com sucesso (compra/venda, cancelamento parcial, token
inválido), ainda não submetido ao RuneLite Plugin Hub.**

## O que ele faz

- Observa suas ofertas na Grand Exchange (evento `GrandExchangeOfferChanged` do
  RuneLite). Quando uma oferta **completa** ou **cancela**, o plugin monta um
  registro da transação e envia para `https://getmastercrafter.com/api/osrs/plugin/trades`,
  autenticado com o token que você cola nas configurações do plugin.
- **100% passivo.** O plugin nunca clica, digita, nem executa nenhuma ação no jogo -
  ele só lê o estado da oferta e faz chamadas HTTP de saída.
- **Desligado por padrão.** O envio só começa depois que você ativa a opção
  "Send trades to GetMasterCrafter" nas configurações do plugin.
- **Painel em 3 idiomas** (inglês/português/espanhol), detectado automaticamente pelo
  idioma do sistema operacional, com fallback pro inglês se não for nenhum dos três.
  A tela de configurações do RuneLite (nomes/descrições dos campos) fica só em
  inglês - anotações Java são fixadas em tempo de compilação, não dá pra trocar
  junto com o idioma do painel.
- **Links da comunidade** no painel: ícone do Discord (abre o convite do servidor) e
  ícone de globo (abre o site do GMC), via `LinkBrowser` do RuneLite.

## Dados enviados

Para cada transação completada ou cancelada, o plugin envia:

| Campo | O que é |
|---|---|
| `itemId` | ID do item na OSRS Wiki |
| `type` | `"buy"` ou `"sell"` |
| `status` | `"completed"` ou `"cancelled"` |
| `filledQuantity` | Quantidade que realmente foi comprada/vendida |
| `unitPrice` | Preço médio efetivamente pago/recebido por unidade |
| `occurredAt` | Momento (no seu computador) em que a transação foi detectada |
| `idempotencyKey` | Identificador interno para evitar duplicatas (ver abaixo) |

Nenhum outro dado do jogo, da conta, ou de outros jogadores é lido ou enviado. O nome
do personagem **não** é enviado no corpo da requisição - o servidor identifica o
personagem pelo token usado na autenticação.

## Como usar

1. Gere um token na sua conta em getmastercrafter.com (Configurações > Plugin OSRS).
2. No RuneLite, abra as configurações do plugin "GMC Flip Tracker" e cole o token no
   campo "GMC API token".
3. Ative "Send trades to GetMasterCrafter".
4. O painel lateral (ícone do plugin na barra de navegação) mostra o estado da
   conexão (com um indicador colorido: verde = ativo, cinza = desligado, vermelho =
   erro), o resultado do último envio, e qualquer erro em texto legível - no idioma
   do seu sistema operacional (inglês/português/espanhol).

## Como buildar

```
./gradlew build
```

Compila e roda os testes unitários (`TradeEventTranslatorTest`,
`IdempotencyKeyGeneratorTest`, `PluginTradeEventSerializationTest`). Nenhuma
dependência além do que o `runelite-client` já fornece (OkHttp e Gson, injetados via
`@Inject`) - nenhuma dependência nova foi adicionada.

## Testando ao vivo

Isto só pode ser verificado por você, dentro do jogo - não pode ser automatizado (e
seria contra as diretrizes de terceiros da Jagex):

```
./gradlew run
```

Faça login com uma conta Jagex (veja
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)),
cole um token real, ative o envio, e confira:

1. Comprar algo barato na GE -> a transação aparece no Profit Tracker do GMC, no
   personagem certo, com quantidade/preço corretos.
2. Cancelar uma oferta (com e sem preenchimento parcial) -> aparece como `cancelled`.
3. Colar um token inválido -> o painel mostra um erro legível ("token inválido...").
4. Se possível, reproduzir um reenvio (ex.: desconectar a internet momentaneamente) e
   confirmar que a transação não aparece duplicada no Profit Tracker.

## Estratégia da idempotencyKey

O endpoint é idempotente por `idempotencyKey`, mas a constraint de unicidade no
servidor é por **conta** (`UNIQUE(user_id, idempotencyKey)`), não por personagem. Se
duas contas de personagem da mesma conta GMC gerarem a mesma chave para trades
diferentes, a segunda vira um "duplicate" silencioso no servidor. Por isso a chave
sempre incorpora o **nome do personagem**:

```
sha256hex(nome_do_personagem + itemId + slot + type + status + filledQuantity + unitPrice + momento_de_conclusao)
```

O "momento de conclusão" é capturado **uma única vez** pelo plugin, na primeira vez
que uma oferta terminal (comprada/vendida/cancelada) é observada naquele slot durante
a sessão atual. Reenvios (retry) da mesma tentativa reusam esse mesmo valor, então
produzem sempre a mesma chave - por isso um reenvio nunca duplica.

`GrandExchangeOfferChanged` dispara várias vezes conforme uma oferta preenche aos
poucos, e também pode redisparar um estado terminal já visto (por exemplo, ao logar
novamente com uma oferta completa ainda não coletada esperando no slot). O plugin
mantém uma memória em memória (nunca gravada em disco) do último estado terminal já
tratado por slot, e ignora silenciosamente uma repetição idêntica - só monta e envia
um evento quando o slot muda para um estado terminal genuinamente novo.

## Idiomas

Texto do painel (GmcStatusPanel) e das mensagens de erro fica em
src/main/resources/com/getmastercrafter/fliptracker/messages*.properties (base =
inglês, _pt = português, _es = espanhol), carregado via Messages.java (ResourceBundle
padrão do Java, detecta o idioma do sistema automaticamente). Os valores acentuados
nesses arquivos usam escape unicode (ASCII puro no disco) - ao adicionar uma chave
nova, mantenha esse padrão nos três arquivos.

## Limitações conhecidas (decisão deliberada, não bug)

- **Reinicio do plugin/cliente com oferta já completa e não coletada no slot:** a
  memória de sessão é perdida ao reiniciar. Se uma oferta já completa (mas ainda não
  coletada) estiver sentada num slot quando o plugin reinicia, ela será "redescoberta"
  e receberá um novo "momento de conclusão" -> uma nova idempotencyKey -> uma
  duplicata real no servidor. Isso é um trade-off deliberado para manter o plugin
  simples (sem persistir estado em disco), não um bug.
- **Sem fila persistente de reenvio:** se o envio de uma transação falhar em todas as
  tentativas de retry (rede fora do ar por mais de ~1min, ou serviço indisponível por
  muito tempo), o evento é descartado e um aviso é gravado no log do RuneLite - não há
  fila em disco que sobrevive a um reinicio do plugin. Se a oferta ainda estiver
  esperando (não coletada) no slot, reiniciar o plugin fará uma nova tentativa (sujeito
  à mesma ressalva de duplicata acima); se já tiver sido coletada, aquela transação
  específica não será reenviada.
- **Preço unitário:** unitPrice é a média efetivamente paga/recebida
  (spent / filledQuantity), não o preço listado na oferta - eles podem diferir
  quando a Grand Exchange fecha a negociação a um preço melhor do que o solicitado.

## Assets

assets/ guarda os PNGs de origem (logo completo em alta resolução, ícones
48x48/32x32) usados como fonte para derivar os recursos empacotados em
src/main/resources/ (icon.png, logo_header.png) e o icon.png da raiz (ícone de
vitrine do Plugin Hub, máximo 48x72px). assets/ não é empacotado no jar do plugin.
