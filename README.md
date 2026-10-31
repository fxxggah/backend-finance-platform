# Planejamento do Backend: Gestão Financeira Pessoal

> Documento de referência para **Gabriel** e **Breno** (backend). O **João** (frontend) usa as seções 6, 10 e 11.
> Stack: Java + Spring Boot (REST) · MySQL · Flyway · Next.js/TypeScript/Tailwind (front) · Railway (deploy).
> Arquitetura: **camadas (controller → service → repository → entity), organizadas por feature**.

---

## 0. Escopo da V1

| Módulo | O que entrega |
|---|---|
| Autenticação | Login com Google, JWT próprio, refresh token |
| Contas | Várias contas por usuário (Nubank, Mercado Pago, Dinheiro...) |
| Categorias | Personalizáveis, com cor e ícone |
| Transações | Receitas e despesas, em conta **ou** em cartão de crédito, com ou sem parcelamento |
| Transferências | Entre contas do próprio usuário |
| Cartão de crédito | Limite, fatura calculada, parcelas, pagamento de fatura |
| Dashboard | Filtro por dia, semana, quinzena, mês, trimestre, semestre, ano e período customizado |
| Metas | Três tipos: economia (objetivo), ganho e gasto |
| Simulações | Projeção de investimento e quitação de dívida |

---

## 1. Decisões de arquitetura

| Decisão | Escolha | Motivo |
|---|---|---|
| Dinheiro | `BigDecimal` + `DECIMAL(19,2)` | Nunca `double`/`float` em valores financeiros |
| Chave primária | `BIGINT AUTO_INCREMENT` | Simples e rápido no MySQL |
| Autenticação | ID Token do Google + **JWT próprio** | API stateless, sem redirects server-side |
| Multi-tenancy | Coluna `user_id` em toda tabela de dados |
| Migrations | **Flyway** | Schema versionado no Git, essencial com duas pessoas |
| Exclusão | Soft delete em `transaction` e `category`; **arquivar** em `account` e `credit_card` |
| Saldos, faturas e progresso de metas | **Sempre calculados**, nunca gravados (exceto `currentAmount` de meta `SAVINGS`) | Evita dessincronização ao editar/excluir |
| Contagem de despesas | **Competência**: despesa de cartão conta na data da compra/parcela | Pagar fatura não é despesa |
| Ícones | Backend guarda uma **chave de texto**; o front mapeia para o ícone |
| Fuso | `America/Sao_Paulo` para "hoje"/"mês atual"; instantes em UTC | Evita erro de virada de mês |

### Estrutura de pacotes

```
com.seuprojeto.finance
├── common/          # BaseEntity, exceptions, GlobalExceptionHandler, enums compartilhados
├── security/        # SecurityConfig, JwtService, GoogleTokenVerifier
├── auth/            # controller, service, dto
├── user/            # entity, repository, service, controller
├── account/         # entity, repository, service, controller, dto
├── category/        # idem
├── transaction/     # idem (inclui lógica de parcelas)
├── transfer/        # idem
├── creditcard/      # CreditCard, InvoicePayment, InvoiceService, controller, dto
├── goal/            # Goal, GoalContribution, idem
├── dashboard/       # controller, service, PeriodResolver, dto (sem entity)
└── simulation/      # controller, service, dto (sem entity)
```

Dentro de cada feature, as camadas de sempre. Cada um trabalha na sua pasta e o conflito de merge cai muito.

---

## 2. Autenticação (login com Google)

### Fluxo

```
Next.js ──(login Google)──► recebe ID Token
Next.js ──POST /api/v1/auth/google {idToken}──► Backend
Backend valida o token (GoogleIdTokenVerifier)
Backend cria/atualiza o usuário
Backend ◄── devolve { accessToken (15 min), refreshToken (7 dias), user }
Chamadas seguintes: Authorization: Bearer <accessToken>
```

### Regras

- Validar do ID Token: assinatura, `aud` (seu Client ID), `iss`, expiração e `email_verified = true`.
- Primeiro login cria o `User` **e os dados iniciais**: categorias padrão e a conta **"Carteira"** (`CASH`).
- Logins seguintes atualizam nome e foto; a busca é por `googleId` (o `sub` do Google).
- Refresh token é **de uso único** (rotation): a cada refresh o antigo é revogado e um novo é emitido.
- Guardar só o **hash** do refresh token no banco.
- Logout revoga o refresh token.
- `User.active = false` não autentica.
- JWT: gerar com `NimbusJwtEncoder`, validar com `oauth2-resource-server` (sem filtro customizado).

### Categorias padrão (seed)

Despesa: Alimentação, Transporte, Moradia, Saúde, Educação, Lazer, Compras, Outros.
Receita: Salário, Freelance, Investimentos, Outros.

---

## 3. Modelagem de dados

### 3.1 Relacionamentos

