# Plano de implementação

Leia **no início de cada tarefa**: fase atual, dono e status. Uma feature por vez, na ordem abaixo. Não comece algo novo antes de fechar o que está em andamento.

**Legenda:** `[ ]` a fazer · `[~]` em andamento · `[x]` pronto (cumpre a Definição de pronto do `CLAUDE.md`)

## Equipe

| Pessoa | Papel |
|---|---|
| Biel | Backend |
| Breno | Backend |
| João | Frontend (outro repositório) |

## Resumo das fases

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
