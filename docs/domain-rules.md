# Regras de negócio

Fonte da verdade das regras do v1. Se uma regra mudar, **atualize este arquivo na mesma tarefa**.

Convenção: `422` para regra de negócio violada, `400` para payload inválido, `404` para recurso inexistente **ou de outro usuário**, `409` para conflito (nome duplicado).

## 1. Decisões globais

| Decisão | Regra |
|---|---|
| Dinheiro | `BigDecimal`, `DECIMAL(19,2)`, valores sempre positivos (o sinal vem do `type`) |
| Despesa no cartão | Conta na **data da compra/parcela** (competência), não no pagamento da fatura |
| Valores derivados | Saldo, fatura e progresso de meta `INCOME`/`EXPENSE` são **calculados**, nunca gravados |
| Exclusão | Soft delete em `transaction` e `category`; **arquivar** em `account` e `credit_card` |
| Ícone | Chave de texto `^[a-z0-9-]{1,40}$`; o front mapeia para o ícone. O back não valida contra lista |
| Cor | Hex `^#[0-9A-Fa-f]{6}$` |
| Fuso | "Hoje" e "mês atual" em `America/Sao_Paulo` |

## 2. Autenticação e usuário

- Validar o ID Token do Google: assinatura, `aud`, `iss`, expiração e `email_verified = true`.
- Primeiro login cria o `User`, as **categorias padrão** e a conta **"Carteira"** (`CASH`).
  - Despesa: Alimentação, Transporte, Moradia, Saúde, Educação, Lazer, Compras, Outros.
  - Receita: Salário, Freelance, Investimentos, Outros.
- Logins seguintes atualizam nome e foto; a busca é por `googleId`. Nunca duplicar usuário.
- Refresh token é de **uso único** (rotation): ao renovar, o antigo é revogado.
- Guardar só o **hash** do refresh token.
- Logout revoga o refresh token.
- `User.active = false` não autentica.
- `DELETE /users/me` apaga a conta e **todos** os dados do usuário (LGPD).

## 3. Contas (`Account`)

- Nome obrigatório (1–60), **único por usuário**.
- Tipos: `BANK`, `CASH`, `DIGITAL_WALLET`, `OTHER`.
- `initialBalance` pode ser alterado a qualquer momento; só muda o saldo calculado.
- Conta **com movimentação** (transações, transferências ou pagamentos de fatura): só é **arquivada**. Conta vazia: hard delete.
- Conta arquivada some do seletor de nova transação, mas histórico e relatórios continuam.
- Não pode excluir/arquivar a **última conta ativa** do usuário.
- Saldo negativo é permitido (nunca bloquear lançamento por saldo).

**Saldo (calculado):**

```
saldo = initialBalance
      + receitas da conta
      − despesas da conta
      + transferências recebidas
      − transferências enviadas
      − pagamentos de fatura feitos a partir da conta
```

## 4. Categorias (`Category`)

- Nome 1–60, **único por usuário dentro do mesmo tipo** (`INCOME`/`EXPENSE`).
- O tipo **não pode mudar** se a categoria já tem transações.
- Excluir categoria **com** transações: soft delete. **Sem** transações: hard delete.
- Não pode criar transação em categoria excluída.

## 5. Transações (`Transaction`)

- `amount` > 0, no máximo 2 casas decimais, teto 999.999.999,99.
- Exatamente **um** entre `accountId` e `creditCardId` (**XOR**). Validar na service **e** no banco (`CHECK`).
- Cartão só aceita `EXPENSE`.
- O tipo da transação deve ser igual ao tipo da categoria.
- Conta, cartão e categoria devem **pertencer ao usuário** e não estar arquivados/excluídos.
- `date` obrigatória. Data futura: até **1 ano**; exceção: parcelas, até 24 meses.
- Edição e exclusão só do próprio usuário. Exclusão é soft delete e sai de todos os cálculos.
- Listagem **paginada** (máx. 100), ordenada por data decrescente. Filtros: período, tipo, categoria, conta, cartão e texto.

### Parcelamento (só cartão)

- Compra de R$ 600 em 3x vira **3 transações** com o mesmo `installmentGroupId` (UUID).
- Parcela *n*: `date = dataDaCompra.plusMonths(n − 1)` (o `java.time` ajusta fim de mês) e `invoiceReference` calculado a partir dessa data.
- Valor: `base = total / n` arredondado **para baixo** em 2 casas; a **última parcela** recebe o resto (33,33 / 33,33 / 33,34).
- Máximo de **24 parcelas**. À vista = `installments = 1`.
- Excluir apaga o **grupo inteiro**. Editar categoria/descrição vale para o grupo.
- **Valor e nº de parcelas não são editáveis**: exclui e recria.

## 6. Transferências (`Transfer`)

- `fromAccount ≠ toAccount`; ambas do usuário e não arquivadas; `amount` > 0.
- **Não gera receita nem despesa**: só altera saldos.
- Editar/excluir recalcula os saldos automaticamente (são calculados).
- Conta com transferência vinculada só pode ser arquivada.

## 7. Cartão de crédito (`CreditCard`)

### Cadastro

- `creditLimit` > 0; `closingDay` e `dueDay` entre **1 e 28**.
- Nome único por usuário.
- Alterar `closingDay`/`dueDay` vale **só para compras futuras**. O `invoiceReference` já gravado **nunca** muda.
- Cartão com compras ou pagamentos: só **arquivado**. Vazio: hard delete.

### Em qual fatura cai a compra

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

Exemplo (fecha dia 25, vence dia 2): compra em 20/10 → fatura de outubro, vence 02/11. Compra em 26/10 → fatura de novembro, vence 02/12.

