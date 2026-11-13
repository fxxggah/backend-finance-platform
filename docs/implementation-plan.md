# Plano de implementação

Leia **no início de cada tarefa**: fase atual, dono e status. Uma feature por vez, na ordem abaixo. Não comece algo novo antes de fechar o que está em andamento.

**Legenda:** `[ ]` a fazer · `[~]` em andamento · `[x]` pronto (cumpre a Definição de pronto do `AGENTS.md`)

## Situação atual (13/11/2026)

- **Responsável:** Gabriel, autor e responsável pelo projeto inteiro. Frontend em outro repositório; o trabalho aqui é restrito ao backend.
- **V1:** funcional, conforme confirmação de Gabriel. Os checklists antigos estão desatualizados e foram preservados abaixo como histórico, sem declarar verificações individuais como realizadas nesta tarefa.
- **Fase atual:** V2 / fase 1 — prova técnica gratuita, em andamento; extração por WAV sintético e pelos dois arquivos WebM/MP4 com fala de Gabriel validada. Fase 0 concluída em 13/11/2026.
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
- A fase 0 foi exclusivamente documental. A fase 1 adiciona somente a prova manual e seus testes; endpoints públicos, persistência e assinatura ficam nas fases posteriores.

### Direção técnica proposta para validar na fase 1

- Preferir o SDK oficial `com.google.genai:google-genai` atrás da porta `TransactionExtractor`; decidir e registrar a versão antes de adicionar a dependência. Spring AI é alternativa se surgir benefício concreto.
- Enviar áudio diretamente ao modelo, com saída estruturada e rascunho sem persistência. Não guardar nem logar áudio, transcrição ou conteúdo financeiro; verificar também arquivos temporários do multipart.
- Contexto mínimo de categorias, contas e cartões ativos do próprio usuário, com referências temporárias mapeadas pelo backend. Sem histórico financeiro ou saldos.
- Validar toda saída como entrada não confiável: dinheiro em `BigDecimal`, datas via `Clock`, propriedade e estado dos recursos, tipo, XOR conta/cartão e parcelas. Revalidar tudo na criação efetiva.
- Campos ausentes ou ambíguos devem aparecer como pendências; não inventar conta/cartão/categoria. Detectar múltiplos lançamentos e pedir separação, sem descartar parte da gravação silenciosamente.
- Cota persistente por usuário e proteção global do projeto, com reserva atômica e tratamento de repetição/concorrência. Não manter transação de banco aberta durante a chamada externa.
- Cota do FIN usa `America/Sao_Paulo`; os limites e a janela do provedor são independentes. Ao esgotar capacidade, oferecer preenchimento manual, sem troca automática para serviço pago.
- Hipóteses para avaliação, ainda não limites comerciais: até 30 segundos por áudio e 10 solicitações por usuário/dia. Ajustar ao limite real do projeto e aos resultados do piloto.
- Adiar `transactions.source`, schema de assinaturas e escolha do gateway. O contrato de rascunho, erros e extensão de `/users/me` será definido antes da implementação, em `docs/api-spec.md`.

### Fases da V2

Todas as fases têm **Gabriel** como responsável. Executar uma tarefa por vez.

