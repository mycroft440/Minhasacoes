# Minhas Ações

App Android que mostra se cada ação da sua carteira está **acima ou abaixo de uma média móvel simples (MMS)** do período que você escolher, e avisa por notificação quando uma ação acabou de ficar abaixo da média.

## Baixar

O APK é gerado automaticamente a cada commit na `main`:

**[Baixar MinhasAcoes.apk](https://github.com/mycroft440/Minhasacoes/releases/download/latest/MinhasAcoes.apk)**

Novas versões instalam por cima da anterior (mesma assinatura), sem perder suas carteiras.

## O que faz

- **Carteiras**: crie quantas quiser, cada uma com uma média padrão (ex.: MM20 ou MME21).
- **Resumo da carteira**: variação do dia (média simples da variação de todas as ações) e quantas estão acima/abaixo da média.
- **Ações**: logo da empresa, preço, variação do dia, mini gráfico dos últimos 60 pregões, valor da média e distância até ela. Lista ordenada com as ações em alta primeiro, depois as abaixo da média.
- **Rentabilidade mensal**: variação de cada mês (jan, fev, mar…) por ação e da carteira (média das ações), com o acumulado do ano e navegação entre anos.
- **Médias**: simples (MMS) ou exponencial (MME), período de 2 a 500 pregões por ação, e modo **cruzamento de médias** (ex.: MM9 x MM21).
- **Busca com autocompletar**: digite o ticker ou o nome da empresa.
- **Alertas**: notificação quando a ação cruza para baixo da média. Opcionalmente, também quando volta para cima.
- **Atualização automática** de hora em hora durante o pregão (B3 e/ou EUA, conforme as ações) e uma vez após o fechamento; fora disso não consome internet.
- **Widget** na tela inicial com o resumo das carteiras.
- Tema claro e escuro automáticos.

## Dados

Cotações do **Yahoo Finance** pelo endpoint público `v8/finance/chart` — gratuito, sem cadastro nem chave de API, com histórico diário completo (necessário para médias longas como MM200). Ações da B3 são digitadas normalmente (`PETR4`, `VALE3`, `BOVA11`); o app adiciona o sufixo `.SA`. Ativos dos EUA funcionam direto (`AAPL`, `MSFT`).

Se o Yahoo falhar, ações da B3 são buscadas na **brapi.dev** como reserva. Sem token só funcionam PETR4, VALE3, ITUB4 e MGLU3; com o token gratuito (configurável no app), qualquer ação com até 3 meses de histórico.

As médias usam os fechamentos diários, com o preço atual como último valor durante o pregão.

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · OkHttp · Coil (logos: brapi.dev para B3, Financial Modeling Prep para EUA). `minSdk 26` (Android 8.0).

## Compilar e testar

```bash
./gradlew testReleaseUnitTest assembleRelease
```

O workflow `.github/workflows/build.yml` roda os testes unitários (médias, cruzamentos, horário de pregão e leitura das respostas das APIs), compila e publica o APK na release `latest`.
