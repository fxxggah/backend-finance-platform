# Especificação da API

Contrato REST do v1. O front (João) gera os tipos TypeScript a partir do OpenAPI (`/v3/api-docs`), então **este documento e o Swagger precisam bater**. Alterou endpoint ou DTO? Atualize aqui **na mesma tarefa** e avise o João.

## 1. Convenções

- Prefixo **`/api/v1`**. Todas as rotas, exceto `/auth/**`, exigem `Authorization: Bearer <accessToken>`.
- Datas em ISO-8601 (`2026-10-21`). Referência de fatura no formato `yyyy-MM`.
- Valores monetários como número decimal (`1234.56`). Enums como string.
- Paginação: `?page=0&size=20&sort=date,desc` (máx. 100 por página). Resposta: `{ content, page, size, totalElements, totalPages }`.
- `userId` **nunca** trafega em payload, query ou URL.
- Mensagens de validação e erro em português (pt-BR); identificadores, campos e rotas em inglês.

### Status HTTP

| Status | Uso |
|---|---|
| `200` | OK (inclui `POST` de simulação) |
| `201` | Recurso criado |
| `204` | Sem conteúdo |
| `400` | Validação de payload |
| `401` | Não autenticado / token inválido |
| `404` | Não encontrado (**inclui recurso de outro usuário**) |
| `409` | Conflito (ex.: nome duplicado) |
| `422` | Regra de negócio violada |
| `500` | Erro inesperado (mensagem genérica) |

### Formato de erro (`ProblemDetail`, RFC 7807)

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "amount must be greater than 0",
  "errors": [{ "field": "amount", "message": "deve ser maior que zero" }]
}
```

`errors[]` só aparece em `400`.

## 2. Auth

Públicas (sem Bearer).

| Método | URI | Request | Response |
|---|---|---|---|
| POST | `/auth/google` | `{ idToken }` | `200 { accessToken, refreshToken, expiresIn, user }` |
| POST | `/auth/refresh` | `{ refreshToken }` | `200 { accessToken, refreshToken, expiresIn }` |
| POST | `/auth/logout` | `{ refreshToken }` | `204` |

- Access token: JWT, 15 min. Refresh token: 7 dias, **uso único** (rotation).
- Refresh reutilizado ou revogado → `401`.

## 3. Usuário

| Método | URI | Response |
|---|---|---|
| GET | `/users/me` | `200 { id, name, email, pictureUrl }` |
| DELETE | `/users/me` | `204` (apaga a conta e todos os dados) |

## 4. Contas

| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/accounts` | n/a | `200 { accounts: [{ id, name, type, balance, color, icon, archived }], totalBalance, creditCardDebt }` |
| POST | `/accounts` | `{ name, type, initialBalance, color, icon }` | `201` |
| PUT | `/accounts/{id}` | `{ name, type, initialBalance, color, icon }` | `200` |
| DELETE | `/accounts/{id}` | n/a | `200 { archived: true/false }` |

- `DELETE`: hard delete se a conta não tem movimentação; senão arquiva. Última conta ativa → `422`.
- `creditCardDebt` = soma do que há de fatura em aberto (para o patrimônio líquido).
- `balance` é calculado (nunca gravado).

## 5. Categorias

| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/categories?type=EXPENSE` | n/a | `200 [{ id, name, type, color, icon }]` |
| POST | `/categories` | `{ name, type, color, icon }` | `201` |
| PUT | `/categories/{id}` | `{ name, color, icon }` | `200` |
| DELETE | `/categories/{id}` | n/a | `204` |

- Nome duplicado dentro do mesmo tipo → `409`.
- `DELETE`: soft delete se tem transações; hard delete se não tem.
- Tipo não é editável (por isso o `PUT` não recebe `type`).

## 6. Transações

| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/transactions?from=&to=&type=&categoryId=&accountId=&creditCardId=&q=&page=&size=` | n/a | `200` paginado |
| GET | `/transactions/{id}` | n/a | `200` |
| POST | `/transactions` | ver abaixo | `201` (lista, se parcelada) |
| PUT | `/transactions/{id}` | igual ao POST (sem valor/parcelas se for parcelada) | `200` |
| DELETE | `/transactions/{id}` | n/a | `204` (apaga o grupo inteiro se parcelada) |

Ordenação padrão: `date,desc`.

**Request de criação** (`accountId` **OU** `creditCardId`, nunca os dois):

