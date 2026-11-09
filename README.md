# Gboard: layout alfabético personalizável em todos os layouts latinos

Patch Morphe para Gboard **18.5.4.993608717-release-arm64-v8a**.

## Onde configurar

Depois de aplicar o patch, abra **Configurações do Gboard**. Uma categoria **Teclado personalizado** aparecerá com a entrada **Personalizar layout**.

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

O patch tenta modificar qualquer mapeamento alfabético latino que contenha as 26 teclas e os mesmos IDs de posição do Gboard. Ele não exige mais que o layout original seja QWERTZ. Layouts não latinos e painéis de símbolos/números ficam fora do escopo.

## Compilação

O GitHub Actions restaura o código-fonte nativo, compila a tela e o runtime para DEX, incorpora a extensão ao patch Morphe e publica o `.mpp` como artefato.
