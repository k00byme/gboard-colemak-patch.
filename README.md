# Gboard QWERTZ: layout personalizável nas configurações do teclado

Patch Morphe para Gboard **18.5.4.993608717-release-arm64-v8a**.

## Onde configurar

Depois de aplicar o patch, abra **Configurações do Gboard**. Uma categoria **Teclado personalizado** aparecerá com a entrada **Personalizar QWERTZ**.

Edite as três fileiras:

| Fileira | Letras | Padrão |
|---|---:|---|
| Superior | 10 | `qwertzuiop` |
| Do meio | 9 | `asdfghjkl` |
| Inferior | 7 | `yxcvbnm` |

As três sequências juntas precisam conter cada letra de `a` a `z` exatamente uma vez. Toque em **Aplicar e reiniciar Gboard** depois de editar as fileiras.

## Diagnóstico

A tela do editor mostra **Diagnóstico do layout**. Abra um campo de texto para o Gboard montar o teclado e, em seguida, volte às configurações do editor. O diagnóstico informa se o runtime realmente encontrou e remapeou os grupos de teclas, ou por que não conseguiu.

## Compatibilidade

- Pacote: `com.google.android.inputmethod.latin`
- Versão: `18.5.4.993608717-release-arm64-v8a`
- Arquitetura: `arm64-v8a`

O patch reconhece e modifica apenas o mapeamento QWERTZ original. Outros layouts, como QWERTY, não devem ser alterados.

## Compilação

O GitHub Actions restaura o código-fonte nativo, compila a tela e o runtime para DEX, incorpora a extensão ao patch Morphe e publica o `.mpp` como artefato.
