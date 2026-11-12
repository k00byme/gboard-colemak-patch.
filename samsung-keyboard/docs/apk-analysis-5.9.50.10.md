# Análise técnica — Samsung Keyboard 5.9.50.10

## Arquivo analisado

- Pacote: com.samsung.android.honeyboard
- Versão: 5.9.50.10
- Version code: 595010300
- SHA-256: c87bfd9ae4bb9e17f527a53aaccd955cbc203083dabe9fc9673134d4adbbfb8e
- DEXs: classes.dex, classes2.dex, classes3.dex
- Bibliotecas nativas de previsão/transliteração: XT9, Transliterate, fluency e outras.

O APK original não é armazenado no repositório.

## 1. Botão de colagem instantânea

### Estruturas encontradas
- icecone/clipboard/viewmodel/ClipboardViewModel
- icecone/clipboard/stub/ClipBoardHandler
- textboard/keyboardbar/view/ClipboardModeLayout
- settings/styleandlayout/keyboardtoolbar/KeyboardToolbarSettingsFragment

Os recursos incluem rótulos como “Edit toolbar”, “Paste”, “Paste copied text” e “Clipboard”. Isso confirma a existência de componentes de clipboard e configuração da barra, mas não identifica ainda o construtor da lista de itens e o callback de toque usado pela barra superior.

### Implementação desejada
Adicionar um item separado do painel Clipboard. Ao tocar, deve inserir diretamente o texto principal disponível na área de transferência no campo ativo, sem abrir o painel.

**Estado: pendente.** Não foi criado um botão falso ou um patch que apenas altere rótulos.

## 2. Correção apenas de acentuação

### Estruturas encontradas
- base/spellcheckerservice/HoneyBoardSpellCheckerService
- Lmd/b, wrapper usado pelo teclado para encaminhar operações da InputConnection
- Lmd/b.commitText(CharSequence, int)
- Lmd/b.getTextBeforeCursor(int, int)
- Lmd/b.deleteSurroundingText(int, int)

A sessão HoneyBoardSpellCheckerService.onGetSuggestions retorna uma SuggestionsInfo vazia. Portanto, não é um ponto funcional para implementar as sugestões pretendidas.

### Hook experimental criado
O método Lmd/b.commitText existe no classes.dex da versão 5.9.50.10 com assinatura commitText(CharSequence, int): boolean. O patch injeta uma chamada para AccentOnlyRuntime.normalizeCommit(Object, CharSequence) no início do método e substitui o parâmetro de texto pelo retorno do helper.

O helper:
- atua apenas quando há espaço ou pontuação de encerramento;
- tenta corrigir apenas palavras presentes num léxico conservador;
- preserva palavras desconhecidas e capitalização;
- não modifica formas ambíguas como esta/está, tem/têm, pais/país ou e/é;
- em caso de erro de reflexão ou incompatibilidade, devolve o texto original.

Exemplos iniciais: nao → não, voce → você, tambem → também, cafe → café, facil → fácil. A palavra katorze permanece katorze.

O patch está desativado por padrão. O runtime Java foi compilado localmente e passou em testes com um wrapper simulado. Isso não valida o hook no Samsung Keyboard real.

**Pendente:** confirmar que o fingerprint é aceito pelo Morphe, testar o comportamento com composição do Samsung Keyboard e expandir o léxico sem introduzir falsos positivos.

## 3. Centralizar a sugestão principal

### Estruturas encontradas
- textboard/candidate/viewmodel/CandidateViewModel
- textboard/candidate/view/CandidateTableLayout
- textboard/databinding/CandidateViewBindingImpl
- textboard/databinding/CandidatesRowLayoutBindingImpl
- CandidateContainerBinding, que mantém candidateScrollHolder (RecyclerView) e candidateMultiLineHolder (FlexboxLayout)

CandidateViewModel.getSuggestionIndex(boolean) foi localizado, mas a análise de chamadas mostra que ele é usado por getSuggestionNumber() para apresentar a numeração da sugestão. Não é o método que define a ordem horizontal. Forçá-lo para 1 poderia alterar a numeração sem mudar a posição visual.

**Pendente:** identificar a lista/adaptador real que alimenta as sugestões e reordenar a posição principal sem quebrar a associação entre cada palavra e sua ação ao toque.

## Compatibilidade e validação

Há patches públicos Morphe para builds anteriores 5.9.40.x, mas não há compatibilidade presumida com 5.9.50.10. Este projeto usa um fingerprint específico para o APK enviado.

## Estado desta análise

O projeto Morphe isolado foi criado na branch samsung-keyboard. Um hook experimental para acentuação foi escrito e o runtime passou em testes locais simples. O build GitHub Actions foi iniciado; o resultado ainda precisa ser conferido. O recurso de colagem e o reposicionamento de sugestões continuam em investigação. Nenhuma das três funções foi validada no Galaxy A15 nesta etapa.
