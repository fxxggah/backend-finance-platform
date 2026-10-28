# Modelo de dados

Fonte da verdade do schema do v1. Se o schema mudar, **crie uma migration nova** (`V<n+1>`) e atualize este arquivo na mesma tarefa. Nunca edite migration já aplicada.

> Nomes de tabela e coluna em inglês, `snake_case`. Tabelas no **plural**. Convenções gerais (tipos, auditoria, enums) estão em `docs/architecture.md` seção 6.

## 1. Convenções

| Item | Regra |
|---|---|
| PK | `BIGINT AUTO_INCREMENT` (`@GeneratedValue(strategy = IDENTITY)`) |
| Dinheiro | `DECIMAL(19,2)` / `BigDecimal`, sempre positivo (o sinal vem do `type`), exceto `goal_contributions.amount` |
| Auditoria | Toda entidade herda `BaseEntity`: `created_at`, `updated_at` (`DATETIME(6)`, UTC, `Instant`) |
| Enums | `VARCHAR`, `@Enumerated(EnumType.STRING)` |
| Multiusuário | Toda tabela de dado tem `user_id` (FK para `users`), exceto `goal_contributions` (isolada via `goals.user_id`) |
| Relacionamentos | `@ManyToOne(fetch = LAZY)` sempre |
| Exclusão | Soft delete (`deleted`) em `transactions` e `categories`; arquivar (`archived`) em `accounts` e `credit_cards` |
| Entidades JPA | `@Getter/@Setter`, `equals/hashCode` pelo `id`. **Sem `@Data`** |
| Engine/charset | InnoDB, `utf8mb4` |

## 2. Relacionamentos

```
User 1──N Account
User 1──N CreditCard
User 1──N Category
User 1──N Transaction ──N──1 Category
Transaction N──1 Account          (XOR com CreditCard)
Transaction N──1 CreditCard
User 1──N Transfer ──► from_account / to_account (Account)
CreditCard 1──N InvoicePayment ──N──1 Account
User 1──N Goal 1──N GoalContribution
Goal N──1 Category (opcional, só INCOME/EXPENSE)
User 1──N RefreshToken
```

Não existe tabela `Invoice`: fatura é consulta agregada por `(credit_card_id, invoice_reference)`.

## 3. Enums

```java
enum TransactionType { INCOME, EXPENSE }
enum AccountType     { BANK, CASH, DIGITAL_WALLET, OTHER }
enum GoalType        { SAVINGS, INCOME, EXPENSE }
enum GoalStatus      { ACTIVE, CANCELLED }                                   // persistido
enum GoalState       { IN_PROGRESS, WARNING, EXCEEDED, ACHIEVED, EXPIRED }   // calculado
enum InvoiceStatus   { OPEN, CLOSED, PARTIALLY_PAID, PAID, OVERDUE }         // calculado
enum Period          { DAY, WEEK, FORTNIGHT, MONTH, QUARTER, SEMESTER, YEAR, CUSTOM }
```

`GoalState`, `InvoiceStatus` e `Period` **não são colunas**: são calculados ou vêm da requisição.

## 4. Tabelas

### 4.1 `users`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `google_id` | VARCHAR(255) | NOT NULL, **UNIQUE** |
| `email` | VARCHAR(255) | NOT NULL, **UNIQUE** |
| `name` | VARCHAR(255) | NOT NULL |
| `picture_url` | VARCHAR(500) | |
| `active` | BOOLEAN | NOT NULL, default `true` |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

### 4.2 `refresh_tokens`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `token_hash` | VARCHAR(255) | NOT NULL, **UNIQUE** (só o hash, nunca o token puro) |
| `expires_at` | DATETIME(6) | NOT NULL |
| `revoked` | BOOLEAN | NOT NULL, default `false` |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Índice: `(user_id)`.

