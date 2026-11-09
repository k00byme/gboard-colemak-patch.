# Gboard QWERTZ: layout personalizável

Patch Morphe para o Gboard **18.5.4.993608717-release-arm64-v8a**.

## Personalização

Antes de aplicar o patch, abra as opções dele no Morphe e edite as três fileiras:

| Campo | Quantidade | Padrão |
|---|---:|---|
| Top row | 10 letras | `qwertzuiop` |
| Middle row | 9 letras | `asdfghjkl` |
| Bottom row | 7 letras | `yxcvbnm` |

Digite as letras em minúsculo ou maiúsculo, sem espaços. As três fileiras precisam conter todas as letras de **a a z exatamente uma vez**. O patch mantém as dimensões e os espaçadores do QWERTZ original e atualiza os mapeamentos normal, maiúsculo e dividido.

Exemplo de fileira superior personalizada: `qwfpgjluyx`. As outras duas fileiras devem ser ajustadas para acomodar as letras restantes, sem repetições.

## Compatibilidade

- Pacote: `com.google.android.inputmethod.latin`
- Versão: `18.5.4.993608717-release-arm64-v8a`
- Arquitetura: `arm64-v8a`

O patch altera somente o recurso de mapeamento do QWERTZ. QWERTY, Colemak e os outros layouts permanecem inalterados.

## Compilação

Cada push para `main` inicia o GitHub Actions. O arquivo `.mpp` compilado é publicado como artefato chamado **gboard-colemak-patch**, disponível na execução do workflow por 14 dias.