```json
{ "type": "EXPENSE", "amount": 600.00, "categoryId": 3, "date": "2026-10-20",
  "description": "Notebook", "notes": null,
  "creditCardId": 2, "installments": 3 }
```

- `installments` só vale com `creditCardId` (1 a 24; omitido = 1).
- Os dois ou nenhum entre `accountId`/`creditCardId` → `422`. Cartão com `INCOME` → `422`.

**Resposta de uma transação:**

```json
{ "id": 42, "type": "EXPENSE", "amount": 200.00, "date": "2026-10-20",
  "description": "Notebook", "notes": null,
  "category": { "id": 3, "name": "Compras", "color": "#FF5733", "icon": "shopping-bag" },
  "account": null,
  "creditCard": { "id": 2, "name": "Nubank" },
  "invoiceReference": "2026-11",
  "installment": { "groupId": "…", "number": 1, "total": 3 } }
```

`account`, `creditCard`, `invoiceReference` e `installment` são `null` quando não se aplicam.

## 7. Transferências

| Método | URI | Request | Response |
|---|---|---|---|
| POST | `/transfers` | `{ fromAccountId, toAccountId, amount, date, description }` | `201` |
| GET | `/transfers?from=&to=&accountId=&page=&size=` | n/a | `200` paginado |
| GET | `/transfers/{id}` | n/a | `200` |
| PUT | `/transfers/{id}` | igual ao POST | `200` |
| DELETE | `/transfers/{id}` | n/a | `204` |

`fromAccountId == toAccountId` → `422`.

## 8. Cartões de crédito

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

- `availableLimit` pode ser **negativo** (estourar o limite não bloqueia).
- `status` da fatura: `OPEN`, `CLOSED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE` (calculado).
- Pagamento em fatura `OPEN`, ou que faça a soma passar do total → `422`.

## 9. Dashboard

Todos aceitam `period`, `date` (referência) e, para `CUSTOM`, `from`/`to`. Filtros opcionais: `accountId`, `creditCardId`. Intervalo máximo: 24 meses.

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

- `incomeChangePct`/`expenseChangePct` = `null` quando o período anterior é zero.
- `granularity` ∈ `DAY`, `WEEK`, `MONTH`. Baldes vazios vêm com zero.
- O front **não calcula calendário**: usa `from`/`to` da resposta.

## 10. Metas

| Método | URI | Request | Response |
|---|---|---|---|
| GET | `/goals?type=&status=ACTIVE` | n/a | `200 [goal]` |
| GET | `/goals/{id}` | n/a | goal + últimas contribuições (se `SAVINGS`) |
| POST | `/goals` | ver abaixo | `201` |
| PUT | `/goals/{id}` | igual ao POST | `200` |
| PATCH | `/goals/{id}/cancel` | n/a | `200` |
| DELETE | `/goals/{id}` | n/a | `204` |
| GET | `/goals/{id}/contributions` | n/a | `200` paginado (só `SAVINGS`) |
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

- `state` ∈ `IN_PROGRESS`, `WARNING`, `EXCEEDED`, `ACHIEVED`, `EXPIRED` (calculado). `status` ∈ `ACTIVE`, `CANCELLED` (persistido).
- `percentage` preserva o valor real (pode passar de 100); o front limita a exibição.
- Metas `SAVINGS` incluem `monthlyNeeded` quando há `endDate`.
- `POST /goals/{id}/contributions` em meta `INCOME`/`EXPENSE` ou `CANCELLED` → `422`. Retirada que deixaria `currentAmount` negativo → `422`.

## 11. Simulações

Sem persistência. A resposta sempre inclui `disclaimer`: *"Simulação ilustrativa, não é recomendação financeira."*

| Método | URI | Request | Response |
|---|---|---|---|
| POST | `/simulations/investment` | `{ initialAmount, monthlyContribution, annualRate, years }` | `200 { totalInvested, totalInterest, finalAmount, monthlyBreakdown: [...], disclaimer }` |
| POST | `/simulations/debt-payoff` | `{ debtAmount, monthlyRate, monthlyPayment }` | `200 { months, totalPaid, totalInterest, schedule: [...], disclaimer }` |

- Investimento: `initialAmount` ≥ 0, `monthlyContribution` ≥ 0, `annualRate` 0–100, `years` 1–50.
- Dívida: se `monthlyPayment <= debtAmount × monthlyRate` → `422` (nunca quita). Limite de 600 meses.

## 12. Públicos e documentação

- Sem autenticação: `/auth/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` (Swagger só no perfil `dev`).
- Controllers com `@Tag`, endpoints com `@Operation`.
