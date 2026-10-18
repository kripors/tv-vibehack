# Alterações

## TV Home Lite Glass — 2026-10-18

- Comparado com `upstream/main` (Gathin23/tv-vibehack); restaurados GlassView, DockDecoration, Outfit e os recursos originais de cartões e menus.
- Recuperados dock inferior, grade de cartões 180 × 101 dp, título do favorito, brilho ambiente, painéis translúcidos e gradientes.
- Restauradas transições de escala e destaque de vidro somente ao mudar foco; sem loops ou dependência de molas. Rolagem alinha o vidro sem iniciar novas animações.
- Preservados cache LRU compartilhado de 2 MiB, uma thread de carregamento sob demanda, cancelamento na reciclagem e imagens até 96 × 96 pixels. Banners originais são rasterizados em 96 × 54 e usam o mesmo cache, sem Drawables retidos na lista.
- Preservadas atualização por eventos de pacotes, ausência de recarga incondicional no onResume, prefetch desativado, pool reduzido, R8 e redução de recursos.
- Mantidos HDMI, botão de TV aberta e configurações, Android 11 (minSdk 26/targetSdk 29) e workflow GitHub Actions sem alterações. ClockDream não foi restaurado.
- Verificação estática de XML e referências locais; compilação/lint bloqueados por ausência de Java executável e SDK Android no ambiente. Nenhum comando ADB, instalação ou acesso à TV.

## TV Home Lite — 2026-10-18

- Reaproveitados MainActivity.java e AppAdapter.java com Java/XML e RecyclerView/GridLayoutManager.
- Removidos GlassView, DockDecoration, ClockDream, layout do protetor, animação de entrada e fonte embarcada.
- Substituídos brilho ambiente, molas, escalas e gradientes por fundo preto e bordas de foco estáticas.
- Eliminados banners dos apps e ícones mantidos na lista. Adicionado carregamento assíncrono por célula, cancelamento ao reciclar, rasterização até 96 px e LRU de 2 MiB.
- Desativados prefetch e item animator; reduzidos cache de views e pool reciclado.
- Eliminada reconstrução incondicional em onResume. Adicionado receiver de pacotes durante a Activity, com agrupamento de eventos e restauração de foco por componente.
- Preservados controle remoto, entradas HDMI e configurações. Adicionado botão permanente de TV aberta com alternativas de abertura.
- Removidos monitoramento de rede, consultas de SSID/localização, permissões relacionadas e dependência de animação dinâmica.
- Habilitados minificação R8 e redução de recursos em release.
- Adicionados rótulos pt-BR e instruções de compilação sem instalação.
- Nenhuma alteração ou instalação no dispositivo. Build e medições no aparelho permanecem pendentes por ausência de toolchain neste ambiente.