```
User 1──N Account
User 1──N CreditCard
User 1──N Category
User 1──N Transaction ──N──1 Category
Transaction N──1 Account
Transaction N──1 CreditCard
User 1──N Transfer ──► fromAccount / toAccount (Account)
CreditCard 1──N InvoicePayment ──N──1 Account
User 1──N Goal 1──N GoalContribution
Goal N──1 Category (opcional, só INCOME/EXPENSE)
User 1──N RefreshToken
```

Todas as entidades herdam de `BaseEntity` (`createdAt`, `updatedAt`, com `@EnableJpaAuditing`).

### 3.2 Enums

```java
enum TransactionType { INCOME, EXPENSE }
enum AccountType     { BANK, CASH, DIGITAL_WALLET, OTHER }
enum GoalType        { SAVINGS, INCOME, EXPENSE }
enum GoalStatus      { ACTIVE, CANCELLED, COMPLETED }  // persistido
enum GoalState       { IN_PROGRESS, WARNING, EXCEEDED, ACHIEVED, EXPIRED } // calculado
enum InvoiceStatus   { OPEN, CLOSED, PARTIALLY_PAID, PAID, OVERDUE }        // calculado
enum Period          { DAY, WEEK, FORTNIGHT, MONTH, QUARTER, SEMESTER, YEAR, CUSTOM }
```

Sempre `@Enumerated(EnumType.STRING)`.

### 3.3 Entidades

#### `User`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `googleId` | `String` | unique, not null |
| `email` | `String` | unique, not null |
| `name` | `String` | not null |
| `pictureUrl` | `String` | |
| `active` | `boolean` | default true |

#### `RefreshToken`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | |
| `tokenHash` | `String` | hash, nunca o token puro |
| `expiresAt` | `Instant` | |
| `revoked` | `boolean` | |

#### `Account`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | not null |
| `name` | `String(60)` | not null |
| `type` | `AccountType` | not null |
| `initialBalance` | `BigDecimal(19,2)` | default 0 |
| `color` | `String(7)` | hex |
| `icon` | `String(40)` | chave do ícone |
| `archived` | `boolean` | default false |
| | | **unique (`user_id`, `name`)** |

**Saldo (calculado):**

```
saldo = initialBalance
      + receitas da conta
      + transferências recebidas
      − despesas da conta
      − transferências enviadas
      − pagamentos de fatura feitos a partir da conta
```

#### `Category`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | not null |
| `name` | `String(60)` | not null |
| `type` | `TransactionType` | not null |
| `color` | `String(7)` | hex |
| `icon` | `String(40)` | chave do ícone |
| `deleted` | `boolean` | soft delete |
| | | **unique (`user_id`, `name`, `type`)** |

#### `CreditCard`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | not null |
| `name` | `String(60)` | not null |
| `creditLimit` | `BigDecimal(19,2)` | > 0 |
| `closingDay` | `int` | 1 a 28 |
| `dueDay` | `int` | 1 a 28 |
| `color` / `icon` | `String` | |
| `archived` | `boolean` | |
| | | **unique (`user_id`, `name`)** |

> Dias limitados a 1–28 para fugir de fevereiro e meses de 30 dias.

#### `Transaction`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | not null |
| `account` | `@ManyToOne(LAZY)` | nullable (XOR com `creditCard`) |
| `creditCard` | `@ManyToOne(LAZY)` | nullable (XOR com `account`) |
| `category` | `@ManyToOne(LAZY)` | not null |
| `type` | `TransactionType` | |
| `amount` | `BigDecimal(19,2)` | sempre positivo; o sinal vem do `type` |
| `date` | `LocalDate` | |
| `description` | `String(255)` | |
| `notes` | `String(500)` | opcional |
| `invoiceReference` | `LocalDate` | dia 1 do mês da fatura; só para compra no cartão |
| `installmentGroupId` | `String(36)` | UUID; agrupa as parcelas |
| `installmentNumber` | `Integer` | ex.: 2 |
| `installmentTotal` | `Integer` | ex.: 3 |
| `deleted` | `boolean` | soft delete |

Constraints no banco (MySQL 8.0.16+):

```sql
CHECK ((account_id IS NULL) <> (credit_card_id IS NULL)),
CHECK (credit_card_id IS NULL OR type = 'EXPENSE')
```

Índices: `(user_id, date)`, `(user_id, account_id, date)`, `(user_id, type, date)`, `(user_id, category_id)`, `(user_id, credit_card_id, invoice_reference)`.

#### `Transfer`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | |
| `fromAccount` / `toAccount` | `@ManyToOne(LAZY)` | not null |
| `amount` | `BigDecimal(19,2)` | > 0 |
| `date` | `LocalDate` | |
| `description` | `String(255)` | opcional |

`CHECK (from_account_id <> to_account_id)`.

