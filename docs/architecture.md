# Arquitetura do backend

Arquitetura em **camadas**, organizada **por feature**. Cada pessoa trabalha na sua pasta e o conflito de merge cai muito.

> O pacote base real (`<base-package>` abaixo) é o da classe com `@SpringBootApplication`. Confira lá: o `groupId` do `pom.xml` tem hífen (`com.finance-platform`), então o Initializr pode ter gerado algo como `com.finance`. Não presuma.

## 1. Estrutura de pacotes

```
<base-package>
├── common/          # Subpacotes: entity/ (BaseEntity), enums/, exception/ (GlobalExceptionHandler, exceptions), config/ (Clock, Jpa auditing)
├── security/        # SecurityConfig, JwtService (emitir/validar), GoogleTokenVerifier, CurrentUser
├── auth/            # AuthController, AuthService, dto
├── user/            # User, UserRepository, UserService, UserController
├── account/         # Account + camadas + dto
├── category/        # Category + camadas + dto
├── transaction/     # Transaction + camadas + dto (inclui parcelamento)
├── transfer/        # Transfer + camadas + dto
├── creditcard/      # CreditCard, InvoicePayment, InvoiceService, controller, dto
├── goal/            # Goal, GoalContribution + camadas + dto
├── dashboard/       # DashboardController, DashboardService, PeriodResolver, dto (sem entity)
└── simulation/      # SimulationController, SimulationService, dto (sem entity)
```

Dentro de cada feature: `XController`, `XService`, `XRepository`, `X` (entity) e um subpacote `dto` com `XRequest`/`XResponse`.

## 2. Responsabilidade de cada camada

| Camada | Pode | Não pode |
|---|---|---|
| **Controller** | Receber HTTP, `@Valid` no body, chamar o service, montar o status HTTP | Ter regra de negócio, acessar repository, devolver entidade |
| **Service** | Aplicar regras, orquestrar repositories, `@Transactional`, lançar exceções de negócio | Conhecer `HttpServletRequest` ou detalhes HTTP |
| **Repository** | Queries (Spring Data, JPQL, projeções) | Ter regra de negócio |
| **Entity** | Mapear tabela e invariantes simples | Ser serializada na API |

### DTOs

- `record` para Request e Response. Bean Validation nos Requests (`@NotNull`, `@Positive`, `@Size`, `@Pattern`...).
- Conversão entidade ↔ DTO com **mapper manual** (método estático ou classe `XMapper` na própria feature). MapStruct só se o time decidir adotar.
- Mensagens de validação em português: `@Positive(message = "deve ser maior que zero")`.

### Transações

- `@Transactional` no **service**, nunca no controller.
- Leituras: `@Transactional(readOnly = true)`.
- Operações que alteram mais de uma tabela (ex.: criar parcelas, contribuição de meta, pagamento de fatura) ficam numa **única** transação.

## 3. Segurança

### Fluxo de autenticação

1. O front faz o login com o Google e recebe um **ID Token**.
2. `POST /api/v1/auth/google` recebe `{ idToken }`. O `GoogleTokenVerifier` (`google-api-client`) valida assinatura, `aud` (Client ID), `iss`, expiração e `email_verified`.
3. O `AuthService` busca o usuário por `googleId`; se não existir, cria o `User` **e os dados iniciais** (categorias padrão + conta "Carteira"). Se existir, atualiza nome e foto.
4. Devolve **access token** (JWT, 15 min) e **refresh token** (7 dias, uso único com rotation, só o **hash** vai ao banco).

### JWT próprio

- Emitir com `NimbusJwtEncoder`; validar com o suporte de **Resource Server** do Spring Security (`JwtDecoder`). Sem filtro customizado.
- Claim `sub` = **id interno** do usuário (não o `sub` do Google).
- Chave (HS256) vem de `JWT_SECRET`. Mínimo de 32 bytes.

### Pegando o usuário logado

Uma única classe `CurrentUser` em `security/` lê o id do `SecurityContext`. **Services recebem o `userId` como parâmetro** (o controller o obtém de `CurrentUser`), o que facilita testar.

```java
// padrão do controller
@GetMapping("/{id}")
public AccountResponse get(@PathVariable Long id) {
    return accountService.get(currentUser.id(), id);
}

// padrão do service
public AccountResponse get(Long userId, Long id) {
    return accountRepository.findByIdAndUserId(id, userId)
            .map(AccountMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
}
```

### Regras de acesso

- Públicas: `/api/v1/auth/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` (Swagger só no perfil `dev`).
- Todo o resto exige JWT válido.
- CORS liberado **somente** para `CORS_ORIGINS` (lista de origens do front).
- Sessão stateless, CSRF desabilitado (API com Bearer token).

