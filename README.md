# Gboard Colemak: remover `;` e preencher a fileira

Patch Morphe para o Gboard **18.5.4.993608717-release-arm64-v8a**.

## Resultado

- Mantém a ordem Colemak da fileira superior: **Q W F P G J L U Y**.
- Remove o ponto e vírgula (`;`) da fileira principal.
- Redistribui a largura que era ocupada pela tecla removida igualmente entre as nove letras, preenchendo a fileira sem deixar um espaço vazio.
- Mantém o espaçador central original entre os grupos de teclas.
- Preserva as outras fileiras do Colemak e os demais layouts.
- Não altera os recursos da tela de símbolos (`?123`), então o ponto e vírgula continua disponível nela.

O patch cria cópias privadas dos recursos usados pelo Colemak. Assim, não precisa alterar os recursos compartilhados com QWERTY ou outros layouts. O recurso de restrições do aplicativo no manifesto também permanece intacto.

## Compatibilidade

- Pacote: `com.google.android.inputmethod.latin`
- Versão: `18.5.4.993608717-release-arm64-v8a`
- Arquitetura do APK enviado: `arm64-v8a`

O patch verifica os elementos XML esperados e interrompe a aplicação caso a estrutura do Gboard seja diferente.

## Compilação

Cada push para `main` inicia o GitHub Actions. O arquivo `.mpp` compilado é publicado como artefato chamado **gboard-colemak-patch**, disponível na execução do workflow por 14 dias. Também é possível iniciar uma compilação manual na aba **Actions**.