#### `InvoicePayment`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | |
| `creditCard` | `@ManyToOne(LAZY)` | |
| `account` | `@ManyToOne(LAZY)` | conta de onde saiu o dinheiro |
| `invoiceReference` | `LocalDate` | dia 1 do mês da fatura |
| `amount` | `BigDecimal(19,2)` | > 0 |
| `date` | `LocalDate` | |

#### `Goal`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `user` | `@ManyToOne(LAZY)` | |
| `type` | `GoalType` | `SAVINGS`, `INCOME`, `EXPENSE` |
| `name` | `String(100)` | |
| `description` | `String(255)` | opcional |
| `icon` / `color` | `String` | opcionais; padrão `target` |
| `targetAmount` | `BigDecimal(19,2)` | > 0; para `EXPENSE` é o **limite** |
| `currentAmount` | `BigDecimal(19,2)` | **só `SAVINGS`**; soma das contribuições |
| `category` | `@ManyToOne(LAZY)`, nullable | só `INCOME`/`EXPENSE`; sem categoria = meta global |
| `startDate` | `LocalDate` | obrigatório em `INCOME`/`EXPENSE` |
| `endDate` | `LocalDate` | obrigatório em `INCOME`/`EXPENSE`; em `SAVINGS` é o **prazo opcional** |
| `status` | `GoalStatus` | `ACTIVE`/`CANCELLED` |
| `contributions` | `@OneToMany(mappedBy="goal", cascade=ALL)` | só `SAVINGS` |

#### `GoalContribution`
| Campo | Tipo | Observação |
|---|---|---|
| `id` | `Long` | PK |
| `goal` | `@ManyToOne(LAZY)` | |
| `amount` | `BigDecimal(19,2)` | positivo = depósito, negativo = retirada; ≠ 0 |
| `date` | `LocalDate` | |
| `notes` | `String(255)` | |

### 3.4 Esqueleto de exemplo

```java
@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor
public class Transaction extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id")
    private CreditCard creditCard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    private LocalDate invoiceReference;
    private String installmentGroupId;
    private Integer installmentNumber;
    private Integer installmentTotal;
    private String description;
    private boolean deleted = false;
}
```

> **Não use `@Data` do Lombok em entidades JPA.** Use `@Getter/@Setter` e implemente `equals/hashCode` só pelo `id`.

### 3.5 Isolamento entre usuários (ponto crítico de segurança)

1. O `userId` **nunca** vem do payload ou da URL: vem sempre do JWT (`SecurityContext`).
2. **Todo** método de repository que busca dados do usuário recebe o `userId`: `findByIdAndUserId`, `findAllByUserId`.
3. Recurso de outro usuário retorna **404** (não 403), para não vazar a existência do ID.
4. Um teste de integração por feature: "usuário A não lê, edita nem exclui dado do usuário B".
5. Ao referenciar outra entidade no payload (`categoryId`, `accountId`, `creditCardId`), validar que ela pertence ao usuário logado.

---

## 4. Regras de negócio

### 4.1 Usuário

- `DELETE /users/me` apaga a conta e **todos** os dados do usuário (LGPD).

### 4.2 Contas

- Nome obrigatório (1–60), único por usuário.
- `initialBalance` pode ser alterado a qualquer momento; só muda o saldo calculado.
- Conta **com** movimentação (transações, transferências ou pagamentos de fatura): só é **arquivada**. Conta vazia: hard delete.
- Conta arquivada não aparece no seletor de nova transação, mas histórico e relatórios continuam.
- Não é possível excluir/arquivar a **última conta ativa** do usuário.
- Saldo negativo é permitido (não bloqueia lançamentos).
- Cor em hex válido; ícone em formato `^[a-z0-9-]{1,40}$`.

### 4.3 Categorias

- Nome de 1 a 60 caracteres, único por usuário dentro do mesmo tipo.
- O tipo **não pode ser alterado** se a categoria já tem transações.
- Excluir categoria **com** transações: soft delete (some do seletor, mas o histórico mantém o nome).
- Excluir categoria **sem** transações: hard delete.
- Não é possível criar transação em categoria excluída.

### 4.4 Transações

- `amount` > 0, máximo 2 casas decimais, teto 999.999.999,99.
- Exatamente **um** entre `accountId` e `creditCardId` (XOR). Cartão só aceita `EXPENSE`.
- O tipo da transação deve ser igual ao tipo da categoria.
- Conta/cartão/categoria devem pertencer ao usuário, não estar arquivados/excluídos.
- `date` obrigatória. Data futura permitida até **1 ano à frente** (exceção: parcelas, até 24 meses).
- Edição e exclusão só do próprio usuário. Exclusão é soft delete e a transação sai de todos os cálculos.
- Listagem **sempre paginada** (máx. 100 por página), ordenada por data decrescente por padrão. Filtros: período, tipo, categoria, conta, cartão, texto.

### 4.5 Transferências

