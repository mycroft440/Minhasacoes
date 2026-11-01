# Minhas Ações

App Android que mostra se cada ação da sua carteira está **acima ou abaixo de uma média móvel simples (MMS)** do período que você escolher, e avisa por notificação quando uma ação acabou de ficar abaixo da média.

## Baixar

O APK é gerado automaticamente a cada commit na `main`:

**[Baixar MinhasAcoes.apk](https://github.com/mycroft440/Minhasacoes/releases/download/latest/MinhasAcoes.apk)**

Novas versões instalam por cima da anterior (mesma assinatura), sem perder suas carteiras.

## O que faz

- **Carteiras**: crie quantas quiser, cada uma com uma média padrão (ex.: MM20).
- **Resumo da carteira**: variação do dia (média simples da variação de todas as ações) e quantas estão acima/abaixo da média.
- **Ações**: preço, variação do dia, valor da média e distância até ela, com indicação de alta/baixa.
- **Período por ação**: cada ação pode ter seu próprio período (2 a 500 pregões).
- **Alertas**: notificação quando a ação cruza para baixo da média. Opcionalmente, também quando volta para cima.
- **Atualização automática de hora em hora** (WorkManager), além de puxar para atualizar.
- Tema claro e escuro automáticos.

## Dados

Cotações do **Yahoo Finance** pelo endpoint público `v8/finance/chart` — gratuito, sem cadastro nem chave de API, com histórico diário completo (necessário para médias longas como MM200). Ações da B3 são digitadas normalmente (`PETR4`, `VALE3`, `BOVA11`); o app adiciona o sufixo `.SA`. Ativos dos EUA funcionam direto (`AAPL`, `MSFT`).

A MMS é calculada com os fechamentos diários, usando o preço atual como último valor durante o pregão.

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · OkHttp. `minSdk 26` (Android 8.0).

## Compilar

```bash
./gradlew assembleRelease
```

O workflow `.github/workflows/build.yml` compila e publica o APK na release `latest`.