## 4. Isolamento entre usuários

1. `userId` nunca vem do payload, da query ou da URL.
2. Todo repository de dado do usuário expõe métodos com `userId`: `findByIdAndUserId`, `findAllByUserId`, `existsByIdAndUserId`.
3. Ao receber `categoryId`, `accountId`, `creditCardId` etc. no payload, **buscar com `userId`** para provar que pertence ao usuário.
4. Recurso de outro usuário = **404**.
5. Um teste de integração de isolamento por feature (usuário A tenta ler, editar e excluir dado do B).

## 5. Tratamento de erros

`GlobalExceptionHandler` (`@RestControllerAdvice`) converte exceções em `ProblemDetail`:

| Exceção | Status |
|---|---|
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 (com lista `errors[]` de campo e mensagem) |
| `AuthenticationException` / token inválido | 401 |
| `ResourceNotFoundException` | 404 |
| `ConflictException` (ex.: nome duplicado) | 409 |
| `BusinessRuleException` (regra de negócio violada) | 422 |
| `Exception` inesperada | 500, mensagem genérica, **detalhe só no log** |

Formato:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "amount must be greater than 0",
  "errors": [{ "field": "amount", "message": "deve ser maior que zero" }]
}
```

Nunca devolver stack trace, SQL nem dados de outro usuário em mensagem de erro.

## 6. Persistência

- `BaseEntity` (`@MappedSuperclass`) com `createdAt`/`updatedAt` via auditing (`@EnableJpaAuditing`), tipo `Instant`.
- `@ManyToOne(fetch = LAZY)` sempre. Cuidado com N+1: use `JOIN FETCH` ou projeção quando listar com relacionamento.
- Enums com `@Enumerated(EnumType.STRING)`.
- Dinheiro: `@Column(precision = 19, scale = 2)` e `BigDecimal`.
- Soft delete (`deleted = true`) em `transaction` e `category`; **arquivar** (`archived = true`) em `account` e `credit_card`. Queries de leitura sempre filtram os removidos/arquivados quando fizer sentido.
- Dashboard: queries agregadas com `@Query` e projeções (interface ou record), nunca carregando entidades.

### Flyway

- Arquivos em `src/main/resources/db/migration`, nome `V<n>__descricao.sql`.
- `V1__init.sql` cria todas as tabelas, índices e `CHECK`.
- Nunca editar migration já aplicada; sempre criar `V<n+1>`.
- Em produção: `spring.jpa.hibernate.ddl-auto=validate`.

## 7. Tempo e fuso

- Registrar um bean `Clock` (`Clock.system(ZoneId.of("America/Sao_Paulo"))`) e **injetá-lo** onde precisar de "hoje". Nada de `LocalDate.now()` solto nos services (impede teste).
- Banco e instantes em UTC (`hibernate.jdbc.time_zone=UTC`).

## 8. Configuração

- Perfis `dev` e `prod` (`application-dev.yml`, `application-prod.yml`).
- Variáveis de ambiente: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `CORS_ORIGINS`.
- Railway injeta `PORT`: `server.port=${PORT:8080}`.
- Health check em `/actuator/health`.

## 9. Documentação da API

- springdoc gera o OpenAPI em `/v3/api-docs` e a UI em `/swagger-ui.html`.
- Anotar controllers com `@Tag` e endpoints com `@Operation`. O front gera os tipos TypeScript a partir desse JSON, então **o contrato precisa estar fiel**.

## 10. Testes

| Tipo | Ferramenta | O que cobre |
|---|---|---|
| Unitário | JUnit 5 + Mockito | Regras de service (cálculo de fatura, parcelas, `PeriodResolver`, simulações) |
| Repository/integração | `@DataJpaTest` ou `@SpringBootTest` + **Testcontainers (MySQL)** | Queries agregadas, `CHECK`, constraints e índices |
| Web | `@SpringBootTest` + `MockMvc` + `spring-security-test` | Status HTTP, validação, autorização, isolamento entre usuários |

- **Não usar H2**: as agregações e os `CHECK` se comportam diferente do MySQL.
- Todo teste de web que exige login usa um JWT de teste com `sub` = id do usuário criado no teste.

## 11. Atenção: Spring Boot 4

- Os starters foram modularizados e **renomeados** (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-security-oauth2-resource-server`...). Siga os nomes que já estão no `pom.xml`.
- Pacotes e módulos de algumas bibliotecas mudaram em relação ao Boot 3 (Spring Framework 7, Spring Security 7, Hibernate 7). Em caso de dúvida num import ou numa API, **consulte a documentação oficial ou o código que já compila no projeto** em vez de assumir o padrão do Boot 3.