- `fromAccount ≠ toAccount`; ambas do usuário e não arquivadas; `amount` > 0.
- Não gera receita nem despesa: só altera saldos.
- Edição/exclusão recalculam os saldos automaticamente (saldo é calculado).

### 4.6 Cartão de crédito

**Cadastro**
- `closingDay` e `dueDay` entre 1 e 28.
- Alterar `closingDay`/`dueDay` vale **só para compras futuras**: o `invoiceReference` já gravado nunca muda.
- Cartão com compras ou pagamentos: só arquivado. Vazio: hard delete.

**Em qual fatura cai a compra**

```java
LocalDate invoiceReference(LocalDate purchase, int closingDay) {
    LocalDate firstOfMonth = purchase.withDayOfMonth(1);
    return purchase.getDayOfMonth() <= closingDay ? firstOfMonth : firstOfMonth.plusMonths(1);
}

LocalDate closingDate(LocalDate ref, int closingDay) {
    return ref.withDayOfMonth(closingDay);
}

LocalDate dueDate(LocalDate ref, int closingDay, int dueDay) {
    LocalDate base = dueDay > closingDay ? ref : ref.plusMonths(1);
    return base.withDayOfMonth(dueDay);
}
```

Exemplo: fecha dia 25, vence dia 2. Compra em 20/10 → fatura de outubro, vence 02/11. Compra em 26/10 → fatura de novembro, vence 02/12.

**`invoiceReference` é gravado na criação da transação.** Não calcular na leitura.

**Fatura = consulta agregada** por `(cartão, invoiceReference)`. Não existe tabela `Invoice`.

```
total  = SUM(transações do cartão com aquele invoiceReference, deleted = false)
pago   = SUM(InvoicePayment do cartão com aquele invoiceReference)
```

**Status (derivado), avaliar nesta ordem:**

| # | Condição | Status |
|---|---|---|
| 1 | hoje ≤ data de fechamento | `OPEN` |
| 2 | pago ≥ total (ou total = 0) | `PAID` |
| 3 | hoje > vencimento | `OVERDUE` |
| 4 | pago > 0 | `PARTIALLY_PAID` |
| 5 | caso contrário | `CLOSED` |

**Parcelamento**
- Compra de R$ 600 em 3x vira **3 transações** com o mesmo `installmentGroupId`.
- Parcela *n* tem `date = dataDaCompra.plusMonths(n-1)` (o `java.time` ajusta fim de mês sozinho) e `invoiceReference` calculado a partir dessa data.
- Valor: `base = total / n` arredondado para baixo em 2 casas; a **última parcela** recebe o resto (33,33 / 33,33 / 33,34).
- Máximo 24 parcelas. À vista = `installments = 1`.
- Excluir apaga o **grupo inteiro**. Editar categoria/descrição vale para o grupo. **Valor e nº de parcelas não são editáveis**: exclui e recria.

**Pagamento de fatura**
- Só em fatura com status diferente de `OPEN` (o total ainda não é final).
- Aceita pagamento parcial; a soma dos pagamentos não pode passar do total da fatura (422).
- Conta de origem do usuário e não arquivada.
- **Não é despesa**: não entra em receitas/despesas do dashboard. Reduz o saldo da conta.

**Limite**

```
limite usado       = SUM(todas as compras do cartão, incl. parcelas futuras) − SUM(todos os pagamentos)
limite disponível  = creditLimit − limite usado
```

Estourar o limite **não bloqueia**: devolve `availableLimit` negativo e o front avisa.

### 4.7 Dashboard

- Todos os cálculos filtram `deleted = false` e `user_id`.
- **Não entram** em receitas/despesas: transferências e pagamentos de fatura.
- Gastos no cartão entram **na data da compra/parcela**.
- Sem filtro de conta/cartão, consolida tudo. `accountId` = só transações daquela conta. `creditCardId` = só daquele cartão.
- Calcular com **queries agregadas** (`SUM`, `GROUP BY`), nunca carregando entidades em memória.
- Período máximo de consulta: 24 meses.
- Variação percentual sobre período anterior zerado retorna `null` (sem divisão por zero).

**Períodos (resolvidos pelo backend em `PeriodResolver`)**

| Período | Definição |
|---|---|
| `DAY` | O dia de `date` |
| `WEEK` | Segunda a domingo (ISO) |
| `FORTNIGHT` | 1ª: dia 1 ao 15 · 2ª: dia 16 ao último dia do mês |
| `MONTH` | Mês calendário |
| `QUARTER` | jan–mar, abr–jun, jul–set, out–dez |
| `SEMESTER` | jan–jun, jul–dez |
| `YEAR` | Ano calendário |
| `CUSTOM` | `from` e `to` enviados pelo cliente |

O resolver devolve `(from, to)` **e o período anterior equivalente** (para a comparação). A resposta sempre inclui o intervalo resolvido.

