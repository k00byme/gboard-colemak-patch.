# Gboard Cafe

Projeto de patches Morphe que recria no Gboard recursos visuais e de edição inspirados no Samsung Keys Cafe.

## Patches no único bundle .mpp

1. **Gboard Cafe Core** — runtime compartilhado, Activities e funções comuns.
2. **Settings Integration** — adiciona Gboard Cafe às configurações do próprio Gboard.
3. **Key Effects** — efeitos visuais na tecla pressionada.
4. **Board Effects** — efeitos visuais sobre a superfície inteira do teclado.
5. **Key Layout Editor** — editor visual de arrastar e soltar, mover teclas entre linhas, excluir teclas e criar teclas de texto.
6. **Diagnostics** — página opcional com o estado dos hooks e das preferências.
7. **Gboard structure discovery** — patch opcional de inspeção, desativado por padrão.

Os patches de recurso são independentes sempre que tecnicamente possível. São publicados juntos em um único arquivo `.mpp`; Core e Settings Integration são as dependências compartilhadas necessárias.

## O que mudou nesta versão

### Interface e cores

- Interface escura com cartões, campos e botões arredondados.
- Paleta de cores predefinidas com seleção visual.
- Campo hexadecimal continua disponível como opção personalizada.
- A cor selecionada permanece marcada quando a tela é aberta novamente.

### Efeitos

- Efeitos por tecla: ripple, flash, contorno, bounce, partículas, tinta, blob, blob expansivo e pegadas.
- Efeitos de teclado: anéis, faíscas, faíscas giratórias, fogos, blob, blob expansivo, contorno e paint gun.
- Os renderizadores têm duração própria, fade-out e remoção automática do overlay. Não dependem de o Gboard encaminhar ACTION_UP para o mesmo hook.
- A remoção também é agendada por um prazo na fila da própria View, como salvaguarda caso a animação deixe de receber frames.
- Salvar configurações limpa os efeitos ativos.

Os desenhos são implementações originais para o ciclo de eventos e desenho do Gboard; não reutilizam bytecode ou assets do Samsung.

### Editor visual de teclas

- Segure uma tecla e arraste para outra posição ou linha.
- Solte no espaço da linha para colocar no fim ou sobre outra tecla para inserir antes dela.
- Exclua quantas teclas desejar; as linhas podem ficar vazias.
- As teclas originais removidas ficam ocultas, não destruídas, para que o layout original possa ser restaurado.
- As teclas restantes de uma linha LinearLayout recebem pesos recalculados para preencher o espaço disponível.
- Adicione teclas de texto personalizadas, como `ç`, `;` ou `Tab`. Elas inserem o texto definido por meio da InputConnection do teclado.
- Os identificadores das teclas são estáveis e distinguem até rótulos repetidos.
- As teclas ocultadas pela versão anterior são migradas para o novo modelo.

O editor segue o fluxo de interação descrito nas referências do Keys Cafe: arrastar, reorganizar e adicionar/remover teclas. O Gboard Cafe redistribui largura por pesos; não tenta reproduzir o editor proprietário de dimensões da Samsung.

### Proteção de layouts

O editor só reaplica alterações quando o conjunto completo de teclas corresponde ao layout original capturado. Páginas numéricas, de símbolos ou outras variantes com conjunto diferente de teclas são ignoradas. A proteção evita aplicar uma disposição QWERTY a outro tipo de teclado.

## Correção crítica — colisão de classes

O crash anterior foi identificado a partir do stack trace e da comparação de DEX:

- O DEX da extensão tinha classes sintéticas de listeners renomeadas pelo R8 para descritores globais como `La;`, `Lb;` e `Lc;`.
- O DEX original do Gboard já continha uma classe `Lc;` com construtor diferente.
- A referência do Cafe podia resolver para a classe do Gboard e provocar `NoSuchMethodError`.

A correção adiciona `-dontobfuscate` e regras keep específicas à extensão, preservando o namespace `app.gboard.cafe.runtime` também para classes sintéticas. O DEX do bundle deve ser inspecionado depois de cada mudança de build para confirmar que os listeners continuam nesse namespace.

## Alvo inicial

- Pacote: `com.google.android.inputmethod.latin`
- Versão: `18.5.4.993608717-release-arm64-v8a`
- Arquivo de referência: XAPK arm64-v8a
- Compatibilidade: somente a versão acima até nova análise

## Limitações conhecidas

- Teclas personalizadas são teclas de texto: inserem o texto configurado; não criam um novo `SoftKeyDef` nativo com ações avançadas, long-press, alternância de símbolos ou função de sistema.
- O editor movimenta teclas entre as linhas já existentes; não cria linhas novas nem oferece ajuste manual de altura/largura.
- A redistribuição automática depende de as linhas do Gboard usarem `LinearLayout` e pesos compatíveis. Em outros tipos de container, o patch falha sem tentar redistribuir.
- Somente o layout primário cujo conjunto de teclas corresponda ao capturado é editável. Outros layouts são ignorados com segurança.
- Efeitos, multitouch, acessibilidade, floating/tablet mode e diferentes idiomas precisam de validação no aparelho.

## Build e validação

O GitHub Actions gera um único bundle `.mpp`. Build bem-sucedido confirma compilação, não prova instalação nem comportamento em aparelho. O critério para uma versão testada é aplicar os patches no Gboard 18.5.4, instalar no Galaxy A15 e testar a tela, animações, arrastar/soltar, teclas personalizadas, persistência e restauração.

## Licença e marcas

Projeto independente, sem afiliação com Samsung ou Google. Não redistribui os APKs enviados para análise.
