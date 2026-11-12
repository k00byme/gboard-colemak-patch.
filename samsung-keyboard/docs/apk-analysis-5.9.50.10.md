# Análise técnica — Samsung Keyboard 5.9.50.10

## Arquivo analisado

- Arquivo recebido: com.samsung.android.honeyboard_5.9.50.10-595010300_minAPI33(arm64-v8a,armeabi-v7a,x86,x86_64)(nodpi)_apkmirror.com.apk
- Pacote: com.samsung.android.honeyboard
- Versão: 5.9.50.10
- Version code: 595010300
- SHA-256: c87bfd9ae4bb9e17f527a53aaccd955cbc203083dabe9fc9673134d4adbbfb8e
- DEXs: classes.dex, classes2.dex, classes3.dex
- O APK também contém bibliotecas nativas de previsão/transliteração, incluindo XT9 e Transliterate.

O APK original não é incluído no repositório.

## 1. Botão de colagem instantânea

### Estruturas encontradas
- icecone/clipboard/viewmodel/ClipboardViewModel
- icecone/clipboard/stub/ClipBoardHandler
- textboard/keyboardbar/view/ClipboardModeLayout
- Preferências de barra: settings/styleandlayout/keyboardtoolbar/KeyboardToolbarSettingsFragment

Os recursos do APK contêm textos associados a “Edit toolbar”, “Paste”, “Paste copied text”, “Clipboard” e limites de quantidade de ícones. Isso confirma que há uma infraestrutura de barra personalizável, mas ainda não identifica o ponto exato em que os itens são construídos e conectados aos eventos de toque.

### Implementação pretendida
Adicionar um item próprio à barra que leia o texto principal disponível no clipboard e o insira no campo ativo pela conexão de entrada do teclado. A ação deve ignorar clipboard vazio e conteúdo não textual, sem abrir o painel de clipboard.

**Pendente:** identificar a classe que monta a lista de itens da barra e a ponte correta para a InputConnection ativa.

## 2. Correção apenas de acentuação

### Estruturas encontradas
- base/spellcheckerservice/HoneyBoardSpellCheckerService
- Modelos e componentes de previsão presentes nos DEXs e nas bibliotecas nativas.
- Recursos incluem opções de “Suggest text corrections”, “Predictive text” e correção gramatical.

O serviço de spell checker é um ponto de investigação, mas não se deve reutilizar a correção ortográfica geral: o requisito é estritamente limitado à acentuação.

### Regra funcional
- nao → não
- voce → você
- tambem → também
- cafe → café
- facil → fácil
- katorze → permanece katorze
- esta deve permanecer inalterado quando o contexto não permitir distinguir esta de está.

A implementação deverá usar um léxico de formas acentuadas e contexto. Em casos ambíguos, não deve alterar o texto. Não deve substituir erros ortográficos por palavras corretas.

**Pendente:** encontrar o caminho de entrada de texto que permita aplicar essa regra sem ativar a autocorreção geral do Samsung Keyboard.

## 3. Centralizar a sugestão principal

### Estruturas encontradas
- textboard/candidate/viewmodel/CandidateViewModel
- textboard/candidate/view/CandidateTableLayout
- textboard/databinding/CandidateViewBindingImpl
- textboard/databinding/CandidatesRowLayoutBindingImpl

O método CandidateViewModel.getSuggestionIndex(boolean) foi localizado. Na análise de chamadas, a referência direta encontrada até agora parte de getSuggestionNumber(). Portanto, ele aparenta participar da numeração exibida da sugestão; **não está confirmado como o método que determina a posição horizontal dos candidatos**. Não é seguro forçar o retorno para 1 sem rastrear o adaptador/lista e a ordem dos itens.

A estrutura CandidatesRowLayoutBindingImpl referencia uma área de lista/RecyclerView e é um dos pontos de investigação para encontrar a ordem visual real.

**Pendente:** mapear o adaptador ou a lista que alimenta as três posições, preservando a associação entre texto, dados de seleção e ação ao tocar em cada sugestão.

## Compatibilidade e validação

Há projetos Morphe públicos com patches para versões anteriores do Samsung Keyboard 5.9.40.x. A versão deste APK é 5.9.50.10 e não deve ser tratada como compatível por aproximação. Fingerprints e offsets precisam ser confirmados na versão-alvo.

## Estado desta análise

Esta é uma análise estrutural inicial, não uma decompilação completa nem uma implementação dos recursos. Nenhum patch funcional foi gerado ou testado no aparelho nesta etapa. O próximo passo técnico é fechar os três pontos de entrada e, então, escrever os patches específicos para esta versão.
