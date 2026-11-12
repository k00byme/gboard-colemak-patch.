# Gboard Cafe

Pacote de patches Morphe que recria no Gboard recursos visuais e de edição de teclas inspirados no Samsung Keys Cafe.

## Prioridades

1. Efeitos visuais por tecla e efeitos sobre a área inteira do teclado.
2. Reorganização das teclas existentes.
3. Entrada e controles dentro das configurações do próprio Gboard.

Os demais recursos do Keys Cafe ficam fora do escopo inicial.

## Estado da implementação

O projeto contém:

- Efeitos originais desenhados em runtime na camada visual das teclas: ripple, flash de cor, contorno, bounce, partículas, tinta, blob, blob expansivo e pegadas.
- Efeitos originais na camada visual do teclado: anéis, faíscas, faíscas giratórias, fogos, blob, blob expansivo, onda de contorno e paint gun.
- Controles para ativar os efeitos, escolher os dois tipos de efeito, cor e duração.
- Uma entrada Gboard Cafe no recurso de preferências do Gboard, apontando para uma Activity dentro do pacote do Gboard.
- Editor para reordenar as teclas dentro de cada linha, ocultar/mostrar teclas existentes e restaurar a ordem original.
- Persistência do layout e reaplicação após a reconstrução da árvore de Views, quando as identidades e linhas ainda correspondem.

**Importante:** a implementação compila na infraestrutura CI, mas ainda não foi aplicada ao APK e testada em um aparelho. O arquivo `.mpp` não deve ser tratado como uma versão validada para instalação até que o patch seja aplicado pelo Morphe e testado no dispositivo.

## Alvo inicial

- Aplicativo: Gboard
- Pacote: `com.google.android.inputmethod.latin`
- Versão de referência: `18.5.4.993608717-release-arm64-v8a`
- Arquivo analisado: XAPK arm64-v8a

## Estratégia técnica

- Reaproveitar apenas conceitos independentes dos serviços privados do Samsung Honeyboard.
- Recriar efeitos com desenho procedural original; nenhum bytecode ou recurso visual do APK Samsung é redistribuído.
- Inserir os hooks somente nos métodos verificados do build-alvo.
- Integrar os controles ao recurso de configurações do Gboard, sem depender de um aplicativo externo.
- Falhar com segurança se classe, método, parâmetros ou versão não corresponderem ao alvo.

## Limitações conhecidas

- O editor atual reorganiza teclas dentro da mesma linha. Mover teclas entre linhas, criar teclas novas e editar ações/long-press ainda não estão implementados.
- Variantes de idioma, páginas de símbolos, layouts de tablet/flutuante e acessibilidade precisam ser testados.
- A aparência e a resposta dos efeitos precisam de validação física, especialmente com multitouch.
- Somente a versão Gboard indicada é alvo deste patch.

## Estrutura

- `patches/`: patches Kotlin Morphe e modelos.
- `extensions/gboard-cafe/`: Activity de configurações e runtime Java.
- `.github/workflows/build.yml`: compilação automática e upload do bundle `.mpp`.
- `docs/REVERSE_ENGINEERING.md`: achados dos APKs.
- `docs/IMPLEMENTATION_PLAN.md`: etapas e critérios de validação.

## Compilar

Requisitos: JDK 17, Android SDK e credencial GitHub com leitura do GitHub Packages do Morphe.

```bash
./gradlew :patches:buildAndroid
```

O workflow do GitHub Actions publica o `.mpp` como artefato de workflow.

## Créditos e licenças

Keys Cafe e Gboard são marcas e aplicativos de seus respectivos titulares. Este projeto é independente e não é afiliado à Samsung ou ao Google. Não inclui nem redistribui os APKs enviados para análise.
