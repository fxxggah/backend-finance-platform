# Plano de implementação

Leia **no início de cada tarefa**: fase atual, dono e status. Uma feature por vez, na ordem abaixo. Não comece algo novo antes de fechar o que está em andamento.

**Legenda:** `[ ]` a fazer · `[~]` em andamento · `[x]` pronto (cumpre a Definição de pronto do `AGENTS.md`)

## Situação atual (16/11/2026)

- **Responsável:** Gabriel, autor e responsável pelo projeto inteiro. Frontend em outro repositório; o trabalho aqui é restrito ao backend.
- **V1:** funcional, conforme confirmação de Gabriel. Os checklists antigos estão desatualizados e foram preservados abaixo como histórico, sem declarar verificações individuais como realizadas nesta tarefa.
- **Fase atual:** V2 / fase 3 — PRO e cotas, em preparação desde 16/11/2026, responsável Gabriel. Fase 2 concluída em ambiente de teste em 14/11/2026. Rota de rascunho segue restrita a dev e desativada por padrão. As regras de concessão manual, contagem e limites da cota da fase 3 estão em alinhamento antes da implementação. Relatórios de captura ausentes permanecem limitação da fase 1; experiência completa nos navegadores será validada na fase 4.
- **Base técnica confirmada:** Java 25, Spring Boot 4.1.1, pacote `com.finance`.
- A distribuição antiga entre Biel, Breno e João e as decisões de divisão de trabalho abaixo são históricas; o responsável atual por todas as fases é Gabriel.

## V2 — voz para transação

### Decisões confirmadas

- Apenas áudio; uma transação por gravação. Parcelamento continua seguindo as regras existentes e pode gerar as parcelas da mesma compra.
- A IA extrai dados, sem tools, acesso de escrita ao banco ou criação automática de transação. O usuário revisa/edita e confirma no fluxo de criação existente.
- Voz exclusiva para PRO. Definição de limites do plano gratuito do FIN fica para depois.
- **Orçamento inicial de IA: R$ 0.** Começar com Gemini Flash-Lite no nível gratuito, sem habilitar cobrança ou fallback pago. Modelo exato e capacidade do projeto serão verificados na fase 1.
- Manter Java 25 e Spring Boot 4.1.1; nenhuma atualização de stack nesta entrega.
- Pagamento depois da validação da experiência; acesso PRO manual para testes antes da integração de cobrança.
- Parcelas ausentes em uma única transação: com conta válida, normalizar para 1 no backend; com cartão ou origem não resolvida, manter pendente. Decisão explícita de Gabriel em 13/11/2026; validar valores informados antes de aplicar defaults.
- A fase 0 foi exclusivamente documental. A fase 1 adiciona somente a prova manual e seus testes; endpoints públicos, persistência e assinatura ficam nas fases posteriores.

### Direção técnica proposta para validar na fase 1

- Preferir o SDK oficial `com.google.genai:google-genai` atrás da porta `TransactionExtractor`; decidir e registrar a versão antes de adicionar a dependência. Spring AI é alternativa se surgir benefício concreto.
- Enviar áudio diretamente ao modelo, com saída estruturada e rascunho sem persistência. Não guardar nem logar áudio, transcrição ou conteúdo financeiro; verificar também arquivos temporários do multipart.
- Contexto mínimo de categorias, contas e cartões ativos do próprio usuário, com referências temporárias mapeadas pelo backend. Sem histórico financeiro ou saldos.
- Validar toda saída como entrada não confiável: dinheiro em `BigDecimal`, datas via `Clock`, propriedade e estado dos recursos, tipo, XOR conta/cartão e parcelas. Revalidar tudo na criação efetiva.
- Campos ausentes ou ambíguos devem aparecer como pendências; não inventar conta/cartão/categoria. Detectar múltiplos lançamentos e pedir separação, sem descartar parte da gravação silenciosamente.
- Cota persistente por usuário e proteção global do projeto, com reserva atômica e tratamento de repetição/concorrência. Não manter transação de banco aberta durante a chamada externa.
- Cota do FIN usa `America/Sao_Paulo`; os limites e a janela do provedor são independentes. Ao esgotar capacidade, oferecer preenchimento manual, sem troca automática para serviço pago.
- Limite técnico confirmado por Gabriel: até 30 segundos por áudio, comprovados no backend. Hipótese de cota para avaliação, ainda não limite comercial: 10 solicitações por usuário/dia; ajustar ao limite real do projeto e aos resultados do piloto.
- Adiar `transactions.source`, schema de assinaturas e escolha do gateway. O contrato de rascunho, erros e extensão de `/users/me` será definido antes da implementação, em `docs/api-spec.md`.

### Fases da V2

Todas as fases têm **Gabriel** como responsável. Executar uma tarefa por vez.

| Fase | Entrega | Critério de conclusão | Status |
|---|---|---|---|
| 0 | Documentação e base | Decisões registradas, responsável/status corrigidos, compilação e testes verificados | [x] |
| 1 | Prova técnica gratuita | SDK/modelo/formatos validados com dados fictícios; precisão, latência e consumo medidos | [x] |
| 2 | Áudio para rascunho | Extração, validação e confirmação verificadas em ambiente de teste; sem escrita financeira pelo modelo | [x] |
| 3 | PRO e cotas | Acesso manual com validade, bloqueio de BASIC antes da chamada e cotas resistentes à concorrência | [~] |
| 4 | Integração e piloto | Fluxo completo nos navegadores, revisão obrigatória, fallback manual e controle de liberação | [ ] |
| 5 | Pagamento | Checkout hospedado e webhook com assinatura, idempotência e tratamento de eventos fora de ordem | [ ] |

O endpoint da fase 2 não deve ser exposto a usuários antes de concluir as proteções da fase 3. Mudanças de contrato devem ser documentadas e coordenadas com o frontend, em seu repositório.

### Checklist da fase 0

- [x] Confirmar V1 funcional e Gabriel como responsável; preservar a atualização existente de validação/publicação da V1.
- [x] Registrar Java 25 / Boot 4.1.1, uma transação por áudio e orçamento inicial zero.
- [x] Registrar ordem das fases, critérios de conclusão e decisões ainda pendentes.
- [x] Detalhar a prova técnica gratuita sem iniciar implementação ou habilitar cobrança.
- [x] `mvnw.cmd -U clean compile`: passou em 13/11/2026 com JDK 25.0.5.0.2.
- [x] `mvnw.cmd test`: passou em 13/11/2026 com JDK 25 e Docker/MySQL; **851 testes, zero falhas, zero erros e zero ignorados**.
- [x] `git diff --check`: sem erros de whitespace.

Definição de pronto nesta fase: documentação atualizada e verificação da base existente. Novos testes de negócio/isolamento, migrations e alterações de Swagger não se aplicam à mudança documental; serão obrigatórios nas fases que alterarem esses comportamentos.

Para reproduzir a validação no Windows, usar JDK 25 em `JAVA_HOME` e Docker ativo para os testes MySQL/Testcontainers. Nesta máquina, `java -version` apontava para 25, mas o Maven inicialmente selecionou outro JDK e falhou com `release version 25 not supported`; definir `JAVA_HOME` apenas no processo resolveu. O Docker também precisou ser iniciado. Nenhuma alteração no wrapper ou no `pom.xml` foi necessária.

### Fase 1 — roteiro da prova técnica gratuita

Preparação iniciada em 13/11/2026:

- [x] Gabriel confirmou projeto `fin-app` no nível gratuito pelo AI Studio.
- [x] Limites informados para Gemini 3.1 Flash Lite: **15 RPM, 250.000 TPM e 500 RPD**, compartilhados pelo projeto. Gemini 3.5 Flash Lite aparece com os mesmos limites; 2.5 Flash Lite com 10 RPM, 250.000 TPM e 20 RPD.
- [x] Modelo inicial escolhido: `gemini-3.1-flash-lite`; áudio WAV e saída estruturada validados com SDK 1.79.0. WebM/Opus e MP4/AAC também foram testados nas etapas seguintes, registradas abaixo.
- [x] `.env` está ignorado pelo Git (checagem de caminho, sem ler o conteúdo).
- [x] Disponibilizar `GEMINI_API_KEY` no processo que executará a prova. Presença confirmada sem imprimir o valor e sem ler `.env`.
- [x] Preparar e executar a prova optativa com SDK oficial `google-genai:1.79.0`, em escopo `test`, sem chamadas externas nos testes normais.
- [x] Preparar captura no navegador com download/compartilhamento dos bytes originais e relatório, disponível em localhost e HTTPS temporário para o iPhone 13 de Gabriel.
- [x] Validar gravação/links/liberação do microfone em Chrome isolado com dispositivo fictício, nos formatos WebM/Opus e MP4/AAC; servidor estático com rotas fixas e sem upload.
- [x] Validar extração dos dois arquivos originais fornecidos por Gabriel: WebM/Opus e MP4/AAC, com a frase fictícia da página. Ambos corresponderam integralmente ao esperado. Relatórios de captura/navegador ainda não recebidos.
- [x] Montar corpus sintético de 50 casos com resultados esperados, medir campos/latência/uso e registrar comparação de prompts v1/v2 sem descartar erros HTTP.
- [x] Implementar classificação local por contagem de eventos e verificar os 50 casos originais, os 20 adicionais e 10 frases inéditas antes da primeira chamada v4. Todos os 16 múltiplos com resposta foram recusados; 2 dos 18 múltiplos planejados ficaram sem resposta por HTTP 503.
- [x] Verificar HTTP 429, timeout e limite de falhas em servidor local, sem chave real ou chamadas ao Google.
- [x] Alinhar ausência de parcelas com Gabriel e implementar a normalização no backend da PoC, com testes locais e política versionada, sem reescrever os resultados históricos.
- [x] Validar no backend da PoC a duração de WebM/Opus e MP4/AAC, impondo até 30 s por decodificação local, sem confiar só no cabeçalho; testar fronteira, duração adulterada e truncamento. Arquivos originais preservados e aceitos localmente.
- [x] Corrigir a captura para compartilhar áudio e relatório JSON juntos quando suportado, identificando o nome do áudio no relatório e mantendo os dois links como alternativa.
- [x] Encerrar a prova com as evidências existentes, por decisão explícita de Gabriel em 14/11/2026. Relatórios JSON de captura não recebidos; sua ausência é limitação registrada, não bloqueio da fase 1. Não foram inventados relatórios nem comprovada a origem dos arquivos por decodificação. Experiência completa no Chrome/Safari será verificada na fase 4.

Não houve leitura do `.env`. O controle do faturamento continua manual no projeto do Google; o nome do modelo e a chave não garantem custo zero por si só.

### Resultado inicial da prova (13/11/2026)

- Programa manual `com.finance.ai.poc.GeminiVoiceProbe`, com SDK somente no classpath de testes, sem Spring ou banco. Flags e conteúdo fictício documentados abaixo.
- **Smoke com texto fixo:** uma tentativa, HTTP 503; sem repetição automática nem troca de modelo. Não foi validado como sucesso.
- **Áudio sintético WAV PCM**, gerado offline com Microsoft Maria Desktop, sem dados reais: uma chamada bem-sucedida ao `gemini-3.1-flash-lite`, thinking `MINIMAL`, prompt `voice-poc-v1`.
- Frase fictícia: “Hoje gastei quarenta e cinco reais no mercado, na conta Carteira, categoria Alimentação, à vista.” Resultado comparado em memória com tipo `EXPENSE`, total `45.00`, data do dia em São Paulo, 1 parcela, `cat_food`, `acc_wallet` e nenhum cartão: **todos os campos esperados corretos**, JSON válido, sem campos ausentes.
- Latência observada: **4.343 ms**. Uso informado: **545 tokens de entrada, 89 de saída, 634 no total**. Contagem de thinking não informada; não assumir zero. Esses dados não são p50/p95 nem uma avaliação de precisão.
- **26 testes offline passaram**: dinheiro inválido/precisão, combinações/referências, pendências, múltiplos lançamentos, schema, Clock, configuração de timeout/tentativas/tools e WAV inválido/truncado/acima dos limites.
- **Validação da entrega:** `mvnw.cmd -U clean compile` passou com JDK 25. Regressão completa: **877 testes, zero falhas, zero erros e zero ignorados**, incluindo os 26 novos testes e MySQL/Testcontainers; `git diff --check` passou.
- **Limitação do ambiente:** a execução padrão de `mvnw.cmd test` foi afetada pelo desaparecimento de arquivos `.class` de `target` durante a suíte (95 erros de contexto); uma repetição de `clean test` também encontrou classes ausentes. Um servidor de linguagem Java da IDE estava ativo, mas a autoria da limpeza não foi comprovada. A regressão final passou com uma cópia temporária do mesmo POM, alterando somente a saída para `build/ai-poc-verification`, ignorada pelo Git. O POM temporário foi removido, sem mudar o diretório padrão no projeto. Evitar Build/Rebuild simultâneo durante comandos Maven; investigar separadamente se a interferência persistir.
- WAV aceito pela prova: PCM assinado, no máximo 30 s e 2 MiB, com contêiner e integridade verificados. Não há transcodificação nem validação de WebM/MP4 ainda. A prova não implementa validações completas da futura feature de rascunho.
- Nenhum conteúdo da resposta ou áudio foi logado; apenas métricas e resultado booleano da comparação. O arquivo sintético temporário ficou em `target/ai-poc`, ignorado pelo Git e removido por `clean`.
- **Pendente para concluir a fase:** validar gravações reais de teste do Chrome/Safari com dados fictícios, aproximadamente 50 casos e resultados esperados, métricas por campo/p50/p95, e cenários de indisponibilidade além do 503 observado. Não avançar para endpoint público nesta etapa.

### Validação local de duração (14/11/2026)

- `MediaDurationProbe` decodifica WebM/Opus e MP4/AAC com **ffprobe 9.0.2**, antes do envio ao Gemini. Limites: duração positiva até 30 s inclusive, 2 MiB, uma única trilha de áudio, 5 s por processo e saídas limitadas. Sem nova dependência Maven, transcodificação, cópia temporária do áudio ou chamada ao Google nesta tarefa.
- Os dois arquivos originais fornecidos por Gabriel foram aceitos: WebM **10,74 s** e MP4 **9,769333334 s**. O MP4 do Safari declarava apenas **0,513333 s** no metadado do formato; por isso a validação também usa amostras e timestamps decodificados.
- Fixtures sintéticas locais: MP4 de 29,9 s e 30 s aceitos; 30,1 s recusado. WebM de 29,908 s aceito; 30,008 s e 30,108 s recusados. Padding também conta: o arquivo WebM gerado com `-t 30` declarou 30,008 s. A captura continua parando em 29 s.
- WebM acima do limite com cabeçalho adulterado para declarar 0,5 s foi recusado; WebM e MP4 truncados foram recusados. Testes offline cobrem exatamente 30 s, excesso de uma amostra, intervalos de timestamps, metadado divergente, frames ausentes/inválidos, codec/trilhas inesperados e JSON inválido/excessivo. Comparações usam razões exatas antes do arredondamento para nanossegundos.
- Binários portáteis ficam apenas em `build/ai-poc-media`, ignorado pelo Git. Pacote Windows obtido do [provedor listado pelo FFmpeg](https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip), com SHA-256 conferido: `60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba`. Provisionar explicitamente a ferramenta no ambiente; sem download automático durante validação. MP4 que dependa de seek incompatível com stdin falha fechado.
- **Verificação final:** `mvnw.cmd -U clean compile` passou com JDK 25. Regressão completa: **912 testes, zero falhas, zero erros e zero ignorados**, incluindo 61 da PoC (10 novos de duração), MySQL/Testcontainers e verificações existentes de isolamento. Mesma cópia temporária do POM com apenas a saída em `build/ai-poc-verification`, removida ao terminar; nenhum ajuste persistente no POM. `git diff --check` passou.
- A primeira tentativa da regressão falhou com Docker desligado e com uma falha no teste local de HTTP 429 (retornou `REQUEST_ERROR`). Após iniciar o Docker, a suíte inteira passou, inclusive esse teste, sem alterar o teste existente. Não foi demonstrada uma relação causal entre os dois problemas; logs das tentativas ficam em `build/duration-regression*.log`.
- Definição de pronto desta tarefa: regra de duração testada e docs atualizados. Não houve mudança de schema, endpoint ou DTO; migration e atualização de Swagger não se aplicam. A validação implementada continua no escopo da PoC da fase 1; a integração ao endpoint real pertence às fases seguintes.

Para verificar um arquivo sem usar a chave ou enviar áudio, depois de compilar as classes de teste e montar o classpath conforme a seção seguinte:

```powershell
$env:FIN_FFPROBE_PATH = (Resolve-Path 'build/ai-poc-media/ffmpeg-9.0.2/ffmpeg-9.0.2-essentials_build/bin/ffprobe.exe').Path
$cp = 'target/test-classes;target/classes;' + (Get-Content target/ai-poc-classpath.txt -Raw).Trim()
& "$env:JAVA_HOME/bin/java.exe" -cp $cp com.finance.ai.poc.GeminiVoiceProbe --inspect-audio 'C:/caminho/audio.webm'
```

O comando encerra com código 0 e MIME/bytes/duração quando aceita; código 1 quando recusa. A configuração de `FIN_FFPROBE_PATH` também é necessária para enviar WebM/MP4 com a flag de projeto gratuito. Os testes unitários comuns não exigem ffprobe instalado. Relatórios JSON de captura/navegador continuam pendentes; não foram reconstruídos a partir dos arquivos.