**Granularidade do gráfico de evolução** (derivada do tamanho do intervalo):
- até 31 dias: ponto por **dia**
- até ~100 dias: ponto por **semana**
- acima disso: ponto por **mês**

Faça **uma query** agrupada por `date` e `type` e monte os baldes em Java. Preencha com zero os baldes vazios para o gráfico não ter buracos.

### 4.8 Metas

**Regras comuns**
- `targetAmount` > 0. Nome obrigatório (até 100). `icon`/`color` opcionais.
- `status` persistido: `ACTIVE` ou `CANCELLED`. O **estado** (`GoalState`) é calculado na resposta.

**`SAVINGS` (objetivo: entrada de casa, carro, moto, PS5)**
- `endDate` opcional (prazo); se informado, deve ser futuro na criação.
- Progresso via `GoalContribution` (depósito/retirada manual); `currentAmount` gravado e atualizado na **mesma transação de banco** (`@Transactional`).
- Retirada não pode deixar `currentAmount` negativo.
- Aceita contribuição mesmo após atingir 100% (o valor passa de 100%, sem bloqueio). Não aceita em meta `CANCELLED`.
- `monthlyNeeded = (target − current) / meses restantes` (mínimo 1 mês), só se houver `endDate`.
- Contribuição **não** gera despesa nem altera saldo de conta (mundos separados no v1).

**`INCOME` e `EXPENSE` (metas de desempenho)**
- `startDate` e `endDate` obrigatórios, com `endDate >= startDate`.
- `category` opcional; se informada, o tipo da categoria deve bater com o da meta. Sem categoria = global (todas as receitas ou todas as despesas).
- **Não** gravar `currentAmount`: calcular na leitura com a mesma query agregada do dashboard (inclui gastos no cartão).
- `POST /goals/{id}/contributions` retorna **422** para esses tipos.

**Estado calculado**

| Tipo | Regra |
|---|---|
| `EXPENSE` | `< 80%` → `IN_PROGRESS` · `80–100%` → `WARNING` · `> 100%` → `EXCEEDED`. Período encerrado e `≤ 100%` → `ACHIEVED` |
| `INCOME` / `SAVINGS` | `≥ 100%` → `ACHIEVED` · `< 100%` → `IN_PROGRESS` · período/prazo encerrado sem atingir → `EXPIRED` |

### 4.9 Simulações

Sem persistência: validar e calcular. Sempre incluir o aviso *"Simulação ilustrativa, não é recomendação financeira."*

**Projeção de investimento**
- Entradas: `initialAmount` ≥ 0, `monthlyContribution` ≥ 0, `annualRate` 0–100 %, `years` 1–50.
- Taxa mensal composta: `i = (1 + taxaAnual)^(1/12) − 1`.
- `FV = P·(1+i)^n + PMT·[((1+i)^n − 1) / i]`, tratando `i = 0` à parte.
- Retorna total investido, juros, valor final e série mês a mês.
- Usar `BigDecimal` com `MathContext` e `RoundingMode.HALF_EVEN`.

**Quitação de dívida**
- Entradas: `debtAmount` > 0, `monthlyRate` ≥ 0, `monthlyPayment` > 0.
- **Regra crítica:** se `monthlyPayment <= debtAmount × monthlyRate`, a dívida nunca é paga → **422** com mensagem clara (evita loop infinito).
- Limite de segurança de 600 meses no cálculo.
- Retorna meses, total pago, total de juros e tabela de amortização.

---

## 5. Convenções da API

- Prefixo **`/api/v1`**. Todas as rotas, exceto `/auth/*`, exigem `Authorization: Bearer <accessToken>`.
- Datas em ISO-8601 (`2026-10-21`); referência de fatura no formato `yyyy-MM`.
- Valores como número decimal (`1234.56`).
- Paginação: `?page=0&size=20&sort=date,desc`.
- Erros com **`ProblemDetail`** (RFC 7807):

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "amount must be greater than 0",
  "errors": [{ "field": "amount", "message": "deve ser maior que zero" }]
}
```

| Status | Uso |
|---|---|
| `200` | OK (inclui `POST` de simulação) |
| `201` | Recurso criado |
| `204` | Sem conteúdo |
| `400` | Validação de payload |
| `401` | Não autenticado |
| `404` | Não encontrado (inclui recurso de outro usuário) |
| `409` | Conflito (nome duplicado) |
| `422` | Regra de negócio violada |

---

## 6. Endpoints

### 6.1 Auth
| Método | URI | Request | Response |
|---|---|---|---|
| POST | `/auth/google` | `{ idToken }` | `200 { accessToken, refreshToken, expiresIn, user }` |
| POST | `/auth/refresh` | `{ refreshToken }` | `200 { accessToken, refreshToken, expiresIn }` |
| POST | `/auth/logout` | `{ refreshToken }` | `204` |

### 6.2 Usuário
| Método | URI | Response |
|---|---|---|
| GET | `/users/me` | `200 { id, name, email, pictureUrl }` |
| DELETE | `/users/me` | `204` |

### 6.3 Contas
| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/accounts` | n/a | `200 { accounts: [{ id, name, type, balance, color, icon, archived }], totalBalance, creditCardDebt }` |
| POST | `/accounts` | `{ name, type, initialBalance, color, icon }` | `201` |
| PUT | `/accounts/{id}` | `{ name, type, initialBalance, color, icon }` | `200` |
| DELETE | `/accounts/{id}` | n/a | `200 { archived: true/false }` (hard delete se vazia, senão arquiva) |

