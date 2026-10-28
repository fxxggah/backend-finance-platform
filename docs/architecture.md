# Arquitetura do backend

Arquitetura em **camadas**, organizada **por feature**. Cada pessoa trabalha na sua pasta e o conflito de merge cai muito.

> O pacote base real (`<base-package>` abaixo) é o da classe com `@SpringBootApplication`. Confira lá: o `groupId` do `pom.xml` tem hífen (`com.finance-app`), então o Initializr pode ter gerado algo como `com.finance_app.finance_app`. Não presuma.

## 1. Estrutura de pacotes

```
<base-package>
├── common/          # BaseEntity, enums compartilhados, exceptions, GlobalExceptionHandler, configs gerais (Clock, Jpa auditing)
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
