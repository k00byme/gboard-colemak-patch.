# Gboard Cafe

Projeto de patches Morphe para recriar no Gboard recursos visuais e de edição inspirados no Samsung Keys Cafe.

## Patches no único bundle .mpp

1. **Gboard Cafe Core** — insere o runtime e as Activities do projeto.
2. **Settings Integration** — adiciona Gboard Cafe às configurações do Gboard.
3. **Key Effects** — efeitos na tecla pressionada.
4. **Board Effects** — efeitos sobre a área inteira do teclado.
5. **Key Layout Editor** — captura e reordena as teclas existentes dentro da linha, com ocultar/restaurar.
6. **Diagnostics** — página opcional de diagnóstico acessível nas configurações do Gboard.
7. **Gboard structure discovery** — patch opcional de inspeção, desativado por padrão.

Os recursos ficam em patches selecionáveis separados, mas são compilados juntos no mesmo arquivo `.mpp`. Os patches de recurso dependem apenas do Core e da integração de configurações que precisam para funcionar.

## Correção crítica — colisão de classes

O crash anterior foi identificado a partir do stack trace e da comparação de DEX:

- O DEX da extensão continha classes sintéticas de listeners renomeadas pelo R8 para nomes globais como `La;`, `Lb;` e `Lc;`.
- O DEX original do Gboard já continha uma classe `Lc;` em `classes2.dex`.
- A classe `Lc;` do Gboard tinha um construtor `(Lay;)`; a classe sintética do Cafe exigia `(GboardCafeSettingsActivity; I)`.
- Ao mesclar a extensão no APK host, a referência ao descritor global `Lc;` podia resolver para a classe original do Gboard, gerando `NoSuchMethodError`.

A correção adiciona `-dontobfuscate` e regras keep específicas à extensão, preservando o namespace `app.gboard.cafe.runtime` também para classes sintéticas. O critério de aceitação para essa correção é inspecionar o DEX do novo `.mpp` e confirmar que não há classes sintéticas da extensão com descritores globais de uma letra.

## Alvo inicial

- Pacote: `com.google.android.inputmethod.latin`
- Versão: `18.5.4.993608717-release-arm64-v8a`
- Arquivo de referência fornecido: XAPK arm64-v8a
- Compatibilidade: somente a versão acima até nova análise

## Recursos

### Efeitos por tecla
Ripple, flash de cor, contorno, bounce, partículas, tinta, blob, blob expansivo e pegadas.

### Efeitos no teclado inteiro
Anéis expansivos, faíscas, faíscas giratórias, fogos, blob, blob expansivo, onda de contorno e paint gun.

### Editor de layout
Reordena teclas existentes dentro da mesma linha, oculta/mostra teclas e restaura o layout original. Criar teclas novas e mover teclas entre linhas ainda não está implementado.

## Build e validação

GitHub Actions compila o único bundle `.mpp`. Build bem-sucedido prova a compilação; **não** prova que o Morphe aplicou o patch, que o APK instala ou que o runtime funciona. Antes de publicar uma versão validada, é necessário:
- inspecionar o DEX final da extensão e confirmar ausência de classes de uma letra fora do namespace;
- aplicar os patches no Gboard 18.5.4;
- instalar, abrir as configurações, testar ambos os renderizadores e salvar/reabrir um layout num dispositivo real.

## Licença e marcas

Projeto independente, sem afiliação com Samsung ou Google. Não redistribui os APKs fornecidos.
