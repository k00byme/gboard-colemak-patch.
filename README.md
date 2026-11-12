# Gboard Cafe

Projeto de patches Morphe para investigar e reconstruir no Gboard recursos inspirados no Samsung Keys Cafe.

**Prioridades do projeto**
1. Efeitos visuais e animações nas teclas.
2. Editor de layout para mover, adicionar e remover teclas.
3. Entrada e telas de configuração acessíveis dentro das configurações do Gboard.

O restante dos recursos do Keys Cafe é secundário e só será considerado depois dessas duas prioridades.

## Estado atual

Este repositório foi reiniciado para substituir o protótipo anterior de layout QWERTZ/Colemak. Os APKs fornecidos já foram inspecionados. O repositório agora contém os alvos reais de recursos/classes identificados, um modelo de layout editável com validações e um catálogo inicial de efeitos. Ainda não há integração runtime dos efeitos nem do editor no Gboard. Não instale um artefato deste estágio esperando essas funções.

## Alvo inicial

- Aplicativo: Gboard
- Pacote: `com.google.android.inputmethod.latin`
- Versão de referência: `18.5.4.993608717-release-arm64-v8a`
- Arquivo fornecido: XAPK arm64-v8a

## Estratégia

- **Portar quando possível:** estudar as implementações e os recursos do Keys Cafe e reaproveitar apenas componentes tecnicamente independentes dos serviços privados do Samsung Honeyboard.
- **Recriar quando necessário:** implementar o comportamento equivalente para as classes e o ciclo de desenho do Gboard, sem depender do framework de teclado Samsung.
- **Configurações nativas:** integrar a interface às telas de configurações do Gboard. Uma Activity externa isolada não conta como conclusão.
- **Falhar com segurança:** fingerprints e hooks precisam validar a versão e a estrutura real do APK antes de modificar bytecode.

## Estrutura

- `patches/`: código Kotlin dos patches Morphe e modelos de efeitos/layout.
- `.github/workflows/build.yml`: compilação automática com JDK 17.
- `docs/REVERSE_ENGINEERING.md`: achados e plano de análise.
- `docs/IMPLEMENTATION_PLAN.md`: etapas e critérios de aceite.

## Compilar

Requisitos: JDK 17, Android SDK (API 35 / Build Tools 35) e credencial GitHub com acesso de leitura aos GitHub Packages do Morphe.

```bash
./gradlew :patches:buildAndroid
```

O workflow do GitHub Actions compila o bundle `.mpp` e publica-o como artefato de workflow. O primeiro build deve ser tratado como verificação da infraestrutura, não como entrega funcional.

## Aviso de compatibilidade

O Gboard é proprietário e suas classes internas podem mudar entre versões. A versão acima é o único alvo inicial. Qualquer atualização exige nova análise de fingerprints, recursos e comportamento em dispositivo.

## Créditos e licenças

Keys Cafe e Gboard são marcas e aplicativos de seus respectivos titulares. Este projeto é independente e não é afiliado à Samsung ou ao Google. Não inclui nem redistribui os APKs enviados para análise.