| Fase | Entrega | Critério de conclusão | Status |
|---|---|---|---|
| 0 | Documentação e base | Decisões registradas, responsável/status corrigidos, compilação e testes verificados | [x] |
| 1 | Prova técnica gratuita | SDK/modelo/formatos validados com dados fictícios; precisão, latência e consumo medidos | [~] |
| 2 | Áudio para rascunho | Extração, validação e confirmação verificadas em ambiente de teste; sem escrita financeira pelo modelo | [ ] |
| 3 | PRO e cotas | Acesso manual com validade, bloqueio de BASIC antes da chamada e cotas resistentes à concorrência | [ ] |
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
- [x] Modelo inicial escolhido: `gemini-3.1-flash-lite`; áudio WAV e saída estruturada validados com SDK 1.79.0. Os formatos dos navegadores ainda precisam de teste.
- [x] `.env` está ignorado pelo Git (checagem de caminho, sem ler o conteúdo).
- [x] Disponibilizar `GEMINI_API_KEY` no processo que executará a prova. Presença confirmada sem imprimir o valor e sem ler `.env`.
- [x] Preparar e executar a prova optativa com SDK oficial `google-genai:1.79.0`, em escopo `test`, sem chamadas externas nos testes normais.
- [x] Preparar captura no navegador com download/compartilhamento dos bytes originais e relatório, disponível em localhost e HTTPS temporário para o iPhone 13 de Gabriel.
- [x] Validar gravação/links/liberação do microfone em Chrome isolado com dispositivo fictício, nos formatos WebM/Opus e MP4/AAC; servidor estático com rotas fixas e sem upload.
- [x] Validar extração dos dois arquivos originais fornecidos por Gabriel: WebM/Opus e MP4/AAC, com a frase fictícia da página. Ambos corresponderam integralmente ao esperado. Relatórios de captura/navegador ainda não recebidos.
- [ ] Completar amostra de avaliação, métricas e validações técnicas pendentes antes de concluir a fase.

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
# WAV PCM até 30 s; WebM/Opus e MP4/AAC têm suporte experimental (duração ainda manual).
& "$env:JAVA_HOME\bin\java.exe" -cp $probeClasspath com.finance.ai.poc.GeminiVoiceProbe --free-project-confirmed 'C:\caminho\audio-ficticio.wav'
```

O indicador `correspondeAmostraFixa` só verifica a frase fictícia descrita acima; `false` para outra frase não significa erro de extração. A medição por outros casos e os formatos dos navegadores serão adicionados nas próximas tarefas da fase 1. O programa não imprime valores extraídos ou corpo da resposta. Em falhas exibe apenas classe da exceção ou código HTTP, sem mensagens brutas.

### Captura de Chrome e Safari — ferramenta pronta

- Gabriel confirmou acesso ao Safari no iPhone 13.
- Página de teste em `src/test/resources/ai-poc/recorder.html` + `recorder.js`, servida com `node tools/ai-poc/serve-recorder.mjs` em **http://localhost:8765**. Não integra o frontend do produto e não altera a API REST.
- Para iPhone, usar HTTPS confiável. Nesta sessão foi iniciado um Cloudflare Quick Tunnel somente para o servidor estático, com executável oficial 2026.11.0 em `build/ai-poc-tools` e SHA-256 conferido contra o digest da release oficial. Não houve instalação global, criação de conta ou inclusão de dependência no Maven. O endereço é temporário, será informado na conversa e deixa de funcionar ao encerrar o túnel/computador.
- Para recriar o HTTPS com o binário local desta sessão, em outro terminal: `.\build\ai-poc-tools\cloudflared.exe tunnel --config .\build\ai-poc-tools\tunnel-config.yml --no-autoupdate --url http://127.0.0.1:8765 --protocol http2`. O arquivo de configuração é vazio (sem credenciais). Só o HTML e seu JS são expostos, nunca o diretório do projeto ou a API. Documentação: [Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).
- A página usa `MediaRecorder`, formato padrão nativo ou opção suportada escolhida, para preservar o arquivo original; encerra em 29 s e ao ocultar/sair da página. Salva arquivo `.webm` ou `.m4a` conforme o MIME real e relatório JSON de formato, bytes, duração medida pelo relógio da captura e navegador. Não solicita chave nem faz upload, analytics ou chamada à IA.
- **Chrome automatizado:** captura original de WebM/Opus (22.225 bytes) e MP4/AAC (33.206 bytes) com dispositivo fictício passou, incluindo segunda gravação, links, ausência de exceções JS e todas as tracks encerradas. Verificados também CSP, recusa de POST (405) e acesso negado a `.env`, POM e recursos do backend (404). URL HTTPS respondeu 200 com página esperada e CSP. Não equivale a teste de Safari.
- **Reconhecimento Java experimental:** detecta WAV, WebM/Opus e MP4/AAC pelos bytes, sem depender da extensão; mantém o arquivo original e seleciona `audio/wav`, `audio/webm` ou `audio/mp4`. Até 2 MiB. WebM/MP4 são reconhecidos por marcadores, não por um parser/decoder completo: duração, integridade, codecs/tracks completos e conteúdo malicioso permanecem pendentes antes de qualquer endpoint público. Para a captura manual, usar a página e uma frase curta, até 30 s.
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

### Critérios restantes da prova

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