### 4.3 `accounts`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `name` | VARCHAR(60) | NOT NULL |
| `type` | VARCHAR(20) | NOT NULL (`AccountType`) |
| `initial_balance` | DECIMAL(19,2) | NOT NULL, default `0` |
| `color` | VARCHAR(7) | hex `#RRGGBB` |
| `icon` | VARCHAR(40) | chave do ícone (`^[a-z0-9-]{1,40}$`) |
| `archived` | BOOLEAN | NOT NULL, default `false` |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Constraint: **UNIQUE (`user_id`, `name`)**.

Saldo **não é coluna**: ver fórmula em `docs/domain-rules.md` seção 3.

### 4.4 `categories`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `name` | VARCHAR(60) | NOT NULL |
| `type` | VARCHAR(10) | NOT NULL (`TransactionType`) |
| `color` | VARCHAR(7) | hex |
| `icon` | VARCHAR(40) | chave do ícone |
| `deleted` | BOOLEAN | NOT NULL, default `false` |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Constraint: **UNIQUE (`user_id`, `name`, `type`)**.

### 4.5 `credit_cards`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `name` | VARCHAR(60) | NOT NULL |
| `credit_limit` | DECIMAL(19,2) | NOT NULL, `CHECK (credit_limit > 0)` |
| `closing_day` | INT | NOT NULL, `CHECK (closing_day BETWEEN 1 AND 28)` |
| `due_day` | INT | NOT NULL, `CHECK (due_day BETWEEN 1 AND 28)` |
| `color` | VARCHAR(7) | hex |
| `icon` | VARCHAR(40) | chave do ícone |
| `archived` | BOOLEAN | NOT NULL, default `false` |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Constraint: **UNIQUE (`user_id`, `name`)**. Dias limitados a 1–28 para fugir de fevereiro e meses de 30 dias.

### 4.6 `transactions`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `account_id` | BIGINT | NULL, FK `accounts` (XOR com `credit_card_id`) |
| `credit_card_id` | BIGINT | NULL, FK `credit_cards` (XOR com `account_id`) |
| `category_id` | BIGINT | NOT NULL, FK `categories` |
| `type` | VARCHAR(10) | NOT NULL (`TransactionType`) |
| `amount` | DECIMAL(19,2) | NOT NULL, `CHECK (amount > 0)` |
| `date` | DATE | NOT NULL |
| `description` | VARCHAR(255) | |
| `notes` | VARCHAR(500) | |
| `invoice_reference` | DATE | NULL; dia 1 do mês da fatura; **só** compra no cartão; gravado na criação |
| `installment_group_id` | VARCHAR(36) | NULL; UUID que agrupa as parcelas |
| `installment_number` | INT | NULL (ex.: 2) |
| `installment_total` | INT | NULL (ex.: 3) |
| `deleted` | BOOLEAN | NOT NULL, default `false` |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

`CHECK` (MySQL 8.0.16+):

```sql
CHECK ((account_id IS NULL) <> (credit_card_id IS NULL)),
CHECK (credit_card_id IS NULL OR type = 'EXPENSE')
```

Índices:

| Índice | Para quê |
|---|---|
| `(user_id, date)` | Listagem e dashboard por período |
| `(user_id, account_id, date)` | Saldo e filtro por conta |
| `(user_id, type, date)` | Receitas/despesas por período |
| `(user_id, category_id)` | Checagem "categoria tem transações?" e gastos por categoria |
| `(user_id, credit_card_id, invoice_reference)` | Fatura e limite do cartão |
| `(installment_group_id)` | Editar/excluir grupo de parcelas |

### 4.7 `transfers`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `from_account_id` | BIGINT | NOT NULL, FK `accounts` |
| `to_account_id` | BIGINT | NOT NULL, FK `accounts` |
| `amount` | DECIMAL(19,2) | NOT NULL, `CHECK (amount > 0)` |
| `date` | DATE | NOT NULL |
| `description` | VARCHAR(255) | |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

`CHECK (from_account_id <> to_account_id)`. Índices: `(user_id, date)`, `(from_account_id)`, `(to_account_id)`.

