# Auditoria dos testes do backend — 19/11/2026

Base examinada originalmente: commit `09a83d8`. A fase 5 está concluída no sandbox. A auditoria original antecedeu a publicação em produção e não alterou a implementação. A correção posterior dos três bloqueios e a nova avaliação estão em [Correções e reavaliação](#correções-e-reavaliação--19112026); os números de cobertura abaixo são históricos.

O inventário cruzou 52 mapeamentos HTTP do código com os testes de controller, serviço, repositório e integração. A busca estática percorreu os 51 arquivos Java de teste e localizou 770 métodos anotados; os testes parametrizados geram várias execuções. Nenhum método encontrado estava sem asserção ou verificação no corpo, e a busca não encontrou `@Disabled` nem asserções constantes óbvias. Esses critérios não provam que uma asserção verifica a propriedade correta. A cobertura JaCoCo indica execução, não correção de resultados, regras SQL ou integrações externas.

## Resultados da suíte

`mvnw.cmd -U clean compile` passou. A execução completa, em diretório isolado e com JaCoCo 0.8.16, passou com **1.005 casos em 48 classes, zero falhas, zero erros e zero ignorados**. Cobertura geral: **88,1% das linhas** (1.902/2.160) e **66,3% dos ramos** (862/1.300).

| Módulo | Linhas | Ramos |
|---|---:|---:|
| Contas | 94,2% | 77,8% |
| Autenticação | 90,6% | 50,0% |
| Segurança | 61,1% | 16,7% |
| Categorias | 91,4% | 63,6% |
| Cartões e faturas | 89,9% | 61,9% |
| Dashboard | 98,0% | 100,0% |
| Metas | 92,5% | 77,3% |
| Simulação | 98,4% | 85,7% |
| Transações | 97,4% | 88,7% |
| Transferências | 79,5% | 30,0% |
| Usuários | 91,7% | 56,2% |
| IA | 85,8% | 70,1% |
| Cobrança | 82,9% | 54,4% |
| Comum | 88,2% | 75,0% |

Em segurança, o `GoogleTokenVerifier` executou 6 de 38 linhas (só configuração); o `BillingWorker`, zero de 10. O dashboard tem cobertura de ramos alta, mas o problema de fuso abaixo mostra por que isso não garante o contrato. O relatório HTML completo está em `build/backend-test-audit/site/jacoco/index.html` e o log da suíte em `build/backend-test-audit-suite.log`. O POM temporário foi removido; o POM do projeto ficou intacto.

## Falhas reproduzidas em MySQL descartável

| Prioridade | Achado | Evidência | Correção e teste recomendados |
|---|---|---|---|
| Bloqueia produção | **Reutilizar refresh token não persiste a revogação dos demais.** Em [AuthService](../src/main/java/com/finance/auth/service/AuthService.java#L82), o update e a exceção estão na mesma transação. | Depois de receber a exceção esperada, a consulta em outra transação mostrou que o token irmão ainda não estava revogado. O [teste existente](../src/test/java/com/finance/auth/service/AuthServiceTest.java#L404) só comprova a chamada ao mock. | Persistir a revogação antes de retornar a rejeição e testar o estado dos tokens no MySQL depois da resposta. Testar também duas renovações concorrentes do mesmo token. |
| Bloqueia produção | **Um pagamento pode ser removido pela URL de outro cartão ou mês da mesma conta.** O [controller](../src/main/java/com/finance/creditcard/controller/CreditCardController.java#L68) valida o cartão do usuário, mas o [serviço](../src/main/java/com/finance/creditcard/service/InvoiceService.java#L92) busca o pagamento só pelo ID e usuário. | Dois testes HTTP com JWT real receberam 204 em vez de 404: cartão errado e mês errado. | Buscar pelo conjunto usuário + cartão + referência + pagamento. Verificar 404 e permanência da linha, e 204 apenas na URL correta. |
| Bloqueia produção | **Pagamentos simultâneos ultrapassam o saldo da fatura.** Em [InvoiceService](../src/main/java/com/finance/creditcard/service/InvoiceService.java#L62), a leitura do saldo e a gravação não são serializadas entre requisições. | Duas transações concorrentes persistiram R$ 120 em uma fatura de R$ 100: dois pagamentos de R$ 60. Os testes atuais de limite exercitam uma requisição por vez. | Garantir atomicidade por cartão/fatura. Testar pagamentos concorrentes, incluindo que a soma gravada não excede o total. |

Os quatro testes diagnósticos falharam pelas asserções acima, como esperado. As provas e seus logs estão em `build/backend-audit-proof-sources/`, `build/backend-audit-proofs.log` e `build/backend-audit-concurrency.log`. Não fazem parte da suíte oficial. Não usaram o MySQL local, credenciais de usuário, Google, Gemini ou Stripe.

## Lacunas e asserções que merecem reforço

- **Autenticação e produção:** o [verificador Google](../src/main/java/com/finance/security/GoogleTokenVerifier.java#L58) é substituído por mock no teste de autenticação. Falta testar o tratamento efetivo de audience, issuer, expiração, e-mail não verificado e falha do provedor, sem rede real. Os testes MVC geralmente usam `jwt()`, que injeta a identidade e não valida assinatura nem expiração. Há teste com JWT real para a conta desativada em cobrança, mas falta uma matriz de rejeição com o decoder real. Não há teste de boot do artefato no perfil `prod` porque a configuração de produção ainda não existe. Incluir MySQL/Flyway, health, CORS de origem negada e Swagger fechado nesse teste.
- **Datas:** [PeriodResolver](../src/main/java/com/finance/dashboard/service/PeriodResolver.java#L17) e [GoalService](../src/main/java/com/finance/goal/service/GoalService.java#L237) usam o dia local do processo. O [teste atual](../src/test/java/com/finance/dashboard/service/PeriodResolverTest.java#L73) usa a mesma fonte e não acusa a diferença entre UTC e São Paulo. Usar o `Clock` de negócio e fixar instantes nos quais os dois fusos têm dias ou meses diferentes.
- **Contrato de erros:** seis casos em [GoalControllerTest](../src/test/java/com/finance/goal/controller/GoalControllerTest.java#L293) e [TransactionControllerTest](../src/test/java/com/finance/transaction/controller/TransactionControllerTest.java#L230) aceitam qualquer 4xx. O teste pode passar por uma rejeição de autenticação ou por rota inexistente. Fixar status e corpo esperados e verificar se a ação foi barrada no ponto correto.
- **Páginas e volume:** transações limitam explicitamente o tamanho a 100; [TransferController](../src/main/java/com/finance/transfer/controller/TransferController.java#L49) aplica tamanho padrão, sem configuração de máximo 100 encontrada. Cartões e faturas retornam listas. Há divergência entre o limite geral do repositório e os contratos de lista; alinhar o contrato antes de alterá-lo. [InvoiceService](../src/main/java/com/finance/creditcard/service/InvoiceService.java#L55) também faz agregações por fatura na listagem. Adicionar cenários com volume e orçamento de queries para flagrar crescimento excessivo.
- **Stripe:** a integração com MySQL testa HMAC, repetição, prazo, reembolso, renovação, exclusão e fencing, mas [desliga o worker](../src/test/java/com/finance/billing/BillingIntegrationTest.java#L41) e chama o processamento diretamente. O piloto testou o worker real com Stripe; falta regressão automática do agendamento, escolha de eventos vencidos, retomada e isolamento frente a falha em um item do lote.
- **IA e áudio:** fixtures e mocks cobrem formato, parsing, limites e cotas. Ainda falta testar o subprocesso [ffprobe](../src/main/java/com/finance/ai/audio/FfprobeAudioInspector.java#L53) no Linux de deploy com arquivos válidos, corrompidos, timeout e excesso de saída. Testes da PoC não medem a qualidade do Gemini real; Chrome e Safari foram validados separadamente no piloto.

## Cobertura funcional observada

| Módulo | Evidência principal | Próximo reforço |
|---|---|---|
| Contas e categorias | MVC, serviços e repositórios MySQL; filtros por usuário e regras de arquivamento/exclusão | Fluxo HTTP autenticado com estado de saldo e exclusão conferidos antes/depois |
| Transações e transferências | Validação, parcelas, centavos, virada de mês/ano, filtros e isolamento em testes de serviço/repositório | Cenários integrados de rollback e saldos das duas contas; tamanho máximo das páginas de transferências |
| Cartões e faturas | Fechamento/vencimento, pagamento parcial/antecipado, saldo e repositórios | Corrigir e automatizar os três cenários de fatura reproduzidos |
| Dashboard, metas e simulações | Agregações, filtros, status, juros/amortização com resultados numéricos esperados | Fuso determinístico, volume e integração de transação → dashboard/meta |
| Autenticação e usuários | Casos HTTP, hash, rotação simulada, consultas MySQL e exclusão de conta com JWT real em billing | Revogação persistente, concorrência de refresh e verificador Google real com transporte simulado |
| IA e cobrança | Coordenadores e MySQL, mocks de provedores, testes concorrentes de cotas/checkout e piloto externo separado | Worker agendado, decoder de áudio Linux e boot isolado com flags desligadas |

Ordem recomendada na auditoria original: corrigir os três bloqueios com regressões que falhem primeiro; endurecer autenticação, fuso e contratos de erro; preparar e testar o perfil de produção; só então reavaliar a publicação. Não usar meta de 100% de linhas como substituto desses critérios.

## Correções e reavaliação — 19/11/2026

Os três bloqueios reproduzidos foram corrigidos. A prova anterior à correção está em `build/backend-blockers-red.log`: **10 casos, 5 falhas de asserção, zero erros e zero ignorados**. Reproduziu as quatro provas originais e também duas renovações bem-sucedidas do mesmo refresh. Após a correção, os **131 casos das áreas afetadas passaram**, incluindo as 12 novas regressões em MySQL 8.4/Testcontainers (`build/backend-blockers-green.log`).

| Bloqueio original | Correção | Regressão oficial |
|---|---|---|
| Revogação revertida ao rejeitar refresh reutilizado | `AuthService.refresh` confirma a transação em `BadCredentialsException` e bloqueia o token por hash antes da rotação. Erros inesperados continuam desfazendo revogação e sucessor. | `AuthRefreshIntegrationTest`: estado consultado após o 401, preservação dos tokens de outro usuário, duas renovações concorrentes com apenas um sucessor (também revogado), expiração e rollback em falha de assinatura simulada. |
| Exclusão pela URL de outro cartão/mês | O controller encaminha cartão e referência, e o repository filtra pagamento por ID, usuário, cartão e referência. Uma referência inválida também retorna 404. | `InvoicePaymentIntegrationTest` usa JWT assinado e decoder real. Cobre 404 com linha preservada para cartão ou mês incorreto, mês inválido e outro usuário. Na URL correta, verifica 204, preservação de outro pagamento e saldo da conta. |
| Pagamentos concorrentes acima do saldo | `InvoiceService.payInvoice` bloqueia a linha do cartão por ID/usuário antes de calcular o saldo, em READ_COMMITTED, até o commit. | Duas transações com a primeira inserção ainda sem commit: R$ 60 + R$ 60 em fatura de R$ 100 resulta em 422 na segunda e apenas R$ 60 gravados; R$ 60 + R$ 40 resulta em dois pagamentos e R$ 100. Consulta posterior confirma soma, quantidade e débito da conta. |

As regressões não dependem de mocks de repositories/agregações nem de rollback automático envolvendo o teste. Usam barreiras e esperas limitadas para manter a primeira transação aberta; falhas de execução não são convertidas em resultados aceitáveis. O signer JWT é real, exceto no caso explícito que simula falha inesperada. MySQL local e provedores externos não foram usados.

Trade-off: meses diferentes do **mesmo cartão** também aguardam o bloqueio; cartões distintos usam linhas distintas. Não houve mudança de schema, dependências, rotas ou DTOs. A correção torna o vínculo da URL obrigatório e pode exigir ajuste de frontend que enviava cartão/mês incorreto. Descrições OpenAPI e contrato foram atualizados; a regressão consulta `/v3/api-docs`.

### Validação final

- `mvnw.cmd -U clean compile` passou com JDK 25 (`build/backend-blockers-compile.log`).
- A execução padrão de `mvnw.cmd test` foi interrompida após `AccountRepositoryTest.class` desaparecer de `target` durante a suíte (19 erros de contexto nessa classe, sem falha de asserção). O mesmo tipo de interferência já estava registrado no plano; o processo responsável não foi identificado. Evidência: `build/backend-blockers-suite.log`.
- A primeira suíte isolada foi interrompida antes do resumo final (`build/backend-blockers-suite-isolated.log`). Em 20/11, a compilação da versão final com saída isolada passou (`build/backend-blockers-compile-final.log`). Uma tentativa da suíte encontrou o Docker desligado e registrou 15 erros de inicialização do Testcontainers, sem falhas de asserção (`build/backend-blockers-suite-final.log`).
- Com o Docker ativo, a suíte completa retornou `BUILD SUCCESS` em 20/11 (`build/backend-blockers-suite-docker.log`): **1.019 testes, zero falhas, zero erros de teste e zero ignorados**. Inclui 14 regressões novas, duas delas para referências de mês inválidas, além da conferência do OpenAPI. Flyway aplicou e validou as três migrations, e os contextos MySQL das regressões iniciaram com Hibernate `ddl-auto=validate`. O Surefire encerrou o processo filho após 30 s de espera no desligamento, depois da execução dos testes, e o Maven retornou código 0.
- Isolamento feito com cópia temporária do mesmo POM na raiz, acrescentando **somente** `<build><directory>${project.basedir}/build/backend-blockers-verification</directory>`. Nenhum teste foi excluído e o POM do projeto não foi alterado.
- Nenhuma meta nova de cobertura JaCoCo foi medida; as porcentagens da auditoria original não representam a árvore após as correções.

Reprodução habitual, com JDK 25 em `JAVA_HOME`, Docker ativo e sem build simultâneo da IDE:

```powershell
.\mvnw.cmd -U clean compile
.\mvnw.cmd test
```

Se as classes de `target` voltarem a desaparecer durante a execução, repetir com a saída isolada:

```powershell
$verificationPom = Join-Path $PWD '.backend-blockers-verification-pom.xml'
if (Test-Path -LiteralPath $verificationPom) { throw 'POM temporário já existe.' }
$pomText = [IO.File]::ReadAllText((Join-Path $PWD 'pom.xml'))
[IO.File]::WriteAllText($verificationPom,
    $pomText.Replace('<build>', '<build><directory>${project.basedir}/build/backend-blockers-verification</directory>'),
    [Text.UTF8Encoding]::new($false))
try {
    .\mvnw.cmd -f $verificationPom -U clean compile
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }
    .\mvnw.cmd -f $verificationPom test
    if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes.' }
} finally {
    Remove-Item -LiteralPath $verificationPom
}
```

### Decisão sobre deploy

**Manter o deploy de produção bloqueado.** As três falhas confirmadas estão corrigidas, mas os seguintes critérios da auditoria continuam pendentes:

1. **Boot reproduzível do artefato em `prod`:** não existe configuração de produção versionada. Há um `application-prod.yml` local ignorado pelo Git; seu conteúdo não foi lido. A configuração versionada ainda ativa `dev` por padrão. Falta comprovar inicialização no perfil correto com MySQL/Flyway/validate, health, CORS negado e Swagger fechado.
2. **Autenticação e fuso:** continuam faltando a matriz de rejeição do JWT pelo decoder real e os cenários do verificador Google com transporte simulado. `PeriodResolver` e `GoalService` ainda usam `LocalDate.now()` sem o Clock de São Paulo; precisam de correção e testes na virada de dia/mês.
3. **Liberação da V2:** IA e cobrança permanecem explicitamente restritas a `dev & !prod`, inclusive com flags ligadas. Habilitá-las em produção exige uma etapa própria, com teste do worker agendado e do ffprobe no Linux usado pelo deploy. Os pilotos não substituem essas verificações.

Contratos de erro genéricos, paginação e volume/N+1 permanecem no backlog da auditoria; não foram corrigidos ou aprovados implicitamente nesta tarefa. Não houve deploy nem mudança de ambiente externo. Próxima etapa recomendada: fechar os critérios de autenticação/fuso e preparar o teste de boot de produção antes de publicar.
