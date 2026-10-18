# TV Home Lite Glass

Launcher nativo Java/XML para Android TV (Android 8 ou superior), voltado a aparelhos com pouca RAM. Esta árvore contém a versão leve; não há flavor separado. MainActivity e AppAdapter foram reaproveitados.

## Compilação local, sem instalar

Pré-requisitos: JDK 17, Android SDK Platform 34, Build Tools 34.0.0 e acesso aos repositórios Gradle/Google/Maven na primeira compilação. O wrapper usa Gradle 8.9 e o projeto usa Android Gradle Plugin 8.5.2.

Configure `JAVA_HOME` e `ANDROID_HOME` para instalações existentes, ou indique `sdk.dir=/caminho/do/sdk` em `launcher/local.properties` (arquivo local, não versionado). A partir de `launcher/`:

```sh
./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' :app:assembleDebug
./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' :app:assembleRelease :app:lintRelease
```

Saídas: `app/build/outputs/apk/debug/app-debug.apk` e `app/build/outputs/apk/release/app-release-unsigned.apk`. Release usa R8 e redução de recursos. Para distribuir o release, falta configurar uma chave de assinatura ou assinar o APK com `apksigner`; não armazene chaves no repositório.

Em Debian ARM64/PRoot, o AAPT2 fornecido pelo Maven pode ser incompatível com a arquitetura. Nesse caso é necessário um AAPT2 compatível com ARM64 e com o plugin, informado por `-Pandroid.aapt2FromMavenOverride=/caminho/absoluto/aapt2`, ou compilar em Linux x86_64. Os 768 MiB de heap são uma sugestão para o build e podem exceder a memória disponível junto ao Android; prefira compilar em outra máquina se necessário.

Não execute `build-and-install.sh`, scripts de instalação, comandos ADB ou tarefas de instalação para esta fase. Os comandos acima somente compilam/verificam. O manifesto mantém HOME e oferece também entrada na lista de apps de TV; não torna o app launcher padrão por conta própria. O applicationId original foi preservado.

## Compilação manual pelo GitHub Actions

O workflow [Build TV Home Lite APK](../.github/workflows/build-apk.yml) usa Ubuntu 24.04 x86-64, JDK 17 (Temurin), Android SDK Platform 34, Build Tools 34.0.0 e Gradle Wrapper 8.9. O cache do Gradle reutiliza dependências e distribuições do wrapper entre execuções; alterações nos arquivos Gradle invalidam a chave do cache.

1. Revise e envie manualmente ao seu repositório GitHub o workflow e os arquivos do projeto que deseja compilar. Para aparecer o botão de execução manual, o workflow precisa existir na branch padrão. Nenhum envio é feito automaticamente por esta configuração.
2. No GitHub, abra **Actions → Build TV Home Lite APK → Run workflow**. Se necessário, habilite Actions no repositório.
3. Selecione a branch com os arquivos desejados e clique em **Run workflow**.
4. Após a execução terminar com sucesso, abra a execução e, em **Artifacts**, baixe **tv-home-lite-debug**. Extraia o ZIP para obter `app-debug.apk`. É necessário estar conectado ao GitHub e ter acesso ao repositório. O artifact fica disponível por 14 dias, sujeito às políticas do repositório.

O único gatilho é `workflow_dispatch`: pushes e pull requests não iniciam builds. O comando `bash ./gradlew --no-daemon --stacktrace assembleDebug` roda em `launcher/`, usando o wrapper versionado. O upload usa o caminho relativo à raiz Git `launcher/app/build/outputs/apk/debug/app-debug.apk` e falha se o APK estiver ausente. O APK usa a assinatura debug gerada no runner; não é uma versão release.

O workflow prepara as ferramentas apenas no runner do GitHub. Não chama os scripts de instalação do projeto, não usa ADB, não acessa nem modifica a TV, não faz push e não cria releases. A compilação efetiva e o download devem ser confirmados na primeira execução no GitHub.

## Comportamento e configuração

- Visual original com Outfit, dock inferior, painéis translúcidos, gradientes, banners e GlassView. Transições breves ao mudar foco, sem animações contínuas e sem ClockDream.
- RecyclerView/GridLayoutManager com quatro colunas e pool reduzido. Setas navegam, OK abre e Voltar permanece na Home. Entradas e Configurações ficam no topo.
- Ícones e banners carregados em uma única thread, só quando vinculados a uma célula, rasterizados em até 96 × 96 pixels. Banners rasterizados em 96 × 54 pixels. Cache LRU compartilhado de 2 MiB, além dos ícones dos holders visíveis e temporários do carregamento. O carregamento original do PackageManager pode alocar uma imagem maior temporariamente. O cache é liberado em avisos de memória e no encerramento da Activity.
- Lista consultada ao criar a Activity e quando chegam eventos de adição, remoção ou alteração de pacotes. Retornar de outro app não reconstrói a lista. A Activity recriada pelo sistema precisa consultar novamente. O receiver existe somente durante a Activity; não há serviço.
- `app/src/main/res/values/config.xml`: ordem de favoritos, pacotes ocultos, colunas e pacote da TV aberta. Favoritos ausentes são ignorados.
- TV aberta tenta o pacote configurado (Leanback ou launcher comum), a URI pública de canais e a ação de seleção de entrada do fabricante. O botão é sempre exibido; a disponibilidade depende do firmware. HDMI usa TvInputManager e as URIs públicas de passagem, sem privilégios de sistema.
- Sem consultas de SSID/localização, permissões de rede/localização, ClockDream, WebView, vídeos ou serviços em segundo plano.

## Validação pendente

Neste ambiente não foram encontrados Java nem SDK Android; não foi gerado APK nem executado lint Android. XML, referências locais e whitespace foram verificados estaticamente. Ainda faltam compilação debug/release, lint e validação posterior autorizada no AIWA/KTC: foco ao rolar/retornar, instalação/remoção de apps, abertura de HDMI/TV e medição de RAM. O limite de cache não representa o consumo total do processo, e não há medição de economia real nesta etapa.