### 4.8 `invoice_payments`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `credit_card_id` | BIGINT | NOT NULL, FK `credit_cards` |
| `account_id` | BIGINT | NOT NULL, FK `accounts` (conta de onde saiu o dinheiro) |
| `invoice_reference` | DATE | NOT NULL; dia 1 do mês da fatura |
| `amount` | DECIMAL(19,2) | NOT NULL, `CHECK (amount > 0)` |
| `date` | DATE | NOT NULL |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Índices: `(user_id, credit_card_id, invoice_reference)`, `(account_id)`.

### 4.9 `goals`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `user_id` | BIGINT | NOT NULL, FK `users` |
| `type` | VARCHAR(10) | NOT NULL (`GoalType`) |
| `name` | VARCHAR(100) | NOT NULL |
| `description` | VARCHAR(255) | |
| `icon` | VARCHAR(40) | default `target` |
| `color` | VARCHAR(7) | hex |
| `target_amount` | DECIMAL(19,2) | NOT NULL, `CHECK (target_amount > 0)`; em `EXPENSE` é o **limite** |
| `current_amount` | DECIMAL(19,2) | **só `SAVINGS`**; soma das contribuições; `NULL`/`0` nos outros tipos |
| `category_id` | BIGINT | NULL, FK `categories`; só `INCOME`/`EXPENSE` |
| `start_date` | DATE | obrigatório em `INCOME`/`EXPENSE` |
| `end_date` | DATE | obrigatório em `INCOME`/`EXPENSE`; em `SAVINGS` é o prazo opcional |
| `status` | VARCHAR(10) | NOT NULL, default `ACTIVE` (`ACTIVE`/`CANCELLED`) |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Índices: `(user_id, type, status)`, `(user_id, category_id)`.

> `current_amount` de `INCOME`/`EXPENSE` **nunca** é gravado: é calculado na leitura.

### 4.10 `goal_contributions`

| Coluna | Tipo | Restrições |
|---|---|---|
| `id` | BIGINT | PK |
| `goal_id` | BIGINT | NOT NULL, FK `goals` |
| `amount` | DECIMAL(19,2) | NOT NULL, `CHECK (amount <> 0)`; positivo = depósito, negativo = retirada |
| `date` | DATE | NOT NULL |
| `notes` | VARCHAR(255) | |
| `created_at`, `updated_at` | DATETIME(6) | NOT NULL |

Índice: `(goal_id, date)`.

## 5. Exclusão de usuário (LGPD)

`DELETE /users/me` apaga **todos** os dados do usuário. As FKs precisam permitir isso sem violar integridade. Ordem segura de remoção (ou `ON DELETE CASCADE` em `user_id`):

1. `goal_contributions` → `goals`
2. `invoice_payments`
3. `transfers`
4. `transactions`
5. `credit_cards`, `accounts`, `categories`
6. `refresh_tokens`
7. `users`

> Decisão a confirmar antes do `V1`: usar `ON DELETE CASCADE` nas FKs ou apagar na service em ordem. Recomendação: **service em ordem, dentro de uma única `@Transactional`**, para o comportamento ficar explícito e testável (e não depender de cascade silencioso).

## 6. Seed de primeiro login

Não é migration: é feito pelo `AuthService` no primeiro login (ver `docs/domain-rules.md` seção 2).

- Categorias de despesa: Alimentação, Transporte, Moradia, Saúde, Educação, Lazer, Compras, Outros.
- Categorias de receita: Salário, Freelance, Investimentos, Outros.
- Conta "Carteira" (`CASH`, `initial_balance = 0`).
- Os ícones usados só podem ser chaves que existam no mapa do front.

## 7. Flyway

- Arquivos em `src/main/resources/db/migration`, nome `V<n>__descricao.sql`.
- `V1__init.sql` cria **todas** as tabelas, índices e `CHECK`, incluindo as colunas de cartão em `transactions` (nullable). Evita alterar `transactions` com dados em produção.
- Uma migration por mudança. `ddl-auto=validate` em produção.
- Testes de repositório rodam as migrations em **MySQL real (Testcontainers)**, nunca H2.