**`invoiceReference` é gravado na criação da transação.** Nunca calcular na leitura.

### Fatura

Não existe tabela `Invoice`: a fatura é uma consulta agregada por `(cartão, invoiceReference)`.

```
total = SUM(transações do cartão com o invoiceReference, deleted = false)
pago  = SUM(InvoicePayment do cartão com o invoiceReference)
```

**Status (avaliar nesta ordem):**

| # | Condição | Status |
|---|---|---|
| 1 | hoje ≤ data de fechamento | `OPEN` |
| 2 | pago ≥ total (ou total = 0) | `PAID` |
| 3 | hoje > vencimento | `OVERDUE` |
| 4 | pago > 0 | `PARTIALLY_PAID` |
| 5 | caso contrário | `CLOSED` |

### Pagamento de fatura (`InvoicePayment`)

- Só em fatura com status diferente de `OPEN` (o total ainda não é final) → senão **422**.
- Aceita pagamento parcial; a soma dos pagamentos **não pode passar do total** (422).
- Conta de origem do usuário e não arquivada.
- **Não é despesa**: não entra em receitas/despesas do dashboard. Reduz o saldo da conta.

### Limite

```
limite usado      = SUM(todas as compras do cartão, incl. parcelas futuras) − SUM(todos os pagamentos)
limite disponível = creditLimit − limite usado
```

Estourar o limite **não bloqueia**: devolver `availableLimit` negativo e o front avisa.

## 8. Dashboard

- Todo cálculo filtra `deleted = false` e `user_id`.
- **Não entram** em receitas/despesas: transferências e pagamentos de fatura.
- Compras no cartão entram **na data da compra/parcela**.
- Sem filtro, consolida todas as contas e cartões. `accountId` = só transações da conta. `creditCardId` = só do cartão.
- Calcular com **queries agregadas** no banco.
- Período máximo de consulta: **24 meses**.
- Variação percentual sobre período anterior zerado devolve `null`.

### Períodos (resolvidos pelo backend em `PeriodResolver`)

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

O resolver devolve `(from, to)` **e o período anterior equivalente**. A resposta sempre inclui o intervalo resolvido, para o front não calcular calendário.

### Evolução (gráfico)

Granularidade derivada do tamanho do intervalo: até 31 dias → **dia**; até ~100 dias → **semana**; acima → **mês**.
Fazer **uma query** agrupada por `date` e `type` e montar os baldes em Java. Baldes vazios vêm com **zero** (sem buracos no gráfico).

## 9. Metas (`Goal`)

### Regras comuns

- `targetAmount` > 0; nome obrigatório (até 100); `description` até 255; `icon`/`color` opcionais (padrão `target`).
- `status` persistido: `ACTIVE` ou `CANCELLED`. O **estado** (`GoalState`) é calculado na resposta.

### `SAVINGS` (objetivo: entrada de casa, carro, moto, PS5)

- `endDate` opcional (prazo); se informado, deve ser futuro na criação.
- Progresso por `GoalContribution` (depósito/retirada manual). `currentAmount` é gravado e atualizado na **mesma transação**.
- Retirada **não pode** deixar `currentAmount` negativo. Contribuição ≠ 0.
- Aceita contribuição após atingir 100% (passa de 100%, sem bloquear). Não aceita em meta `CANCELLED`.
- `monthlyNeeded = (target − current) / meses restantes` (mínimo 1 mês), só se houver `endDate`.
- Contribuição **não** gera despesa nem altera saldo de conta.

### `INCOME` e `EXPENSE` (metas de desempenho)

- `startDate` e `endDate` obrigatórios, com `endDate >= startDate`.
- `category` opcional; se informada, o tipo da categoria deve bater com o da meta. Sem categoria = global.
- **Não** gravar `currentAmount`: calcular na leitura com a mesma query agregada do dashboard (inclui gastos no cartão).
- Contribuição em meta `INCOME`/`EXPENSE` → **422**.

### Estado calculado

| Tipo | Regra |
|---|---|
| `EXPENSE` | `< 80%` → `IN_PROGRESS` · `80–100%` → `WARNING` · `> 100%` → `EXCEEDED`. Período encerrado e `≤ 100%` → `ACHIEVED` |
| `INCOME` / `SAVINGS` | `≥ 100%` → `ACHIEVED` · `< 100%` → `IN_PROGRESS` · período/prazo encerrado sem atingir → `EXPIRED` |

`percentage = currentAmount / targetAmount × 100` (valor real preservado, o front limita a exibição).

## 10. Simulações (sem persistência)

Sempre incluir o aviso: *"Simulação ilustrativa, não é recomendação financeira."*

### Projeção de investimento

- Entradas: `initialAmount` ≥ 0, `monthlyContribution` ≥ 0, `annualRate` 0–100 %, `years` 1–50.
- Taxa mensal composta: `i = (1 + taxaAnual)^(1/12) − 1`.
- `FV = P·(1+i)^n + PMT·[((1+i)^n − 1) / i]`, tratando `i = 0` à parte.
- Retorna total investido, juros, valor final e série mês a mês.
- `BigDecimal` com `MathContext` e `RoundingMode.HALF_EVEN`.

### Quitação de dívida

- Entradas: `debtAmount` > 0, `monthlyRate` ≥ 0, `monthlyPayment` > 0.
- **Regra crítica:** se `monthlyPayment <= debtAmount × monthlyRate`, a dívida nunca é paga → **422** com mensagem clara (evita loop infinito).
- Limite de segurança de 600 meses no cálculo.
- Retorna meses, total pago, total de juros e tabela de amortização.