### Executar a prova manual no Windows

Executar em PowerShell, na raiz do backend, com `GEMINI_API_KEY` já configurada no ambiente e JDK 25. O programa não carrega `.env`. Confirmar no AI Studio que o projeto segue gratuito antes de usar a flag. Cada execução realiza no máximo uma chamada; os testes comuns não usam a chave.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.5.0.2'
.\mvnw.cmd test-compile dependency:build-classpath '-Dmdep.outputFile=target/ai-poc-classpath.txt' '-DincludeScope=test'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar a prova.' }
$probeClasspath = 'target\test-classes;target\classes;' + (Get-Content -Raw -LiteralPath 'target\ai-poc-classpath.txt').Trim()

# Teste da conexão/schema com frase fictícia fixa (ainda não é teste de áudio):
& "$env:JAVA_HOME\bin\java.exe" -cp $probeClasspath com.finance.ai.poc.GeminiVoiceProbe --free-project-confirmed --text-smoke

# Para áudio, executar separadamente com arquivo fictício até 2 MiB:
# WAV PCM até 30 s; WebM/Opus e MP4/AAC exigem FIN_FFPROBE_PATH (validação local de até 30 s).
& "$env:JAVA_HOME\bin\java.exe" -cp $probeClasspath com.finance.ai.poc.GeminiVoiceProbe --free-project-confirmed 'C:\caminho\audio-ficticio.wav'
```

O indicador `correspondeAmostraFixa` só verifica a frase fictícia descrita acima; `false` para outra frase não significa erro de extração. A avaliação por outros casos e os testes dos formatos estão registrados nas seções seguintes. O programa não imprime valores extraídos ou corpo da resposta. Em falhas exibe apenas classe da exceção ou código HTTP, sem mensagens brutas.

### Fechamento da fase 1 — pendência manual e correção da exportação (14/11/2026)

- SDK/modelo, extração dos formatos, avaliação de 80 casos com métricas, tratamento local de falhas, normalização de parcelas e limite de duração estão implementados/verificados conforme os registros desta fase. Não há endpoint ou persistência a implementar para fechar a prova; pertencem à fase 2.
- Os relatórios de captura não foram encontrados junto dos arquivos `fin-voz-*` em Downloads. O botão de compartilhamento da ferramenta enviava somente o áudio, embora houvesse link separado para JSON. Isso é uma possível explicação para a ausência, não comprovação de como os arquivos foram transferidos.
- Corrigidos `recorder.js` e `recorder.html`: compartilhar o par áudio + JSON quando `canShare` permitir, incluir `audioFileName` no relatório, explicar que ambos precisam ser salvos e manter os dois links quando o compartilhamento do par falhar/não existir. MIME, tamanho, duração do relógio e user agent continuam sendo medidos no navegador, sem upload ou chamada à IA. Não gerar relatórios retrospectivos para os áudios existentes.
- Verificação local do JS: cinco cenários simulados (par suportado, não suportado, erro ao consultar suporte, falha de compartilhamento e cancelamento), com duas capturas em cada. Conferidos correspondência de nomes/bytes/MIME, envio dos dois arquivos, disponibilidade dos links e encerramento do microfone. Sintaxe conferida com `node --check`. A simulação não equivale a executar Safari no iPhone.
- **Verificação desta correção:** `mvnw.cmd -U clean compile` passou; suíte completa com **912 testes, zero falhas, zero erros e zero ignorados**, incluindo MySQL/Testcontainers. Mesma saída isolada em `build/ai-poc-verification` por POM temporário removido ao terminar, sem alterar o POM do projeto. `git diff --check` passou. Nenhuma chamada à IA. Não houve alteração de contrato REST/schema; migration, Swagger e novos testes de isolamento não se aplicam à exportação da fixture manual.

**Decisão de encerramento em 14/11/2026:** Gabriel dispensou os relatórios como requisito para a fase 1 e pediu início da fase 2. Os relatórios não foram recebidos e não serão gerados retrospectivamente. A compatibilidade dos formatos foi testada, mas não se considera a ausência de relatórios uma comprovação do navegador de origem ou da experiência completa no Safari.

Na fase 4, usar novos pares áudio/JSON para conferir nomes, bytes, MIME, navegador declarado e duração do relógio, junto com a inspeção local. Não exigir igualdade exata entre relógio e duração decodificada; o limite depende da decodificação. Relatório é evidência declarada pelo cliente, não autenticação do navegador. Não reenviar áudio à IA só para validar correspondência; qualquer nova avaliação externa mantém as condições de autorização da prova.

### Fase 2 — entregas

- [x] Alinhar e documentar contrato de áudio para rascunho e a resposta da confirmação existente.
- [x] Promover extração/validação da PoC para a feature de IA, com SDK oficial, Clock injetado e saída estruturada validada.
- [x] Carregar somente categorias, contas e cartões ativos do usuário do JWT; usar referências temporárias no contexto e revalidar propriedade/estado ao montar o rascunho.
- [x] Validar tamanho, formato e duração antes da chamada; evitar persistência de áudio, transcrição, contexto e rascunho.
- [x] Retornar dados editáveis e pendências; recusar múltiplas transações; confirmar somente pela criação existente, com todas as regras revalidadas.
- [x] Traduzir falhas do provedor para ProblemDetail sem logar conteúdo sensível, com timeout, uma tentativa e sem fallback pago.
- [x] Manter a rota desativada por padrão e restrita ao ambiente de desenvolvimento até as proteções da fase 3.
- [x] Cobrir contrato, isolamento, validação, erros e confirmação com testes offline; compilar, executar regressão e atualizar docs.

### Fase 3 — regras alinhadas em 16/11/2026

- Gabriel escolheu **10 sucessos por usuário PRO/dia**, configuráveis; falhas não consomem a cota pessoal. Dia do sucesso em America/Sao_Paulo. Uma reserva bloqueia concorrência; o sucesso é registrado ao finalizar um rascunho validado, antes de devolver HTTP 200. Desconexão depois desse registro não permite garantir entrega ao cliente e mantém o sucesso contado.
- PRO manual por `users.pro_expires_at`, sem plano no JWT e sem endpoint administrativo. Verificar usuário ativo e validade no banco em cada operação. BASIC ou validade expirada é bloqueado antes da validação/chamada de IA. Concessão/revogação por SQL documentado, executado somente pelo operador.
- Gabriel delegou a escolha dos limites globais. Adotados **10 tentativas/minuto** em janela móvel e **400 tentativas/dia** no fuso America/Los_Angeles (meia-noite do Pacífico segundo a [documentação do Gemini](https://ai.google.dev/gemini-api/docs/rate-limits)). Configuráveis, com margem em relação aos valores informados no AI Studio em 13/11; não constituem confirmação de cotas atuais nem contabilizam PoC/outros consumidores do mesmo projeto. Erros e reservas abandonadas continuam contando globalmente, sem fallback pago. HTTP 429 do provedor bloqueia novas reservas por 60 s, configuráveis.
- Uma reserva em andamento por usuário, com lease de 120 s; requisição expirada não pode finalizar como sucesso. `Idempotency-Key` obrigatório, hash persistido junto do hash do áudio, sem armazenar áudio/contexto/resposta. Repetição devolve 409, sem nova chamada nem replay de dados financeiros; usar nova chave para tentar novamente intencionalmente. A reserva/contadores são atômicos em MySQL e sobrevivem a reinício/múltiplas instâncias.
- Rota continua dev/opt-in até o piloto. Não implementar pagamento, gateway ou limites comerciais do BASIC nesta fase.

Checklist:

- [ ] Migration de PRO e reservas/controle global; atualizar modelo de dados.
- [ ] Expor plano efetivo e validade em `/users/me`/usuário do login; verificar validade por Clock injetado.
- [ ] Guardar reservas, impedir repetição/concorrência e aplicar cota pessoal de sucessos e orçamento global de tentativas.
- [ ] Integrar a proteção ao rascunho e documentar concessão/revogação manual.
- [ ] Testar expiração, falhas, concorrência real, fusos, exclusão de conta e isolamento; validar compilação/regressão/OpenAPI.

### Fase 2 — implementação e uso local

- Contrato aprovado por Gabriel: `POST /api/v1/ai/transaction-drafts`, áudio binário no corpo, até 2 MiB/30 s, rascunho editável com pendências e confirmação pelo endpoint existente. A resposta de criação de parcelamento permanece um objeto (primeira parcela); corrigido o doc antigo que dizia lista.
- Feature em `com.finance.ai`, sem nova entidade/migration. SDK oficial `google-genai:1.79.0` promovido de test para execução, sem mudar versão. Modelo fixo `gemini-3.1-flash-lite`. Prompt adaptado ao catálogo dinâmico com versão `voice-draft-v1`, derivado das regras experimentais v4; não apresentar as métricas históricas da PoC como nova medição desse prompt.
- Contêiner e limite de duração validados antes de carregar contexto/chamar IA. Decoder nativo da PoC promovido para `FfprobeAudioInspector`, compartilhado pelo adaptador manual. Áudio de requisição lido como stream limitado, sem multipart/transcodificação ou arquivo temporário criado pela feature.
- Snapshot com até 100 recursos ativos por tipo e referências temporárias por requisição; IDs reais, saldos e histórico ficam fora do contexto enviado. Catálogo maior é recusado antes da chamada, sem truncar. Carregamento e revalidação são transações curtas; a orquestração suspende qualquer transação ambiente durante a chamada externa. Campos financeiros/contexto não são logados pelo código novo e o toString do DTO público é redigido.
- Parser estrito de eventos, BigDecimal e datas via Clock em São Paulo; referências são resolvidas apenas no snapshot e revalidadas no banco por userId/estado. Recusa múltiplos, aplica a política aprovada de parcelas e mantém pendências. Não grava transação. Testes demonstram que somente a confirmação existente grava e revalida recursos alterados desde o rascunho; compra parcelada conserva soma e devolve primeira parcela.
- Rota criada somente com perfil `dev`, sem `prod`, e `FIN_AI_ENABLED=true`; desativada por padrão. Configuração testada com perfil prod e com dev+prod para impedir exposição acidental. PRO/cotas continuam exclusivos da fase 3. Testes usam WAVs fictícios, mocks e servidor HTTP em loopback; nenhuma nova chamada ao Google ou medição de precisão real nesta etapa.
- **Verificação:** `mvnw.cmd -U clean compile` passou com JDK 25. Regressão completa: **956 testes, zero falhas, zero erros e zero ignorados** (44 novos da fase 2). Após completar as anotações de erros no OpenAPI, compilação repetida e os **9 testes de integração da feature passaram novamente**, incluindo schema/respostas do Swagger. Testes executados com o mesmo POM em saída isolada `build/ai-poc-verification`; POM temporário removido. `git diff --check` passou.
- **Decoder real:** 11 arquivos verificados localmente pelo `AudioValidator` promovido: os dois áudios originais aceitos, fixtures de fronteira aceitas/recusadas conforme duração, arquivos truncados e duração adulterada recusados. Sem envio ao Google. A primeira rodada focalizada teve cinco erros de configuração dos mocks de integração, corrigidos antes das verificações finais; não se alteraram regras para fazê-la passar.
- **Definição de pronto:** regras novas cobertas por testes de áudio/parser/service; isolamento e confirmação com JWT/MySQL; schema existente validado por Flyway/Hibernate, sem migration necessária; OpenAPI e docs atualizados. Frontend, experiência real no Safari, precisão do prompt dinâmico em piloto, PRO/cotas e liberação em produção seguem nas fases posteriores; não são declarados como entregues nesta fase.

Para testar manualmente com usuário/catálogo/áudio fictícios, chave já configurada no ambiente/configuração de desenvolvimento e MySQL local disponível:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.5.0.2'
$env:FIN_AI_ENABLED = 'true'
$env:FIN_FFPROBE_PATH = (Resolve-Path 'build/ai-poc-media/ffmpeg-9.0.2/ffmpeg-9.0.2-essentials_build/bin/ffprobe.exe').Path
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Com o backend iniciado, usar o Bearer JWT de login do FIN (não a chave Google) no Swagger em `http://localhost:8080/swagger-ui.html` ou, em outro terminal:

```powershell
curl.exe --request POST 'http://localhost:8080/api/v1/ai/transaction-drafts' --header 'Authorization: Bearer <JWT_DO_FIN>' --header 'Content-Type: audio/webm' --data-binary '@C:\caminho\audio-ficticio.webm'
```

No MP4 usar `audio/mp4`; no WAV usar `audio/wav`. Os bytes originais são mantidos e a extensão não determina o codec. Preencher `missingFields` e revisar todos os campos antes de enviar `TransactionRequest` ao `POST /transactions`. `requiresConfirmation=true` é uma indicação de revisão, não token de autorização ou proteção contra repetição. A criação existente mantém sua semântica (incluindo default de parcelas para 1 quando omitidas); a interface de revisão precisa exigir resolver as pendências antes da confirmação. Não habilitar produção ou dados reais nesta etapa.

### Captura de Chrome e Safari — ferramenta pronta

- Gabriel confirmou acesso ao Safari no iPhone 13.
- Página de teste em `src/test/resources/ai-poc/recorder.html` + `recorder.js`, servida com `node tools/ai-poc/serve-recorder.mjs` em **http://localhost:8765**. Não integra o frontend do produto e não altera a API REST.
- Para iPhone, usar HTTPS confiável. Nesta sessão foi iniciado um Cloudflare Quick Tunnel somente para o servidor estático, com executável oficial 2026.11.0 em `build/ai-poc-tools` e SHA-256 conferido contra o digest da release oficial. Não houve instalação global, criação de conta ou inclusão de dependência no Maven. O endereço é temporário, será informado na conversa e deixa de funcionar ao encerrar o túnel/computador.
- Para recriar o HTTPS com o binário local desta sessão, em outro terminal: `.\build\ai-poc-tools\cloudflared.exe tunnel --config .\build\ai-poc-tools\tunnel-config.yml --no-autoupdate --url http://127.0.0.1:8765 --protocol http2`. O arquivo de configuração é vazio (sem credenciais). Só o HTML e seu JS são expostos, nunca o diretório do projeto ou a API. Documentação: [Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).
- A página usa `MediaRecorder`, formato padrão nativo ou opção suportada escolhida, para preservar o arquivo original; encerra em 29 s e ao ocultar/sair da página. Salva arquivo `.webm` ou `.m4a` conforme o MIME real e relatório JSON de formato, bytes, duração medida pelo relógio da captura e navegador. Não solicita chave nem faz upload, analytics ou chamada à IA.
- **Chrome automatizado:** captura original de WebM/Opus (22.225 bytes) e MP4/AAC (33.206 bytes) com dispositivo fictício passou, incluindo segunda gravação, links, ausência de exceções JS e todas as tracks encerradas. Verificados também CSP, recusa de POST (405) e acesso negado a `.env`, POM e recursos do backend (404). URL HTTPS respondeu 200 com página esperada e CSP. Não equivale a teste de Safari.
- **Reconhecimento Java experimental (histórico de 13/11):** inicialmente detectava WAV, WebM/Opus e MP4/AAC por marcadores, mantendo bytes/MIME e até 2 MiB, sem comprovar duração/codec/trilhas dos comprimidos. A validação local por decodificação foi implementada em 14/11 conforme a seção de duração. Isso não representa auditoria de todo conteúdo malicioso possível ou liberação de endpoint público.
- **MP4/AAC do Chrome no Gemini:** chamada com áudio fictício aceita (`audio/mp4`), JSON válido, `UNCLEAR`, 6.910 ms, 366 tokens de entrada e 65 de saída (431 total). O dispositivo fictício não contém fala; esse resultado testa aceitação do contêiner, não precisão de transcrição nem compatibilidade do Safari.
- **WebM do Chrome no Gemini:** primeira chamada retornou HTTP 503, sem repetição automática. Uma nova execução manual posterior aceitou o arquivo (`audio/webm`), JSON válido, `UNCLEAR`, 12.019 ms, 363 tokens de entrada e 65 de saída (428 total). O resultado verifica aceitação do contêiner sem fala, não precisão. Não houve mudança de modelo ou transcodificação.
- **Validação desta etapa:** compilação `mvnw.cmd -U clean compile` passou; suíte completa passou com **881 testes, zero falhas, zero erros e zero ignorados** (30 testes offline da PoC). A suíte foi executada com a mesma cópia temporária do POM e saída isolada em `build/ai-poc-verification`, evitando o problema anterior da IDE; POM temporário removido. Sintaxe dos scripts conferida com `node --check`, captura em Chrome/rotas verificadas e `git diff --check` sem erros. Schema, API REST e regras do domínio financeiro não foram alterados nesta etapa.

Passos de Gabriel:

1. Abrir o endereço HTTPS informado na conversa no **Chrome do computador**. Manter “Padrão do navegador”.
2. Tocar em **Gravar**, permitir o microfone, ler a frase fictícia exibida e tocar em **Parar** antes de 30 s.
3. Ouvir a prévia e salvar **áudio original** e **relatório JSON**.
4. Repetir no **Safari do iPhone 13** com o mesmo endereço. Usar **Compartilhar / Salvar em Arquivos** se disponível ou o link de download. Não usar Gravador, converter ou reenviar por aplicativo que comprima o áudio.
5. Transferir os arquivos originais e relatórios para o computador ou anexá-los na conversa para a prova manual. O envio ao Gemini será feito pelo Java, com a chave somente no ambiente local.

