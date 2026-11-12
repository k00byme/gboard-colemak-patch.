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
| Acentuação PT-BR | Corrigir apenas acentos ausentes, sem autocorreção ortográfica geral | Hook experimental implementado; build e teste no aparelho pendentes |
| Sugestão principal central | Mover a sugestão mais relevante para o centro da fileira | Pendente de localizar a lista/ordenação visual |

## Acentuação experimental

O primeiro patch foi criado para a versão exata 5.9.50.10. Ele intercepta o método de commit de texto do wrapper de InputConnection e chama um normalizador conservador no runtime.

O léxico inicial inclui palavras como nao → não, voce → você, tambem → também, cafe → café e facil → fácil. Grafias desconhecidas, como katorze, são preservadas. Formas ambíguas como esta/está, tem/têm, pais/país e e/é ficam inalteradas.

O patch fica **desativado por padrão** até passar pelo build Morphe e por testes funcionais no Galaxy A15. O runtime foi compilado localmente com javac e passou por testes unitários simples usando um wrapper simulado; isso não substitui o teste dentro do teclado.

## Build e validação

O workflow de GitHub Actions está em:
.github/workflows/build-samsung-keyboard.yml

O workflow deve produzir um arquivo .mpp quando o build terminar com sucesso. Ainda não há confirmação de build concluído nem de funcionamento no aparelho.

O APK original não é armazenado neste repositório.

## Compatibilidade

A única versão-alvo declarada neste momento é 5.9.50.10. Não assumir que os offsets ou fingerprints de builds Samsung 5.9.40.x funcionam nesta versão.

Cada recurso precisa de fingerprint específico, build .mpp bem-sucedido e validação funcional no Galaxy A15 antes de ser considerado concluído.