`creditCardDebt` = soma do que há de fatura em aberto, para o patrimônio líquido.

### 6.4 Categorias
| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/categories?type=EXPENSE` | n/a | `200 [{ id, name, type, color, icon }]` |
| POST | `/categories` | `{ name, type, color, icon }` | `201` |
| PUT | `/categories/{id}` | `{ name, color, icon }` | `200` |
| DELETE | `/categories/{id}` | n/a | `204` |

### 6.5 Transações
| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/transactions?from=&to=&type=&categoryId=&accountId=&creditCardId=&q=&page=&size=` | n/a | `200 { content, page, size, totalElements, totalPages }` |
| GET | `/transactions/{id}` | n/a | `200` |
| POST | `/transactions` | ver abaixo | `201` (lista, se parcelada) |
| PUT | `/transactions/{id}` | mesmo do POST (sem valor/parcelas se for parcelada) | `200` |
| DELETE | `/transactions/{id}` | n/a | `204` (apaga o grupo inteiro se parcelada) |

Request de criação (**`accountId` OU `creditCardId`**, nunca os dois):

```json
{ "type": "EXPENSE", "amount": 600.00, "categoryId": 3, "date": "2026-10-20",
  "description": "Notebook", "notes": null,
  "creditCardId": 2, "installments": 3 }
```

Resposta de uma transação:

```json
{ "id": 42, "type": "EXPENSE", "amount": 200.00, "date": "2026-10-20",
  "description": "Notebook", "notes": null,
  "category": { "id": 3, "name": "Compras", "color": "#FF5733", "icon": "shopping-bag" },
  "account": null,
  "creditCard": { "id": 2, "name": "Nubank" },
  "invoiceReference": "2026-11",
  "installment": { "groupId": "…", "number": 1, "total": 3 } }
```

### 6.6 Transferências
| Método | URI | Request | Response |
|---|---|---|---|
| POST | `/transfers` | `{ fromAccountId, toAccountId, amount, date, description }` | `201` |
| GET | `/transfers?from=&to=&accountId=&page=&size=` | n/a | `200` paginado |
| GET | `/transfers/{id}` | n/a | `200` |
| PUT | `/transfers/{id}` | mesmo do POST | `200` |
| DELETE | `/transfers/{id}` | n/a | `204` |

### 6.7 Cartões de crédito
| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/credit-cards` | n/a | `200 [{ id, name, creditLimit, usedLimit, availableLimit, closingDay, dueDay, color, icon, currentInvoice: { reference, total, status, dueDate } }]` |
| POST | `/credit-cards` | `{ name, creditLimit, closingDay, dueDay, color, icon }` | `201` |
| PUT | `/credit-cards/{id}` | idem | `200` |
| DELETE | `/credit-cards/{id}` | n/a | `200 { archived: true/false }` |
| GET | `/credit-cards/{id}/invoices` | n/a | `200 [{ reference, closingDate, dueDate, total, paid, status }]` |
| GET | `/credit-cards/{id}/invoices/{yyyy-MM}` | n/a | `200 { reference, closingDate, dueDate, total, paid, status, transactions: [...], payments: [...] }` |
| POST | `/credit-cards/{id}/invoices/{yyyy-MM}/payments` | `{ accountId, amount, date }` | `201` |
| DELETE | `/credit-cards/{id}/invoices/{yyyy-MM}/payments/{paymentId}` | n/a | `204` |

### 6.8 Dashboard

Todos aceitam `period`, `date` (referência) e, para `CUSTOM`, `from`/`to`. Filtros opcionais: `accountId`, `creditCardId`.

| Método | URI | Response |
|---|---|---|
| GET | `/dashboard/summary?period=QUARTER&date=2026-10-21` | ver abaixo |
| GET | `/dashboard/expenses-by-category?period=&date=` | `[{ categoryId, name, color, icon, total, percentage, count }]` |
| GET | `/dashboard/evolution?period=&date=` | `{ granularity, points: [{ label, from, to, income, expense, balance }] }` |
| GET | `/dashboard/recent-transactions?limit=5` | lista de transações |

```json
{ "period": "FORTNIGHT", "from": "2026-10-16", "to": "2026-10-31",
  "totalIncome": 4200.00, "totalExpense": 1850.40, "balance": 2349.60,
  "previous": { "from": "2026-10-01", "to": "2026-10-15",
                "incomeChangePct": 12.5, "expenseChangePct": -3.1 } }
