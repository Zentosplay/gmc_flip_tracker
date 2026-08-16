# GMC Flip Tracker

RuneLite plugin for [GetMasterCrafter](https://getmastercrafter.com). Reads your own
Grand Exchange trades and sends them to your GMC Profit Tracker.

**Status: not yet submitted to the RuneLite Plugin Hub.** It still needs a live,
in-game test pass (see "Testando ao vivo" below) before a Plugin Hub PR is opened.

## O que ele faz

- Observa suas ofertas na Grand Exchange (evento `GrandExchangeOfferChanged` do
  RuneLite). Quando uma oferta **completa** ou **cancela**, o plugin monta um
  registro da transacao e envia para `https://getmastercrafter.com/api/osrs/plugin/trades`,
  autenticado com o token que voce cola nas configuracoes do plugin.
- **100% passivo.** O plugin nunca clica, digita, nem executa nenhuma acao no jogo -
  ele so le o estado da oferta e faz chamadas HTTP de saida.
- **Desligado por padrao.** O envio so comeca depois que voce ativa a opcao
  "Enviar transacoes para o GetMasterCrafter" nas configuracoes do plugin.

## Dados enviados

Para cada transacao completada ou cancelada, o plugin envia:

| Campo | O que e |
|---|---|
| `itemId` | ID do item na OSRS Wiki |
| `type` | `"buy"` ou `"sell"` |
| `status` | `"completed"` ou `"cancelled"` |
| `filledQuantity` | Quantidade que realmente foi comprada/vendida |
| `unitPrice` | Preco medio efetivamente pago/recebido por unidade |
| `occurredAt` | Momento (no seu computador) em que a transacao foi detectada |
| `idempotencyKey` | Identificador interno para evitar duplicatas (ver abaixo) |

Nenhum outro dado do jogo, da conta, ou de outros jogadores e lido ou enviado. O nome
do personagem **nao** e enviado no corpo da requisicao - o servidor identifica o
personagem pelo token usado na autenticacao.

## Como usar

1. Gere um token na sua conta em getmastercrafter.com (Configuracoes > Plugin OSRS).
2. No RuneLite, abra as configuracoes do plugin "GMC Flip Tracker" e cole o token no
   campo "GMC API token".
3. Ative "Enviar transacoes para o GetMasterCrafter".
4. O painel lateral (icone do plugin na barra de navegacao) mostra o estado da conexao,
   o resultado do ultimo envio, e qualquer erro em texto legivel.

## Como buildar

```
./gradlew build
```

Compila e roda os testes unitarios (`TradeEventTranslatorTest`,
`IdempotencyKeyGeneratorTest`, `PluginTradeEventSerializationTest`). Nenhuma
dependencia alem do que o `runelite-client` ja fornece (OkHttp e Gson, injetados via
`@Inject`) - nenhuma dependencia nova foi adicionada.

## Testando ao vivo

Isto so pode ser verificado por voce, dentro do jogo - nao pode ser automatizado (e
seria contra as diretrizes de terceiros da Jagex):

```
./gradlew run
```

Faca login com uma conta Jagex (veja
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)),
cole um token real, ative o envio, e confira:

1. Comprar algo barato na GE -> a transacao aparece no Profit Tracker do GMC, no
   personagem certo, com quantidade/preco corretos.
2. Cancelar uma oferta (com e sem preenchimento parcial) -> aparece como `cancelled`.
3. Colar um token invalido -> o painel mostra um erro legivel ("token invalido...").
4. Se possivel, reproduzir um reenvio (ex.: desconectar a internet momentaneamente) e
   confirmar que a transacao nao aparece duplicada no Profit Tracker.

## Estrategia da idempotencyKey

O endpoint e idempotente por `idempotencyKey`, mas a constraint de unicidade no
servidor e por **conta** (`UNIQUE(user_id, idempotencyKey)`), nao por personagem. Se
duas contas de personagem da mesma conta GMC gerarem a mesma chave para trades
diferentes, a segunda vira um "duplicate" silencioso no servidor. Por isso a chave
sempre incorpora o **nome do personagem**:

```
sha256hex(nome_do_personagem + itemId + slot + type + status + filledQuantity + unitPrice + momento_de_conclusao)
```

O "momento de conclusao" e capturado **uma unica vez** pelo plugin, na primeira vez
que uma oferta terminal (comprada/vendida/cancelada) e observada naquele slot durante
a sessao atual. Reenvios (retry) da mesma tentativa reusam esse mesmo valor, entao
produzem sempre a mesma chave - por isso um reenvio nunca duplica.

`GrandExchangeOfferChanged` dispara varias vezes conforme uma oferta preenche aos
poucos, e tambem pode redisparar um estado terminal ja visto (por exemplo, ao logar
novamente com uma oferta completa ainda nao coletada esperando no slot). O plugin
mantem uma memoria em memoria (nunca gravada em disco) do ultimo estado terminal ja
tratado por slot, e ignora silenciosamente uma repeticao identica - so monta e envia
um evento quando o slot muda para um estado terminal genuinamente novo.

## Limitacoes conhecidas (decisao deliberada, nao bug)

- **Reinicio do plugin/cliente com oferta ja completa e nao coletada no slot:** a
  memoria de sessao e perdida ao reiniciar. Se uma oferta ja completa (mas ainda nao
  coletada) estiver sentada num slot quando o plugin reinicia, ela sera "redescoberta"
  e recebera um novo "momento de conclusao" -> uma nova `idempotencyKey` -> uma
  duplicata real no servidor. Isso e um trade-off deliberado para manter o plugin
  simples (sem persistir estado em disco), nao um bug.
- **Sem fila persistente de reenvio:** se o envio de uma transacao falhar em todas as
  tentativas de retry (rede fora do ar por mais de ~1min, ou servico indisponivel por
  muito tempo), o evento e descartado e um aviso e gravado no log do RuneLite - nao ha
  fila em disco que sobrevive a um reinicio do plugin. Se a oferta ainda estiver
  esperando (nao coletada) no slot, reiniciar o plugin fara uma nova tentativa (sujeito
  a mesma ressalva de duplicata acima); se ja tiver sido coletada, aquela transacao
  especifica nao sera reenviada.
- **Preco unitario:** `unitPrice` e a media efetivamente paga/recebida
  (`spent / filledQuantity`), nao o preco listado na oferta - eles podem diferir
  quando a Grand Exchange fecha a negociacao a um preco melhor do que o solicitado.
