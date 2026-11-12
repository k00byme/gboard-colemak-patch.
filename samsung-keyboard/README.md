# Samsung Keyboard — Morphe patches

Projeto separado do Gboard Cafe para modificar o Samsung Keyboard.

- Pacote-alvo: com.samsung.android.honeyboard
- APK de referência: 5.9.50.10 — versionCode 595010300
- Android mínimo declarado no APK: API 33
- Branch de desenvolvimento: samsung-keyboard

## Funcionalidades solicitadas

| Recurso | Comportamento desejado | Estado |
|---|---|---|
| Colar último conteúdo | Botão na barra superior que cola diretamente o clipboard, sem abrir o painel | Pendente de localizar o callback da barra |
| Acentuação PT-BR | Corrigir apenas acentos ausentes, sem autocorreção ortográfica geral | Patch experimental implementado e empacotado; teste no aparelho pendente |
| Sugestão principal central | Mover a sugestão mais relevante para o centro da fileira | Pendente de localizar a lista/ordenação visual |

## Acentuação experimental

O patch foi criado para a versão exata 5.9.50.10. Ele intercepta o método de commit de texto do wrapper de InputConnection e chama um normalizador conservador no runtime.

O léxico inicial inclui palavras como nao → não, voce → você, tambem → também, cafe → café e facil → fácil. Grafias desconhecidas, como katorze, são preservadas. Formas ambíguas como esta/está, tem/têm, pais/país e e/é ficam inalteradas.

O helper foi compilado localmente com javac e passou em testes unitários simples com um wrapper simulado. O bundle Morphe foi compilado com sucesso pelo GitHub Actions (run 43253299894). Isso valida a compilação e o empacotamento, não a aplicação do patch ao APK nem o comportamento no teclado real.

O patch permanece desativado por padrão até passar pelo teste funcional no Galaxy A15.

## Build

Workflow: .github/workflows/build-samsung-keyboard.yml

Último build confirmado: GitHub Actions run 43253299894 — sucesso.
Artifact: samsung-keyboard-morphe-patches (patches.mpp).

## Compatibilidade

A única versão-alvo declarada neste momento é 5.9.50.10. Não assumir que os offsets ou fingerprints de builds Samsung 5.9.40.x funcionam nesta versão.

O APK original enviado para análise não é armazenado no repositório.

## Critério de conclusão

Cada recurso precisa ter fingerprint específico, build .mpp bem-sucedido e validação funcional no Galaxy A15 antes de ser considerado concluído.
