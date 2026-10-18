# Alterações

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