```

### 6.9 Metas
| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/goals?type=&status=ACTIVE` | n/a | `200 [goal]` |
| GET | `/goals/{id}` | n/a | goal + últimas contribuições (se `SAVINGS`) |
| POST | `/goals` | ver abaixo | `201` |
| PUT | `/goals/{id}` | mesmo do POST | `200` |
| PATCH | `/goals/{id}/cancel` | n/a | `200` |
| DELETE | `/goals/{id}` | n/a | `204` |
| GET | `/goals/{id}/contributions` | n/a | paginado (só `SAVINGS`) |
| POST | `/goals/{id}/contributions` | `{ amount, date, notes }` | `201 { contribution, goal }` (só `SAVINGS`) |
| DELETE | `/goals/{id}/contributions/{contributionId}` | n/a | `204` (recalcula a meta) |

Criação de meta de economia:

```json
{ "type": "SAVINGS", "name": "Entrada do apartamento", "description": "20% do imóvel",
  "icon": "home", "color": "#3B82F6", "targetAmount": 60000.00, "endDate": "2028-12-31" }
```

Criação de meta de gasto:

```json
{ "type": "EXPENSE", "name": "Alimentação outubro", "targetAmount": 800.00,
  "categoryId": 3, "startDate": "2026-10-01", "endDate": "2026-10-31" }
```

Resposta (meta de gasto):

```json
{ "id": 7, "type": "EXPENSE", "name": "Alimentação outubro", "targetAmount": 800.00,
  "category": { "id": 3, "name": "Alimentação" },
  "startDate": "2026-10-01", "endDate": "2026-10-31",
  "currentAmount": 650.00, "percentage": 81.25, "state": "WARNING", "status": "ACTIVE" }
```

### 6.10 Simulações
| Método | URI | Request | Response |
|---|---|---|---|
| POST | `/simulations/investment` | `{ initialAmount, monthlyContribution, annualRate, years }` | `{ totalInvested, totalInterest, finalAmount, monthlyBreakdown: [...] }` |
| POST | `/simulations/debt-payoff` | `{ debtAmount, monthlyRate, monthlyPayment }` | `{ months, totalPaid, totalInterest, schedule: [...] }` |

---

## 7. Dependências

### Obrigatórias
| Dependência | Para quê |
|---|---|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` | Persistência |
| `spring-boot-starter-validation` | Bean Validation |
| `spring-boot-starter-security` | Segurança |
| `spring-boot-starter-oauth2-resource-server` | Valida o JWT próprio (e traz `NimbusJwtEncoder`) |
| `mysql-connector-j` | Driver MySQL |
| `flyway-core` + `flyway-mysql` | Migrations |
| `lombok` | Menos boilerplate |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI / OpenAPI |
| `spring-boot-starter-actuator` | Health check do Railway |
| `google-api-client` | `GoogleIdTokenVerifier` |

Versões: **Java 21 (LTS)** e a versão estável mais recente do Spring Boot 3.x. Confirmem em start.spring.io no dia de criar o projeto.

### Testes
- `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ)
- `spring-security-test`
- **Testcontainers (MySQL)**: testar com MySQL de verdade, não H2 (as queries agregadas e os `CHECK` se comportam diferente).

### Opcionais
- **MapStruct**: conversão entidade ↔ DTO (mappers manuais também servem no tamanho do MVP).
- Caffeine/Spring Cache: só se o dashboard ficar lento, e **depois de medir**.
- Bucket4j: rate limit em `/auth/*` (v2).

---

## 8. Configuração e deploy (Railway)