## 12. V2 / fase 1 — prova manual de voz

- `src/test/java/com/finance/ai/poc/GeminiVoiceProbe.java`: programa manual, sem contexto Spring, endpoint da API ou banco. Aceita texto fictício fixo, WAV PCM, WebM/Opus ou MP4/AAC com duração positiva de até 30 s e até 2 MiB. Nos comprimidos, marcadores apenas selecionam o validador; a aceitação exige decodificação local com `MediaDurationProbe` antes de criar o cliente Google. Preserva os bytes originais. Não é a implementação da feature de rascunho.
- `GeminiVoiceProbeTest` no mesmo pacote: testes offline de arquivo, saída estruturada e configuração; nenhum teste automático chama o Google.
- SDK oficial `com.google.genai:google-genai:1.79.0`, apenas em escopo `test`. JSON da prova é um contrato experimental interno, sem alteração da API REST.
- Autenticação exclusivamente com `GEMINI_API_KEY` do processo; não ler `.env`. Modelo fixo inicial `gemini-3.1-flash-lite`, backend Gemini Developer API explícito, timeout de 30 s, uma tentativa, sem tools/continuação automática e thinking `MINIMAL`.
- Uma execução manual faz no máximo uma chamada. Não imprimir prompts, áudio, chave, corpo da resposta ou mensagens brutas de exceção; somente métricas e campos presentes. Exigir opção explícita que confirma projeto gratuito antes da chamada; essa opção não consulta nem altera faturamento.
- Validar JSON, dinheiro exato e referências do catálogo fictício depois da extração. Isso não substitui as regras da criação real. O Safari no iPhone 13 de Gabriel será validado com gravações fictícias originais.
- `src/test/resources/ai-poc/recorder.html` e `recorder.js`: fixture manual de captura com `MediaRecorder`, sem upload, chave ou dependência externa. Permite formato nativo/selecionado, prévia, download original e compartilhamento do par áudio + relatório quando suportado; relatório JSON informa nome do áudio, MIME real, bytes, relógio da captura e user agent. Se o navegador não compartilhar os dois juntos, os links individuais permanecem disponíveis com instrução para salvar ambos. Interrompe a gravação em 29 s e ao sair da página, liberando o microfone. A medição e o user agent do navegador são evidências declaradas pelo cliente, não prova da duração do contêiner ou autenticação da origem.
- `tools/ai-poc/serve-recorder.mjs`: servidor Node com módulos nativos, ligado somente a `127.0.0.1:8765`, servindo exatamente `/` e `/recorder.js`. Métodos de escrita são recusados e não existe leitura de caminhos fornecidos pelo cliente. Não serve o repositório, áudio ou backend REST. É ferramenta da prova, não frontend do produto.
- Para Safari, pode ser usado um Cloudflare Quick Tunnel temporário apontando apenas para esse servidor estático, oferecendo HTTPS confiável. Não expor a porta da API. O túnel não recebe gravações e não habilita cobrança de IA; só serve a página e seu script. Binário local/configuração vazia/logs de execução ficam em `build/ai-poc-tools`, ignorado pelo Git.
- Avaliação sintética: `src/test/resources/ai-poc/evaluation-cases.json` contém 50 frases fictícias e resultados esperados, com data de contexto fixa e casos claros, incompletos, múltiplos, sem transação e com prompt injection. É um corpus de desenvolvimento, não um conjunto independente de produção.
- `tools/ai-poc/generate-evaluation-audio.ps1` gera WAVs offline com Microsoft Maria Desktop e manifesto de hashes em `build/ai-poc-evaluation/audio`; não usa gravações humanas nem lê `.env`.
- `GeminiVoiceEvaluation` em `src/test/java/com/finance/ai/poc` executa o corpus de forma manual, confirmando manifesto/hashes antes das chamadas. Reutiliza schema, validação e prompt da prova, mantendo data fixa do corpus. Até 50 chamadas, uma tentativa por caso, intervalo mínimo de 6 s entre inícios; interrompe em 400/401/403/404/429 ou após 5 falhas consecutivas. Sem troca de modelo ou retry automático.
- Resultados por caso e resumo ficam em `build/ai-poc-evaluation/results-<UTC>/`, sem texto do prompt/áudio/resposta: IDs, campos divergentes, erros, latência e contagens de tokens quando informadas. Erros contam como falha na métrica total e ficam separados da precisão entre respostas validadas; casos não executados são contados como pendentes. p50/p95 da chamada são medidos separadamente da espera de limitação de taxa. `GeminiVoiceEvaluationTest` verifica corpus, comparação e métricas offline.
- Regressão de múltiplas transações: `evaluation-holdout.json` no mesmo diretório de fixtures define 20 casos inéditos, congelados antes de testar o prompt v3 (8 múltiplos, 6 claros incluindo parcelas/correção/compra com vários itens, 4 incompletos, 2 injeções). Esse conjunto é reservado para uma primeira verificação; depois de inspecionados os resultados deixa de ser independente para ajustes futuros. `--holdout` seleciona somente esse recurso permitido; o gerador aceita `-Corpus holdout`. Casos antigos e seus oráculos permanecem iguais.
- A prova `voice-poc-v4-events` muda apenas o JSON interno do modelo para `{ "events": [...] }`, até 3 eventos de 7 campos cada. O Java (`parseExtraction`) determina: nenhum evento = `UNCLEAR`, um = `SINGLE` com validação original, dois ou mais = `MULTIPLE`, descartando todos os campos financeiros na resposta normalizada. Não depende de um status escrito pelo modelo nem soma/escolhe um dos eventos. Continua dependendo de o modelo enumerar corretamente os eventos; a revisão humana permanece obrigatória. O contrato REST não é alterado e os oráculos planos de 8 campos são preservados para comparação entre versões.
- `evaluation-unseen.json` reserva 10 novas frases (5 múltiplas, 3 claras de contraste, 1 parcial e 1 injeção com dois eventos) antes da primeira chamada v4. CLI `--unseen`, gerador `-Corpus unseen`; recursos permitidos são explícitos. Não reusar os resultados inspecionados como evidência independente em ajustes posteriores.
- Testes offline de HTTP 429, timeout e parada usam `HttpServer` do JDK em loopback e credencial fictícia, verificando uma tentativa e ausência de corpo bruto nos relatórios. Não chamam Google nem usam a chave real.
- Política aprovada `account-installments-v1`: após validar um único evento, `parseExtraction` define parcelas 1 somente quando há conta válida e parcelas null. Cartão ou origem não resolvida mantêm null. Nunca corrigir parcelas explicitamente inválidas nem normalizar múltiplos. O prompt e o schema do modelo permanecem v4; a política de normalização tem versão própria nos relatórios.
- Os oráculos originais são imutáveis: o avaliador deriva em uma cópia o resultado esperado sob a política aprovada e informa `normalization` e `effectiveOracleChanges`. A expectativa efetiva muda somente no caso c35 do corpus atual. Relatórios anteriores não são reescritos nem apresentados como reexecuções bem-sucedidas.
- Validação local de duração dos comprimidos: `MediaDurationProbe` e `MediaDurationProbeTest`, no pacote de testes da PoC, usam **ffprobe 9.0.2** para decodificar frames de WebM/Opus e MP4/AAC. Sem biblioteca Maven nova, transcodificação do arquivo enviado, upload de validação ou cópia temporária do áudio. `FIN_FFPROBE_PATH` deve apontar para executável absoluto (propriedade de teste `fin.poc.ffprobe-path` também aceita). A ferramenta precisa estar provisionada no ambiente antes de usar comprimidos; não baixar binário em tempo de requisição.
- O processo recebe os bytes já limitados a 2 MiB por stdin (`pipe:0`), sem shell e sem herdar credenciais, com demuxers/codecs/protocolos permitidos, uma thread, limite de alocação, timeout de 5 s e saídas limitadas a 4 MiB/16 KiB. Metadados e stderr nunca são logados. Processo é encerrado em erro/timeout/saída excessiva; falhar fechado se ferramenta faltar ou a duração não puder ser comprovada.
- Duração adotada: maior entre amostras decodificadas, intervalo dos timestamps dos frames e duração declarada disponível, com aritmética decimal e limite **positivo e <= 30 s**. Não confiar só no duration do cabeçalho nem no relatório do navegador. Uma única trilha de áudio, formato/codec esperado, frames completos com timestamps e amostras válidos; rejeitar outras trilhas, regressão de timestamps ou erro de decodificação. A regra é conservadora: padding decodificado também conta no limite. Isso não é um sandbox de sistema operacional nem prova de ausência de toda corrupção possível.
- `GeminiVoiceProbe --inspect-audio <arquivo>` executa somente a validação local, sem ler a chave ou chamar Google, e exibe MIME, bytes e duração. Arquivos MP4 que exijam seek incompatível com pipe são recusados; os dois arquivos originais fornecidos por Gabriel foram aceitos em 14/11/2026 (WebM 10,74 s; MP4 9,769333334 s). ffmpeg do mesmo pacote é usado somente para gerar fixtures sintéticas de fronteira, não para alterar os áudios enviados.
