# finance-platform · Contexto e Regras do Agente

Backend de um sistema web de **gerenciamento financeiro pessoal**. O frontend (Next.js + TypeScript + Tailwind, gerado pelo v0) fica em outro repositório e é responsabilidade do João. Este repositório é o **backend REST**, feito por Biel e Breno.

## Idioma e tom

Responda ao Biel e ao Breno em **português do Brasil, de forma direta e informal**. Dê recomendações concretas com conclusão clara, sem "depende" solto. Quando houver várias opções, analise, **escolha uma** e justifique em poucas linhas.

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

## Antes de escrever qualquer código

1. Leia `docs/implementation-plan.md` e confirme **em que fase estamos** e de quem é a tarefa.
2. Leia os docs que a tarefa exige: `architecture.md`, `domain-rules.md`, `data-model.md`, `api-spec.md`.
3. Olhe o código que já existe no pacote da feature (`Glob`/`Grep`) e **siga o padrão dele**. O pacote base real está na classe com `@SpringBootApplication`; não presuma o nome.

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

- Faça a **menor mudança** que resolve a tarefa. Preserve a arquitetura em camadas por feature.
- **Não invente** arquivos, classes, endpoints, colunas ou dependências que não estejam nos docs. Se faltar algo, proponha e pergunte.
- Uma tarefa por vez. Se aparecerem ideias novas no caminho, anote-as no fim da resposta; não as implemente.
- Se o doc e o código divergirem, ou se duas regras conflitarem, **pare e pergunte** em vez de escolher em silêncio.
- Mudou regra de negócio, contrato de API ou modelo de dados? Atualize o doc correspondente na mesma tarefa.
- Identificadores de código, endpoints e nomes de tabela em **inglês**. Mensagens de validação e erro mostradas ao usuário em **português (pt-BR)**.

## Formato das respostas

**Implementando uma feature:** diga em 2–3 linhas o que vai fazer, faça, e termine com o que foi alterado (arquivos) e como validar.

**Depurando um erro**, siga esta ordem:
1. **Causa** (o que está errado)
2. **Por quê** (por que acontece)
3. **Correção** (o que mudar)
4. **Local exato** (arquivo e trecho)
5. **Comandos/código** prontos para colar
6. **Efeitos colaterais** (o que a correção pode afetar)
7. **Como validar** (comando ou teste que prova que resolveu)

**Revisando código:** aponte problemas concretos, do mais grave ao menos grave, sempre com o trecho e a correção sugerida. Dê prioridade a: vazamento entre usuários, erro de dinheiro/arredondamento, regra de negócio faltando e N+1.

## Quando terminar uma tarefa

Rode (ou peça para rodar, se não tiver como) e relate o resultado:

```bash
./mvnw -U clean compile
./mvnw test
```

Confira a **definição de pronto** abaixo e marque a tarefa no `docs/implementation-plan.md`. Se algum item não foi cumprido, diga qual e por quê.

## Definição de pronto

- [ ] Compila e os testes passam (`./mvnw test`)
- [ ] Regras de `docs/domain-rules.md` cobertas por teste
- [ ] Teste de isolamento entre usuários, quando aplicável
- [ ] Migration nova (se mudou schema) e `validate` passando
- [ ] Swagger/OpenAPI reflete o endpoint
- [ ] Docs atualizados

## Limites

- Cuide **só do backend**. Frontend é do João; alterações de contrato de API precisam ser avisadas e registradas em `docs/api-spec.md`.
- Não adicione dependências no `pom.xml` sem avisar o motivo e a versão escolhida.
- Não leia, imprima nem grave segredos (`.env`, chaves, senhas, `application-prod.*` com credenciais).
- Nada de comandos destrutivos (`rm -rf`, `git reset --hard`, `git push --force`, `DROP`) sem confirmação explícita do Biel ou do Breno.
- Não implemente nada da lista **Fora do escopo do v1** abaixo.

## Fora do escopo do v1

Estorno de compra no cartão, juros/multa/rotativo, cartão adicional, cashback, importação de extrato/fatura, transações recorrentes, orçamento recorrente, metas vinculadas a conta, simulações salvas, multi-moeda, notificações, imagem em meta. **Não implementar**, só anotar.

## Comandos úteis

```bash
./mvnw -U clean compile                      # compilar (o -U força atualizar dependências)
./mvnw test                                  # testes
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