- Perfis `dev` e `prod` (`application-dev.yml`, `application-prod.yml`).
- **Segredos só por variável de ambiente:** `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `CORS_ORIGINS`.
- `spring.jpa.hibernate.ddl-auto=validate` em produção: quem altera o schema é o Flyway.
- **CORS** configurado com a origem do frontend (é o erro que mais trava na integração).
- Railway injeta `PORT`: `server.port=${PORT:8080}`.
- Health check em `/actuator/health`.
- `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`; "hoje" e "mês atual" calculados em `America/Sao_Paulo`.
- **Remover antes de produção** qualquer atalho de `userId` fixo usado em perfil `dev`.

### Flyway

- `V1__init.sql` já cria **todas** as tabelas, incluindo as colunas de cartão em `transactions` (nullable) e os `CHECK`. Custa uma linha agora e evita alterar `transactions` com dados em produção.
- Uma migration por mudança, nunca editar uma migration já aplicada.

---

## 9. Ordem de implementação

| Fase | Conteúdo | Feito? |
|---|---|---|
| 0 | Projeto Spring, Flyway `V1`, `BaseEntity`, `GlobalExceptionHandler`/`ProblemDetail`, Swagger, perfis. **Precisa estar no `main` antes de dividir.** | Feito |
| 1 | `User`, `RefreshToken`, validação Google, JWT, `SecurityConfig` | Feito |
| 2 | `Account` → `Category` → `Transaction` (já com XOR e campos de cartão no schema; compra no cartão ainda não implementada) | Feito |
| 3 | `Goal` (3 tipos) e `Dashboard` com `PeriodResolver`; `Simulation` (independente) | Feito |
| 4 | `Transfer` | Feito |
| 5 | `CreditCard` + faturas, compra **à vista** primeiro | Feito |
| 6 | Parcelamento → `InvoicePayment` → limite | Feito |
| 7 | Testes unitarioes e de integracao, Swagger para o João, CORS, deploy | ... |

**Por que o cartão fica por último:** ele toca `Transaction`, `Dashboard`, `Account` e `Goal` ao mesmo tempo. Se atrasar, não trava o resto do v1.

Enquanto a autenticação (fase 1) não existe, podem testar `Goal`/`Simulation` com um `userId` fixo **apenas no perfil `dev`**.

---

## 10. Alinhamento com o frontend (João)

- **Contrato primeiro:** publicar o Swagger cedo. O front gera os tipos TypeScript a partir de `/v3/api-docs` com `openapi-typescript`, e o v0 passa a trabalhar em cima de contratos reais.
- **Ícones:** o front mantém um mapa curado (40–60 ícones do `lucide-react`) que serve de **fonte da verdade** e alimenta o seletor de ícone. O seed do backend só usa chaves que existam nesse mapa. Sempre com fallback (`CircleHelp`) para chave desconhecida.

```ts
import { Utensils, Car, Home, Wallet, CircleHelp } from "lucide-react";

export const ICONS = { utensils: Utensils, car: Car, home: Home, wallet: Wallet } as const;

export function CategoryIcon({ name }: { name: string }) {
  const Icon = ICONS[name as keyof typeof ICONS] ?? CircleHelp;
  return <Icon />;
}
```

- **Não** usar `import * as icons from "lucide-react"` (joga a biblioteca inteira no bundle).
- **Telas novas por causa do v1:** contas, transferência, cartões, detalhe de fatura, fluxo de pagamento de fatura, e formulário de metas com 3 tipos.
- **Formulário de transação:** seletor "conta **ou** cartão" + campo de parcelas (só quando cartão).
- **Dashboard:** seletor de período (dia → ano + customizado) enviando `period` e `date`; o intervalo exibido vem da **resposta**, o front não calcula calendário.
- Enums chegam como string; valores monetários como número decimal.

---

## 11. Checklist de testes (mínimo)

- [ ] **Isolamento:** usuário A não acessa dado do usuário B em cada feature (inclui `accountId`, `creditCardId`, `categoryId` no payload).
- [ ] XOR de `account`/`creditCard` rejeitado na service **e** no banco.
- [ ] Cartão rejeita `INCOME`.
- [ ] `invoiceReference` nos limites: compra no dia do fechamento e no dia seguinte.
- [ ] Mudar `closingDay` não move compras antigas de fatura.
- [ ] Parcelas: soma das parcelas = total; resto na última; datas em fim de mês (ex.: 31/01 → 28/02).
- [ ] Status da fatura em cada um dos 5 estados; pagamento acima do total retorna 422; pagamento em fatura `OPEN` retorna 422.
- [ ] Transferência e pagamento de fatura **não** alteram receitas/despesas do dashboard.
- [ ] Saldo da conta bate com a fórmula após editar e excluir transações/transferências.
- [ ] `PeriodResolver`: quinzena, semana ISO, virada de trimestre/semestre/ano; período anterior correto.
- [ ] Evolução preenche baldes vazios com zero.
- [ ] Meta `SAVINGS`: retirada não deixa saldo negativo; contribuição em meta `INCOME`/`EXPENSE` retorna 422.
- [ ] Meta `EXPENSE`: estados `IN_PROGRESS`, `WARNING`, `EXCEEDED` e `ACHIEVED` ao encerrar o período.
- [ ] Simulação de dívida com pagamento ≤ juros retorna 422.
- [ ] Refresh token reutilizado é rejeitado.

---

## 12. Fora do v1 (anotar, não implementar)

Estorno/reembolso de compra no cartão · juros, multa, rotativo e IOF · cartão adicional · cashback e pontos · importação de extrato/fatura (OFX/CSV/PDF) · transações recorrentes · orçamento recorrente ("todo mês") · metas vinculadas a uma conta · simulações salvas · multi-moeda · notificações · foto/imagem em meta.

---

## 13. Próximo passo

Escrever o **`V1__init.sql`** completo (tabelas, índices, constraints e `CHECK`) e revisar juntos antes de codar. É a peça que mais evita retrabalho.