O microfone exige contexto seguro; `http://IP-do-PC` no iPhone não substitui HTTPS. Referências: [getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) e [isTypeSupported](https://developer.mozilla.org/en-US/docs/Web/API/MediaRecorder/isTypeSupported_static).

### Avaliação e pendências da prova

### Política de parcelas aprovada — implementação local

- Gabriel escolheu explicitamente: **conta usa 1; cartão sem informação fica pendente**. Aplicada na PoC em `parseExtraction`, depois da validação de um único evento. Origem não resolvida também permanece pendente. Valores explícitos acima de 1 em conta continuam inválidos; múltiplos eventos não recebem defaults.
- Prompt e schema do modelo continuam `voice-poc-v4-events`; a política tem versão independente **`account-installments-v1`**, exibida pelo programa e registrada em novos resumos. Não foi alterada a criação manual, a API REST ou o schema de banco, nem adicionada dependência.
- O corpus original e os relatórios já produzidos foram preservados. Em avaliações futuras, o comparador deriva uma cópia do esperado sob a política aprovada; os novos resumos informam `normalization` e `effectiveOracleChanges`. No corpus atual, só `c35` muda de `null` para 1 no esperado efetivo. Isso não reclassifica retroativamente a rodada anterior nem equivale a uma nova medição de precisão.
- Testes locais cobrem conta com parcelas null, cartão e origem não resolvida com pendência, preservação de parcelas explícitas em cartão, rejeição de parcelas inválidas em conta e XOR antes de normalizar, política versionada e integridade do oráculo histórico. A ausência na fala continua sendo identificada pelo extrator; a normalização trata o resultado null sem garantir que o modelo nunca faça inferências incorretas.
- Não houve nova chamada ao Google nem reprocessamento de gravações humanas nesta tarefa. `mvnw.cmd -U clean compile` passou com JDK 25; **902 testes passaram, zero falhas/erros/ignorados**, em saída isolada para evitar interferência da IDE, incluindo 51 testes offline da PoC. Após o ajuste final de reconhecimento de conta no comparador, os 21 testes focalizados do avaliador também passaram. `git diff --check` sem erros; POM temporário removido.
- Definição de pronto desta tarefa: regra aprovada registrada em `domain-rules.md`, implementação/avaliação versionadas, cobertura das situações de conta/cartão/origem indefinida e invalidações, compilação e testes aprovados, arquitetura/plano atualizados. Migration, Swagger e novos testes de isolamento com banco não se aplicam, pois não houve mudança de entidade, endpoint ou acesso a dados do usuário. A fase 1 permanece em andamento pelas pendências de áudio.

### Correção de múltiplas transações — estrutura de eventos

- `voice-poc-v3` tentou priorizar classificação no prompt e foi avaliado primeiro em `voice-holdout-v1`, 20 frases congeladas antes das chamadas. Resultado: 12 correspondências exatas, 3 respostas rejeitadas pelo validador, 2 HTTP 503 e 3 erros de requisição em aproximadamente 30 s; nenhuma divergência entre as 12 respostas validadas. Os rejeitados não produziram rascunho, mas também não cumpriram o resultado esperado. Não foi executada uma regressão de 50 com v3. Artefatos: `build/ai-poc-evaluation/results-2026-11-13T19-24-31-952180300Z/`.
- A versão final experimental **`voice-poc-v4-events`** faz o modelo enumerar até 3 eventos em um array. **O Java decide a classificação**, preservando o formato normalizado dos oráculos: 0 = UNCLEAR; 1 = SINGLE, com validações existentes; 2 ou 3 = MULTIPLE, com todos os campos financeiros descartados. Não soma valores nem seleciona uma transação para salvar. Ainda depende da enumeração correta pelo modelo; não é garantia de reconhecer toda fala múltipla possível.
- A mudança é restrita ao JSON interno da PoC e ao parser `parseExtraction`. Contrato REST, entidade, schema de banco, DTOs públicos e criação manual permanecem iguais. Modelo, SDK, cota gratuita e proibição de tools permanecem. Os arquivos/oráculos do corpus original não foram alterados.
- Depois da avaliação v3, os 20 casos `holdout` passaram a ser regressão de desenvolvimento. Um último conjunto de **10 frases inéditas** (`evaluation-unseen.json`, `voice-unseen-v1`) foi fixado e gerado offline antes da primeira chamada v4: 5 múltiplas, 3 compras únicas de contraste (itens, parcelas e correção verbal), 1 parcial e 1 injeção com dois eventos. Não foi alterado após olhar os resultados.
- **Primeira avaliação inédita v4:** 10 chamadas, **8 respostas validadas/exatas e 2 HTTP 503** (`u05`, `u07`), zero divergências ou respostas inválidas nas respostas recebidas. São 8/10 = 80% por tentativa e 8/8 = 100% entre respostas recebidas; não omitir as indisponibilidades. p50 3.019 ms, p95 6.822 ms; 7.535 tokens de entrada + 1.184 de saída = 8.719 informados; uso ausente nas 2 falhas. Artefatos: `build/ai-poc-evaluation/results-2026-11-13T19-32-08-919485700Z/`.
- Testes locais com `HttpServer` em loopback confirmaram **uma tentativa** para HTTP 429 e timeout, sem usar chave real ou Google; cota/configuração param imediatamente e 503 só interrompe o lote após 5 falhas consecutivas. Envelopes malformados, status vindo do modelo, mais de 3 eventos e campos incorretos são rejeitados. Múltiplos eventos não retêm valor/conta/tipo no resultado normalizado.
- `mvnw.cmd -U clean compile`: passou com JDK 25. Regressão completa final em saída isolada: **897 testes, zero falhas, zero erros e zero ignorados**, incluindo **46 testes offline da PoC**. A execução normal de testes não chama Google. POM temporário removido após a suíte.
- **Regressão v4 dos 50 casos originais concluída:** 45 exatos, 1 divergência (`c35`, parcelas preenchidas sem informação explícita), 3 HTTP 503 e 1 erro de requisição em 30.017 ms. Os casos `c44`/`c45` que falharam no v2 e o caso `c46` retornaram corretamente `MULTIPLE`. Artefatos: `build/ai-poc-evaluation/results-2026-11-13T19-34-03-872204Z/`.
- **Regressão v4 dos 20 casos adicionais concluída:** 19 exatos, 1 HTTP 503 (`h03`), zero divergências ou respostas inválidas recebidas. O conjunto já havia sido inspecionado na rodada v3 e não é apresentado como evidência inédita de v4. p50 2.283 ms, p95 8.088 ms; 17.204 tokens de entrada + 2.374 de saída = 19.578 informados; uso ausente na falha. Artefatos: `build/ai-poc-evaluation/results-2026-11-13T19-40-05-476470700Z/`.

| Conjunto v4 | Tentativas | Exatos | Divergências | Falhas operacionais |
|---|---|---|---|---|
| Original | 50 | 45 | 1 (parcelas) | 4 |
| Adicional, já inspecionado | 20 | 19 | 0 | 1 |
| Inédito na primeira chamada v4 | 10 | 8 | 0 | 2 |
| Total | 80 | 72 | 1 | 7 |

- **Múltiplos eventos:** 18 casos esperados (inclui injeções), 16 resultados classificados corretamente e 2 HTTP 503. Não houve múltiplo retornado como SINGLE entre os recebidos. Não afirmar aprovação dos 2 casos sem resposta nem garantia geral de enumeração do modelo.
- **Métricas agregadas v4:** 73 respostas aceitas pelo pipeline, 72 exatas (72/80 = 90% por tentativa; 72/73 = 98,63% entre respostas aceitas). São resultados normalizados pelo Java, não uma medida isolada do LLM. p50 2.911 ms, p95 9.721 ms, máximo 30.017 ms; 64.748 tokens de entrada + 7.756 de saída = 72.504 informados. As 7 falhas foram 6 HTTP 503 e 1 erro de requisição no limite de timeout, sem uso informado. Não assumir consumo/custo zero para falhas.
- **Pendência c35:** em um gasto na conta Carteira sem mencionar parcelas, o modelo retornou `1`; o oráculo vigente exige `null`. O valor e os demais campos ficaram corretos. A criação manual já considera 1 parcela por padrão e conta não aceita parcelamento, mas a PoC ainda declara campos não informados como pendentes. Não mudar silenciosamente o oráculo nem apresentar esse resultado como exato: alinhar a política do rascunho em tarefa própria, preservando o histórico das medições.
- **Decisão desta tarefa:** classificação de múltiplos corrigida e verificada na prova, permanecendo experimental e sem endpoint público. Manter a fase 1 aberta pelas pendências listadas acima. A revisão humana continua obrigatória e não se pode exigir precisão/ disponibilidade de produção a partir de uma única voz sintética ou dessas amostras pequenas.

Comandos adicionais do gerador/avaliador (mesma preparação de classpath já documentada):

```powershell
.\tools\ai-poc\generate-evaluation-audio.ps1 -Corpus holdout -OutputDirectory 'build/ai-poc-evaluation/audio-holdout-novo'
.\tools\ai-poc\generate-evaluation-audio.ps1 -Corpus unseen -OutputDirectory 'build/ai-poc-evaluation/audio-unseen-novo'
& "$env:JAVA_HOME\bin\java.exe" -cp $probeClasspath com.finance.ai.poc.GeminiVoiceEvaluation --free-project-confirmed --synthetic-fixtures-only 'build/ai-poc-evaluation/audio-holdout-novo' --holdout
& "$env:JAVA_HOME\bin\java.exe" -cp $probeClasspath com.finance.ai.poc.GeminiVoiceEvaluation --free-project-confirmed --synthetic-fixtures-only 'build/ai-poc-evaluation/audio-unseen-novo' --unseen
```

### Avaliação sintética de 50 casos — duas rodadas concluídas

- Corpus `voice-corpus-v1` em `src/test/resources/ai-poc/evaluation-cases.json`, com data de contexto fixa **13/11/2026**: 30 casos claros, 10 incompletos/ambíguos, 3 sem transação (inclui silêncio), 3 com múltiplas transações e 4 com instruções maliciosas. Resultados esperados definidos antes da rodada; prompt permanece `voice-poc-v1`.
- WAVs gerados **offline** com Microsoft Maria Desktop em PCM mono/16 kHz/16 bits, um caso de silêncio sintético, em `build/ai-poc-evaluation/audio-v1`. Nenhuma gravação humana ou dado financeiro real entra nessa rodada. Manifesto vincula hashes do corpus e dos 50 arquivos; todos são verificados antes da primeira chamada.
- Avaliador manual `GeminiVoiceEvaluation`, até 50 chamadas ao `gemini-3.1-flash-lite`, uma tentativa por caso e intervalo mínimo de 6 s entre inícios. Para em erro de configuração/acesso/cota ou 5 falhas consecutivas. Sem tools, transcodificação, alteração de modelo ou retry automático.
- Artefatos locais em `build/ai-poc-evaluation/results-<UTC>/results.jsonl` e `summary.json`: apenas IDs, grupos, divergências por campo, tipo de resultado, HTTP, latência e uso informado. Sem resposta bruta ou texto/áudio no log. Dados de thinking/uso ausentes não são tratados como zero.
- Comparação exata dos 8 campos. Erros HTTP/JSON/validação não ganham crédito por campo. O resumo informa contagens sobre os 50 planejados, respostas validadas, execuções pendentes, grupos e acerto conjunto de valor/tipo/parcelas nos 30 casos claros. Percentis de latência usam nearest rank sobre as tentativas, incluindo falhas HTTP, sem espera do limitador.
- Esses testes são um baseline de desenvolvimento com uma única voz sintética, sem ruído/sotaques reais nem conjunto independente reservado. Não demonstram precisão de produção, segurança geral contra injeção ou ausência de alucinações. Os casos claros de Chrome/Safari já testados são evidência separada.
- **Baseline `voice-poc-v1` concluído:** 50 tentativas, 47 respostas validadas, 42 correspondências exatas, 5 divergências e 3 HTTP 503. Exatidão por tentativa: 42/50 = 84%; entre respostas validadas: 42/47 = 89,36%. Casos claros: 27/27 respostas corretas, 3 indisponibilidades (27/30 tentativas). Grupos sem transação, múltiplos e injeção: respectivamente 3/3, 3/3 e 4/4. Incompletos/ambíguos: 5/10 correspondências exatas.
- Divergências do baseline: `c32` preencheu data ausente; `c34` escolheu conta ausente; `c35` preencheu parcelas ausentes; `c38` escolheu cartão para uma instituição não cadastrada; `c39` não preservou campos conhecidos quando o valor era ambíguo. Não houve corpo de resposta logado; a análise usa o oráculo e os campos divergentes. Baseline preservado em `build/ai-poc-evaluation/results-2026-11-13T18-55-21-580850500Z/`.
- Baseline: p50 **3.441 ms**, p95 **10.442 ms** (tentativas incluindo erros, sem espera do limitador); uso informado **25.115 tokens de entrada + 3.874 de saída = 28.989**. Uso das 3 falhas HTTP não informado, não assumir custo/uso zero.
- **Ajuste experimental `voice-poc-v2`:** explicita ausência como null, proíbe defaults por catálogo de opção única e substituição de instituições desconhecidas, e conserva fatos conhecidos quando apenas um campo é ambíguo. Schema, valores monetários, SDK, modelo, catálogo, corpus, WAVs e data permanecem iguais. A segunda rodada usa os mesmos 50 casos para regressão do desenvolvimento, sem conjunto independente; não usar melhoria nesse corpus como alegação de precisão de produção.
- **Rodada `voice-poc-v2` concluída:** 50 tentativas, 49 respostas validadas, 47 correspondências exatas, 2 divergências e 1 HTTP 503 (`c48`). Exatidão por tentativa: 47/50 = 94%; entre respostas validadas: 47/49 = 95,92%. Corpus idêntico, sem regravação ou alterações nos resultados esperados. Artefatos em `build/ai-poc-evaluation/results-2026-11-13T19-03-00-259560Z/`.
- Os cinco casos problemáticos do baseline (`c32`, `c34`, `c35`, `c38`, `c39`) corresponderam ao esperado na segunda rodada. Contudo, `c44` (duas despesas) e `c45` (receita e despesa) não foram classificados como `MULTIPLE`; apresentaram campos preenchidos de uma única transação, violando a restrição de uma transação por áudio. É uma regressão observada na comparação, não uma demonstração causal isolada do efeito do prompt, pois só houve uma tentativa por caso em cada versão.
- **Decisão:** manter a fase 1 em andamento. O prompt v2 permanece apenas na prova manual, sem integração ao produto. Não aprovar a fase por uma taxa agregada acima de 95% entre respostas válidas: a falha em múltiplos lançamentos é material. Próxima tarefa é rever a classificação de eventos antes da extração, com regressão do corpus atual e casos inéditos. O problema não foi ocultado por nova rodada até obter aprovação.
- Rodada v2: p50 **1.804 ms**, p95 **10.484 ms**, máximo observado **26.459 ms**; uso informado **35.940 tokens de entrada + 3.781 de saída = 39.721**. A chamada HTTP 503 não informou uso; não assumir zero. Diferenças de latência entre rodadas são observações deste ambiente/provedor, não um efeito de desempenho comprovado do prompt.

| Grupo | Casos | v1 exatos | v2 exatos | Observação |
|---|---|---|---|---|
| Claros | 30 | 27 | 30 | v1 teve 3 HTTP 503; todos os claros com resposta válida acertaram valor/tipo/parcelas |
| Incompletos/ambíguos | 10 | 5 | 10 | Cinco divergências do baseline resolvidas nesta amostra |
| Sem transação/silêncio | 3 | 3 | 3 | Somente esses casos sintéticos foram avaliados |
| Múltiplas transações | 3 | 3 | 1 | Dois casos incorretos na rodada v2; impede aprovação |
| Instruções maliciosas | 4 | 4 | 3 | v2 teve 1 HTTP 503; não comprova segurança geral |

- **Validação de código:** `mvnw.cmd -U clean compile` passou com JDK 25; **889 testes passaram, sem falhas/erros/ignorados**, incluindo 38 testes offline da PoC (8 novos de corpus, comparação, hashes e métricas). Regressão executada em saída isolada com POM temporário, removido após a execução, como nas tarefas anteriores. Testes comuns não chamam Google. `git diff --check` passou. Nenhum schema, endpoint REST, entidade ou regra de transação persistida foi alterado.
- O código de saída 0 do avaliador significa que a execução terminou; não significa aprovação de precisão. Examinar `summary.json`, pendências e falhas antes de decidir o próximo passo. Os relatórios não contêm o conteúdo das respostas.
- O gerador foi corrigido durante a preparação para usar `Int16` no WAV de silêncio; a pasta parcial inicial foi preservada e uma nova saída completa foi gerada. Nenhum desses arquivos entrou no Git.

Reproduzir (PowerShell; chamadas externas só no último comando e após conferir projeto gratuito):

```powershell
# Geração offline; escolher uma pasta nova em cada geração (o script não sobrescreve saídas).
.\tools\ai-poc\generate-evaluation-audio.ps1 -OutputDirectory 'build/ai-poc-evaluation/audio-novo'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.5.0.2'
.\mvnw.cmd test-compile dependency:build-classpath '-Dmdep.outputFile=build/ai-poc-tools/classpath.txt' '-DincludeScope=test'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar avaliação.' }
$probeClasspath = 'target\test-classes;target\classes;' + (Get-Content -Raw -LiteralPath 'build\ai-poc-tools\classpath.txt').Trim()
& "$env:JAVA_HOME\bin\java.exe" -cp $probeClasspath com.finance.ai.poc.GeminiVoiceEvaluation --free-project-confirmed --synthetic-fixtures-only 'build/ai-poc-evaluation/audio-novo'
```

Recebimento das gravações de Gabriel (13/11/2026):

- `fin-voz-2026-11-13T18-06-18-411Z.webm`: 173.379 bytes; reconhecido localmente como WebM/Opus pelos marcadores, dentro de 2 MiB e sem marcadores de vídeo detectados.
- `fin-voz-2026-11-13T18-09-12-715Z.m4a.mp4`: 232.650 bytes; reconhecido localmente como MP4/AAC pelos marcadores, dentro de 2 MiB e sem marcadores de vídeo detectados. A extensão dupla não impede o reconhecimento por conteúdo.
- Relatórios JSON não recebidos; duração e identificação do navegador ainda não foram comprovadas. Os marcadores não equivalem a validação completa do contêiner.
- Gabriel autorizou explicitamente o envio dos dois arquivos à API gratuita Google/Gemini, ciente do uso do conteúdo para melhoria de produtos e possível revisão humana. Após essa autorização, foi executada uma chamada por arquivo, sem repetição automática ou mudança de modelo. Os áudios não foram copiados para o repositório e o conteúdo bruto das respostas não foi logado.

Resultados das gravações fornecidas por Gabriel — `gemini-3.1-flash-lite`, SDK 1.79.0, prompt `voice-poc-v1`, thinking `MINIMAL`:

| Arquivo | MIME detectado | Latência | Tokens entrada | Tokens saída | Total | Resultado |
|---|---|---|---|---|---|---|
| `fin-voz-2026-11-13T18-06-18-411Z.webm` | `audio/webm` | 3.850 ms | 585 | 70 | 655 | JSON válido, SINGLE, sem pendências, amostra esperada correta |
| `fin-voz-2026-11-13T18-09-12-715Z.m4a.mp4` | `audio/mp4` | 4.562 ms | 561 | 89 | 650 | JSON válido, SINGLE, sem pendências, amostra esperada correta |

A comparação em memória verificou todos os campos da frase fictícia: `EXPENSE`, `45.00`, data do dia em São Paulo, 1 parcela, `cat_food`, `acc_wallet` e cartão ausente. Os dois testes passaram, sem transcodificação e sem gravação financeira. Tokens de thinking não informados (não assumir zero). Isso valida esses arquivos, sem comprovar os navegadores/versões de origem ou a duração na ausência dos relatórios. Duas gravações da mesma frase não constituem a avaliação de aproximadamente 50 casos nem comprovam a meta de 95% ou p50/p95 de produção.

Nesta tarefa só foram executadas as duas chamadas manuais e atualizada a documentação; código e dependências permaneceram iguais aos já verificados com 881 testes na etapa anterior. `git diff --check` passou. Não foi repetida a suíte sem alteração de código.

1. **Preparar o projeto de teste:** confirmar nível gratuito e cobrança desabilitada no AI Studio, modelo Flash-Lite disponível e limites efetivos de requisições/tokens por minuto e por dia. Nunca registrar chaves no repositório ou solicitar seu envio pela conversa.
2. **Fixar a integração:** escolher versão do SDK compatível com Java 25 / Boot 4.1.1; validar áudio + JSON estruturado na mesma chamada, timeout, metadados de uso e configuração de thinking específica do modelo. Sem tools.
3. **Verificar formatos reais:** testar arquivos produzidos por Chrome e Safari, validando contêiner, codec, MIME real, tamanho e duração no backend. Definir limite de bytes e estratégia para arquivos inválidos. Só propor transcodificação se os testes mostrarem necessidade.
4. **Montar amostra de avaliação:** aproximadamente 50 gravações com dados fictícios e resultados esperados. Incluir centavos, correção verbal, datas relativas, nomes ambíguos, total versus valor da parcela, silêncio/ruído, campos faltantes, múltiplas compras e instruções maliciosas no áudio/contexto.
5. **Medir:** acerto por campo (especialmente valor, tipo e parcelas), pendências/ambiguidades, JSON inválido, falhas, latência p50/p95 e tokens por modalidade quando disponíveis. Registrar modelo e versão do prompt usados. Meta proposta: pelo menos 95% nos campos críticos dos casos claros; revisar cada erro. A amostra não garante precisão em produção.
6. **Testar indisponibilidade:** limites do provedor, timeout e respostas inválidas devem levar a erro controlado e possibilidade de preenchimento manual, sem tentativas ilimitadas nem cobrança alternativa.
7. **Fechar a prova:** registrar resultados neste plano, modelo e versão de dependência escolhidos, limites técnicos e eventuais necessidades de infraestrutura. Atualizar arquitetura, contrato e regras aplicáveis antes de iniciar a fase 2.

**Privacidade e liberação:** a recomendação para o início gratuito é usar dados fictícios, sem informações pessoais nos áudios ou contexto. O uso de áudio financeiro real exige resolver previamente o tratamento de dados: os termos dos serviços gratuitos informam uso de conteúdo para melhoria de produtos, possível revisão humana e orientam a não enviar informações pessoais, confidenciais ou sensíveis. Aviso ao usuário não substitui essas condições. A decisão de liberar dados reais permanece pendente; não habilitar serviço pago sem nova decisão de Gabriel.

Referências consultadas no planejamento (13/11/2026), a reconfirmar na fase 1:

- [Termos do Gemini](https://ai.google.dev/gemini-api/terms) — uso de dados nos níveis gratuito e pago.
- [Limites do Gemini](https://ai.google.dev/gemini-api/docs/rate-limits) — limites por projeto; consultar valores efetivos no AI Studio.
- [Áudio no Gemini](https://ai.google.dev/gemini-api/docs/audio) — formatos e uso de áudio.
- [SDK Java oficial](https://github.com/googleapis/java-genai) — dependência e integração.

## Histórico da V1 — resumo das fases

Os status e responsáveis das seções antigas abaixo retratam o planejamento original, não a situação atual. Para trabalho novo, seguir a seção **Situação atual** e o plano da V2. A fase 0 da V2 não é uma auditoria retroativa de todos os itens da V1.

| Fase | Dono | Conteúdo | Status |
|---|---|---|---|
| 0 | Biel | Fundação do projeto | [ ] |
| 1 | Biel | Usuário e autenticação | [ ] |
| 2 | Biel | Contas, categorias, transações | [ ] |
| 3 | A definir | Metas, dashboard, simulações | [ ] |
| 4 | A definir | Transferências | [ ] |
| 5 | A definir | Cartão de crédito + faturas (à vista) | [ ] |
| 6 | A definir | Parcelamento, pagamento de fatura, limite | [ ] |
| 7 | Juntos | Testes, Swagger, CORS, deploy | [ ] |

**Por que o cartão fica por último:** ele toca `Transaction`, `Dashboard`, `Account` e `Goal` ao mesmo tempo. Se atrasar, não trava o resto do v1.

## Fase 0: Fundação (juntos)

**Precisa estar no `main` antes de dividir o trabalho.**

- [ ] Projeto Spring Boot, `pom.xml` com as dependências aprovadas, perfis `dev` e `prod`
- [x] Confirmar o pacote base real (classe com `@SpringBootApplication`) e registrar aqui: `com.finance`
- [x] `BaseEntity` (`createdAt`/`updatedAt`) + `@EnableJpaAuditing`
- [ ] `Clock` (`America/Sao_Paulo`) + `hibernate.jdbc.time_zone=UTC`
- [x] `GlobalExceptionHandler` com `ProblemDetail` e exceções (`ResourceNotFoundException`, `ConflictException`, `BusinessRuleException`)
- [ ] Swagger/springdoc (só `dev`) e Actuator (`/actuator/health`)
- [ ] **`V1__init.sql` completo** (tabelas, índices, `CHECK`), revisado pelos dois antes de codar (ver `docs/data-model.md`)
- [ ] Testcontainers (MySQL) funcionando num teste de "contexto sobe"

## Fase 1: Usuário e autenticação (Breno)

- [x] `User`, `RefreshToken`, repositories
- [x] `GoogleTokenVerifier` (assinatura, `aud`, `iss`, expiração, `email_verified`)
- [x] `JwtService` (`NimbusJwtEncoder`) + `JwtDecoder` via Resource Server; `sub` = id interno
- [x] `SecurityConfig` (rotas públicas, stateless, CSRF off, CORS por `CORS_ORIGINS`)
- [x] `CurrentUser`
- [x] `AuthService`: login Google, criação do usuário **com seed** (categorias padrão + conta "Carteira"), refresh com rotation, logout
- [x] `POST /auth/google`, `/auth/refresh`, `/auth/logout`
- [x] `GET /users/me`, `DELETE /users/me` (apaga tudo, LGPD)
- [ ] Testes: refresh reutilizado rejeitado, `active = false` não autentica, primeiro login cria seed, segundo login não duplica

## Fase 2: Contas, categorias, transações (Biel)

Ordem: `Account` → `Category` → `Transaction`.

- [ ] `Account`: CRUD, saldo calculado, arquivar vs. hard delete, última conta ativa
- [ ] `Category`: CRUD, soft delete, tipo imutável com transações
- [ ] `Transaction`: CRUD, XOR no service **e** no banco, tipo = tipo da categoria, filtros e paginação
- [ ] Schema já com campos de cartão; **compra no cartão ainda não implementada** nesta fase
- [ ] Testes de isolamento por feature (A não lê/edita/exclui dado do B, inclusive `accountId`/`categoryId` no payload)
- [ ] Testes: saldo bate com a fórmula após editar/excluir; XOR rejeitado no banco

## Fase 3: Metas, dashboard, simulações (a definir)

`Simulation` é independente e pode ir em paralelo.

- [ ] `PeriodResolver` (8 períodos + período anterior equivalente)
- [ ] Dashboard: `summary`, `expenses-by-category`, `evolution` (uma query + baldes com zero), `recent-transactions`
- [ ] `Goal` `SAVINGS` + `GoalContribution` (mesma transação, retirada não negativa)
- [ ] `Goal` `INCOME`/`EXPENSE` (progresso calculado pela query do dashboard) e `GoalState`
- [ ] `PATCH /goals/{id}/cancel`
- [ ] Simulações: investimento e quitação de dívida (422 quando nunca quita)
- [ ] Testes: `PeriodResolver` (quinzena, semana ISO, virada de trimestre/semestre/ano), estados da meta de gasto, contribuição em `INCOME`/`EXPENSE` → 422

## Fase 4: Transferências (a definir)

- [ ] `Transfer`: CRUD, `from ≠ to`, contas do usuário e não arquivadas
- [ ] Saldo da conta incorpora transferências; dashboard **ignora** transferências
- [ ] Conta com transferência só pode ser arquivada
- [ ] Teste de isolamento

## Fase 5: Cartão de crédito e faturas, compra à vista (a definir)

- [ ] `CreditCard`: CRUD, dias 1–28, arquivar vs. hard delete
- [ ] `InvoiceService`: `invoiceReference`, `closingDate`, `dueDate`, total, pago, status (5 estados, na ordem)
- [ ] Compra no cartão em `Transaction` (só `EXPENSE`), `invoiceReference` gravado na criação
- [ ] `GET /credit-cards`, `/invoices`, `/invoices/{yyyy-MM}`
- [ ] `creditCardDebt` em `GET /accounts`
- [ ] Dashboard e metas passam a incluir gastos do cartão na data da compra
- [ ] Testes: compra no dia do fechamento e no dia seguinte; mudar `closingDay` não move compras antigas; cartão rejeita `INCOME`

## Fase 6: Parcelamento, pagamento de fatura, limite (a definir)

- [ ] Parcelamento: N transações com mesmo `installmentGroupId`, resto na última, `plusMonths`, máx. 24x
- [ ] Excluir apaga o grupo; editar categoria/descrição vale para o grupo; valor/parcelas não editáveis
- [ ] `InvoicePayment`: pagamento parcial, 422 em fatura `OPEN` ou acima do total
- [ ] Pagamento reduz saldo da conta e **não** entra em despesas do dashboard
- [ ] Limite usado/disponível (`availableLimit` pode ser negativo)
- [ ] Testes: soma das parcelas = total; 31/01 → 28/02; os 5 estados da fatura

### Regressão do cadastro parcelado (12/11/2026)

- [x] Atualizar `TransactionServiceTest` para `saveAll`, parcelas em 2x/4x/24x, centavos na última parcela, competências consecutivas e retorno da primeira parcela.
- [x] Cobrir quantidade inválida, parcela de valor zero, pagamento em 1x e validações de conta/cartão.
- [x] Alinhar datas ao dominio: avancar cada parcela a partir da data da compra, ajustar fim de mes e calcular sua competencia.

## Fase 7: Fechamento (juntos)

- [ ] Completar a checklist de testes (unitários e de integração)
- [ ] Swagger fiel ao `docs/api-spec.md`; publicar cedo para o João gerar os tipos
- [ ] CORS com a origem do front
- [ ] Remover qualquer atalho de `userId` fixo do perfil `dev`
- [ ] Deploy no Railway (`PORT`, variáveis de ambiente, health check)

## Checklist de testes (mínimo do v1)

- [ ] **Isolamento:** A não acessa dado do B em cada feature (inclui `accountId`, `creditCardId`, `categoryId` no payload)
- [ ] XOR de `account`/`creditCard` rejeitado na service e no banco
- [ ] Cartão rejeita `INCOME`
- [ ] `invoiceReference` nos limites (dia do fechamento e dia seguinte)
- [ ] Mudar `closingDay` não move compras antigas
- [ ] Parcelas: soma = total, resto na última, fim de mês
- [ ] Status da fatura nos 5 estados; pagamento acima do total → 422; pagamento em fatura `OPEN` → 422
- [ ] Transferência e pagamento de fatura não alteram receitas/despesas do dashboard
- [ ] Saldo da conta bate com a fórmula após editar e excluir
- [ ] `PeriodResolver`: quinzena, semana ISO, viradas; período anterior correto
- [ ] Evolução preenche baldes vazios com zero
- [ ] Meta `SAVINGS`: retirada não deixa negativo; contribuição em `INCOME`/`EXPENSE` → 422
- [ ] Meta `EXPENSE`: `IN_PROGRESS`, `WARNING`, `EXCEEDED` e `ACHIEVED` ao encerrar
- [ ] Simulação de dívida com pagamento ≤ juros → 422
- [ ] Refresh token reutilizado é rejeitado

## Desenvolvimento sem autenticação (temporário)

Enquanto a fase 1 não está no `main`, `Goal` e `Simulation` podem ser testados com um `userId` fixo **apenas no perfil `dev`**. É uma exceção temporária à regra "`userId` só do JWT" e **deve ser removida antes de produção** (item da Fase 7).

## Decisões em aberto

Resolver antes de a fase correspondente começar.

| # | Decisão | Quando | Recomendação |
|---|---|---|---|
| 1 | Donos das fases 3, 4, 5 e 6 | Fim da fase 2 | Dividir de modo que cartão (5 e 6) fique com quem não pegou o dashboard, para evitar conflito em `Transaction`/`Dashboard` |
| 2 | Exclusão de usuário: `ON DELETE CASCADE` ou ordem explícita na service | Antes do `V1` | Ordem explícita numa única `@Transactional` |
| 3 | `GoalStatus` tem `COMPLETED`? | Antes do `V1` | **Não**: `domain-rules.md` define só `ACTIVE`/`CANCELLED`; "atingida" é o `GoalState` calculado |
| 4 | Versão do Spring Boot | Fase 0 | Seguir o `pom.xml` (Boot 4.1.x, conforme `CLAUDE.md`) |

## Anotações (fora do escopo do v1)

Ideias que apareceram no caminho e **não** serão implementadas agora. Anote aqui:

- Estorno de compra no cartão, juros/multa/rotativo/IOF, cartão adicional, cashback, importação de extrato/fatura, transações recorrentes, orçamento recorrente, metas vinculadas a conta, simulações salvas, multi-moeda, notificações, imagem em meta.
- Rate limit em `/auth/*` (Bucket4j) e cache do dashboard: só após medir.

## Ajuste: pagamento antecipado de faturas

- [x] Permitir pagamento parcial/total antes do fechamento, preservando OPEN e os cálculos derivados.
- [x] Clock injetado no InvoiceService e cobertura unitária determinística; contrato e testes HTTP atualizados.
- [x] Compilação com JDK 25 e suíte completa: 850 testes, sem falhas.
- [x] Frontend: build, 18 testes de API/mock e smoke de faturas em 390/1440 px com transporte controlado.
- [x] Teste integrado adicional com MySQL: antecipação, recarga, saldo, limite e novas compras. Suíte de pagamentos: 15 testes, sem falhas (851 casos no conjunto das suítes).
- [x] Validação manual com sessão Google real e publicação coordenada (backend antes do frontend).
