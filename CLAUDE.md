# finance-platform: contexto do projeto

Backend de um sistema web de **gerenciamento financeiro pessoal**. O frontend (Next.js + TypeScript + Tailwind, gerado pelo v0) fica em outro repositório e é responsabilidade do João. Este repositório é o **backend REST**, feito por Biel e Breno.

## Stack

- Java 25 · Spring Boot 4.1.x · Maven (usar o wrapper `./mvnw`; no Windows, `mvnw.cmd`)
- Spring Web MVC · Spring Data JPA (Hibernate) · Bean Validation
- MySQL 8 · Flyway (migrations)
- Spring Security como **OAuth2 Resource Server** (JWT próprio) + login com Google via ID Token
- springdoc-openapi (Swagger UI) · Lombok · Actuator
- Testes: JUnit 5, Mockito, Spring Security Test, Testcontainers (MySQL)
- Deploy: Railway

## Documentação (leia antes de implementar)

| Arquivo | Quando ler |
|---|---|
| `docs/architecture.md` | Sempre que criar classe, pacote, config ou teste |
| `docs/domain-rules.md` | Sempre que mexer em regra de negócio |
| `docs/data-model.md` | Sempre que mexer em entidade, tabela ou migration |
| `docs/api-spec.md` | Sempre que criar ou alterar endpoint ou DTO |
| `docs/implementation-plan.md` | No início de cada tarefa: fase atual, dono e status |

Se a documentação e o código divergirem, **não escolha em silêncio**: avise e pergunte.

## Comandos

```bash
./mvnw -U clean compile                      # compilar (o -U força atualizar dependências)
./mvnw test                                  # testes
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Regras inegociáveis

1. **Dinheiro:** sempre `BigDecimal` e `DECIMAL(19,2)`. Nunca `double`/`float`.
2. **Multiusuário:** o `userId` vem **só do JWT**, nunca de payload, query ou URL.
3. **Isolamento:** toda query de dado do usuário filtra por `user_id`. Recurso de outro usuário responde **404**, não 403.
4. **Schema só via Flyway.** `ddl-auto=validate`. Nunca editar uma migration já aplicada; criar uma nova.
5. **Camadas:** `controller` (só HTTP) → `service` (regras e `@Transactional`) → `repository` → `entity`. DTOs nas bordas; nunca expor entidade na API.
6. **Não usar `@Data` em entidade JPA.** Usar `@Getter/@Setter`, `equals/hashCode` pelo `id`.
7. **Valores derivados são calculados, não gravados:** saldo de conta, fatura de cartão, progresso de meta `INCOME`/`EXPENSE`. Exceção: `currentAmount` de meta `SAVINGS`.
8. **Erros** padronizados com `ProblemDetail` (RFC 7807). Mapeamento: 400 validação · 404 não encontrado · 409 conflito · 422 regra de negócio.
9. **Listagens sempre paginadas** (máx. 100 por página).
10. **Agregações do dashboard** são feitas no banco (`SUM`/`GROUP BY`), nunca carregando entidades em memória.
11. **Datas:** `LocalDate` para datas de negócio; `Instant` (UTC) para auditoria; "hoje" e "mês atual" em `America/Sao_Paulo`, via `Clock` injetado (testável).
12. **Segredos só por variável de ambiente.** Nunca commitar `.env`, chaves ou senhas.

## Como trabalhar

- Mudanças **mínimas**: preserve a arquitetura existente e não invente arquivos, classes ou dependências que não estejam na documentação sem perguntar antes.
- Uma feature por vez, na ordem do `docs/implementation-plan.md`. Não comece algo novo antes de fechar o que está em andamento.
- Código novo vem com teste. Toda feature com dados de usuário vem com **teste de isolamento** (usuário A não acessa dado do usuário B).
- Alterou contrato de API ou regra? Atualize o doc correspondente **na mesma tarefa**.
- Identificadores de código, endpoints e nomes de tabela em **inglês**. Mensagens de validação e erro mostradas ao usuário em **português (pt-BR)**.

## Definição de pronto

- [ ] Compila e os testes passam (`./mvnw test`)
- [ ] Regras de `docs/domain-rules.md` cobertas por teste
- [ ] Teste de isolamento entre usuários, quando aplicável
- [ ] Migration nova (se mudou schema) e `validate` passando
- [ ] Swagger/OpenAPI reflete o endpoint
- [ ] Docs atualizados

## Fora do escopo do v1

Estorno de compra no cartão, juros/multa/rotativo, cartão adicional, cashback, importação de extrato/fatura, transações recorrentes, orçamento recorrente, metas vinculadas a conta, simulações salvas, multi-moeda, notificações, imagem em meta. **Não implementar**, só anotar.
