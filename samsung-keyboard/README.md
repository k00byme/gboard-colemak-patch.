# Samsung Keyboard — Morphe patches

Projeto separado do Gboard Cafe para modificar o Samsung Keyboard.

- Pacote-alvo: com.samsung.android.honeyboard
- APK de referência: 5.9.50.10 — versionCode 595010300
- Android mínimo declarado no nome do pacote: API 33
- Branch de desenvolvimento: samsung-keyboard

## Funcionalidades solicitadas

| Recurso | Comportamento desejado | Estado |
|---|---|---|
| Colar último conteúdo | Novo botão na barra superior que insere diretamente o texto do clipboard, sem abrir a tela da área de transferência | Em análise |
| Acentuação PT-BR | Corrigir somente acentos ausentes; não corrigir grafia, trocar palavras ou reescrever frases | Em análise |
| Sugestão principal central | Exibir a sugestão de maior relevância na posição central da fileira de três sugestões | Em análise |

## Situação técnica

A análise inicial dos três DEXs e dos recursos do APK começou. Os pontos de entrada encontrados até agora estão registrados em análise do APK 5.9.50.10 (docs/apk-analysis-5.9.50.10.md).

**Ainda não há um bundle .mpp funcional nesta branch.** Os pontos de entrada de cada recurso precisam ser confirmados antes de escrever hooks de bytecode. Isso evita publicar patches que compilam, mas alteram a função errada ou quebram o teclado.

O APK enviado para análise não é armazenado neste repositório.

## Compatibilidade

A compatibilidade está limitada ao APK de referência até que outros builds sejam analisados. Não assumir que os patches existentes para Samsung Keyboard 5.9.40.x funcionam em 5.9.50.10.

## Critério de conclusão

Cada recurso deverá ter:
1. fingerprint específico para a versão suportada;
2. patch Morphe e implementação;
3. build .mpp bem-sucedido;
4. validação no Galaxy A15 com o teclado real.

Build bem-sucedido, sozinho, não equivale a teste funcional no aparelho.
