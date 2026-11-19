# Fase 5 — operação Stripe sandbox

Implementação de desenvolvimento; não habilita pagamentos reais. Gabriel é responsável pela conta, configuração de preço/portal e credenciais. Nenhum segredo foi lido ou alterado pelo agente. Java 25 / Boot 4.1.1 preservados; SDK oficial stripe-java 34.1.0, API 2026-10-28.endive. Frontend Next 16.4.6.

## Configurar manualmente

No sandbox, nas **Configurações → Formas de pagamento**, habilite apenas cartão na configuração padrão usada pelo Checkout. A API Endive seleciona os métodos a partir do painel e o backend só entrega o link quando a sessão retorna apenas cartão.

### Stripe CLI no Windows — alternativa ao WinGet (18/11/2026)

O WinGet de Gabriel não encontrou Stripe.StripeCLI. Foi preparada a versão portátil oficial **1.55.12** em `build/stripe-cli/1.55.12/stripe.exe`, ignorada pelo Git. ZIP obtido da release oficial, SHA-256 conferido com o digest publicado no GitHub (`7bdc9f3998f3bc0db4d271c338a7c88f6b33ea1c0245a3fd435582e9b450075a`); `stripe.exe --version` retornou `stripe version 1.55.12`. Nenhum login foi executado pelo agente.

No PowerShell, habilitar o comando somente no terminal atual e autenticar manualmente:

~~~powershell
$env:Path = "C:\dev\fin-app\fin-backend\build\stripe-cli\1.55.12;$env:Path"
stripe --version
stripe login
~~~

Ao abrir outro terminal, repetir somente a linha do Path para disponibilizar o comando. Alternativamente usar o caminho completo do executável. Não foi alterado o Path permanente, a política de execução do PowerShell, arquivos .env ou credenciais. O login abre o navegador para Gabriel escolher e autorizar o sandbox FIN Desenvolvimento; não compartilhar chaves/código de autorização em prints. Se o painel exigir permissão do CLI, conferir Configurações → Equipe e segurança → MCP e CLI access no sandbox. Depois prosseguir com portal/listener e as variáveis abaixo.

Fonte do binário: https://github.com/stripe/stripe-cli/releases/tag/v1.55.12 .

1. Criar/selecionar um sandbox Stripe. Configurar produto FIN PRO com preço fixo mensal em BRL, quantidade 1, sem trial/cupons/prorrateio. O valor é escolha comercial de Gabriel; R$ 39,90 existe apenas nas fixtures de teste e não configura o produto real. Guardar o ID price_... para o backend.
2. Configurar o portal do cliente no sandbox: permitir atualização de cartão e cancelamento **ao fim do período**, desabilitar troca de plano/quantidade e cupons de retenção. Guardar o ID bpc_... da configuração. O backend recusa portal incompatível.
3. Configurar webhook snapshot da própria conta na versão **2026-10-28.endive**. Para localhost, usar Stripe CLI autenticado no sandbox, sem expor API pública. A documentação oficial do listen oferece --latest; antes do teste confirmar que a versão mostrada coincide com a versão fixa acima. Se mudou, alinhar a versão do sandbox/destino; não remover validação do backend. Não usar --live, --print-json ou desabilitar verificação TLS.

~~~powershell
stripe login
stripe listen --latest --events-from '@self' --events 'invoice.paid,invoice.payment_failed,customer.subscription.created,customer.subscription.updated,customer.subscription.deleted,customer.subscription.paused,customer.subscription.resumed,checkout.session.completed,checkout.session.expired,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,charge.refunded,charge.dispute.created,charge.dispute.updated,charge.dispute.closed' --forward-to http://localhost:8080/api/v1/billing/webhooks/stripe
~~~

O listen mostra o segredo de assinatura para copiar manualmente ao ambiente local. Não enviar esse segredo, chave secreta, cookies ou tokens na conversa/prints. Caso seja usado um destino HTTPS no painel em vez do CLI, usar o segredo daquele destino, a versão fixa e os mesmos tipos de evento. O proxy antigo do piloto Safari não publica as rotas de cobrança e não deve ser reutilizado para esse webhook.

4. No ambiente do backend (perfil dev, sem prod), configurar manualmente:

~~~properties
FIN_BILLING_ENABLED=true
FIN_STRIPE_SECRET_KEY=<chave secreta sandbox sk_test_ ou rk_test_ autorizada>
FIN_STRIPE_WEBHOOK_SECRET=<segredo whsec_ do listener/destino de teste>
FIN_STRIPE_PRICE_ID=<price_ do preço mensal BRL>
FIN_STRIPE_PORTAL_CONFIGURATION_ID=<bpc_ do portal de teste>
FIN_BILLING_FRONTEND_ORIGIN=http://localhost:3000
~~~

Ativar somente após preencher todas as configurações: chave live ou configuração incompleta falha explicitamente na inicialização, sem fallback. Não mudar credenciais Gemini/JWT/Google. Iniciar o backend aplica **V3 via Flyway**; ddl-auto continua validate. Não editar V1/V2 nem repetir V3 já aplicada.

5. No ambiente do frontend, habilitar NEXT_PUBLIC_BILLING_ENABLED=true, NEXT_PUBLIC_USE_MOCK=false e NEXT_PUBLIC_API_URL=http://localhost:8080; reiniciar pnpm dev. A flag é false por padrão. Toda cobrança só é acessível com JWT, salvo webhook que exige assinatura.

## Executar o piloto real sandbox

- Abrir Configurações. Conferir preço real retornado pela API e aviso de modo de testes. Assinar abre checkout.stripe.com. Usar somente cartões oficiais de teste Stripe, nunca cartão real.
- Testar pagamento aprovado, cancelamento da página e pagamento recusado. Retornar por ?billing=return nunca ativa PRO sozinho. A liberação depende do worker e de fatura/cobrança confirmadas.
- Conferir GET /api/v1/billing/subscription: paidUntil indica acesso pago; manualProExpiresAt é independente. Se a conta já tinha PRO manual, continuar PRO após reembolso não prova erro: conferir paidUntil e a maior validade, sem somar dias.
- Abrir Gerenciar assinatura, cancelar renovação e confirmar permanência até o fim pago. Testar mudança de cartão. O portal deve impedir troca de plano/quantidade e cancelamento imediato nesta entrega.
- Reenviar o mesmo evento de teste: apenas uma fatura/um período. Evento invoice.paid antigo após reembolso integral/disputa não deve reativar período revogado. Parcial deve aparecer para análise sem revogar automaticamente. Evento externo sem correspondência local nunca cria usuário nem dá PRO.
- Para renovar sem esperar um mês, usar ferramentas/test clocks do sandbox apenas com dados fictícios e conferir o período efetivamente pago. Não alterar o Clock do servidor de produção ou datas do banco para fingir confirmação.
- Exclusão de conta: primeiro bloquear login/refresh/JWT já emitido em todos os módulos; depois a Stripe cancela assinaturas ao excluir o customer remoto; só então apagar localmente. Falha remota retorna 503 e mantém exclusão pendente, retomada pelo worker. Conferir no sandbox que a recorrência terminou. Não declarar exclusão concluída pelo simples clique.

O piloto do sandbox foi concluído em 19/11/2026. O reenvio foi validado pela reentrega local de um evento real recebido pelo Stripe CLI, com o corpo e a assinatura originais preservados em memória. O reenvio pelo Workbench é uma operação separada e exige um destino cadastrado. O plano, as verificações de ponta a ponta e os casos cobertos pelos testes automatizados estão registrados em docs/implementation-plan.md.

## Recuperação e observabilidade local

No perfil `dev`, o pool de conexões define `default_storage_engine=InnoDB` na sessão. Isso permite aplicar a V3 em instalações locais cujo padrão do servidor é MyISAM, sem mudar a configuração global do MySQL.

Se a V3 já falhou com o erro MySQL 1071, confirme no banco de desenvolvimento que o histórico registra apenas a V3 como falha e que nenhuma tabela `billing_*` foi criada. Nesse caso, use o `repair` do Flyway no mesmo schema e com o mesmo conjunto de migrations, e execute a migração novamente com InnoDB na sessão. Se existirem tabelas parciais, inspecione-as antes de reparar.

Worker percorre inbox e clientes em lotes paginados; transações curtas e leases persistentes. Falhas conservam evento para retry; reinício do processo não perde o trabalho. Eventos ignorados/duplicados não concedem período adicional. Consultas operacionais no banco, sem expor endpoint administrativo:

~~~sql
SELECT status, COUNT(*) AS events, MAX(attempts) AS max_attempts,
       MIN(next_attempt_at) AS next_attempt
FROM billing_events GROUP BY status;
SELECT id, status, attempts, next_attempt_at
FROM billing_events WHERE status <> 'DONE'
ORDER BY next_attempt_at, id LIMIT 100;
SELECT user_id, deleting, lease_until, next_sync_at
FROM billing_customers WHERE deleting = b'1' LIMIT 100;
~~~

Se houver tentativas persistentes, verificar configuração/preço/versão/destino e disponibilidade do provedor. Não conceder PRO manualmente como resposta automática a evento inválido. Operações incertas de criação de customer não são repetidas automaticamente após 23 h (janela Stripe 24 h); requerem reconciliação operacional. Não criar novo vínculo às cegas.

Limites do piloto: reconciliação exige listas completas de até 100 assinaturas/faturas/pagamentos/disputas por consulta; has_more causa falha fechada, sem truncar nem conceder período parcial. SDK usa uma tentativa de rede por chamada, conexão 5 s e leitura 10 s. Lease de cliente 180 s, evento 240 s; fencing impede gravação tardia. O checkout dura 35 minutos para deixar margem ao mínimo de 30 minutos da Stripe; a mesma intenção ou um checkout pronto ainda aberto é reutilizado. Pagamentos não se baseiam no timestamp/ordem do webhook. Casos de escala além do piloto e retenção/alertas operacionais devem ser avaliados antes da liberação real.

## Validação automática

- mvnw.cmd -U clean compile e mvnw.cmd test; nesta máquina usar JDK 25 e Docker ativo. Saída isolada build/phase5-verification pelo POM temporário que altera somente build.directory, removido ao fim.
- BillingIntegrationTest: migration/validate, assinatura HMAC real de fixtures, inbox, isolamento, concorrência, lease/fencing, períodos, cancelamento/reembolso, repetição, falha remota e JWT real bloqueado durante exclusão.
- StripeBillingGatewayTest: HTTP local com SDK oficial, cabeçalhos/idempotência/preço, prova de fatura/cartão/customer, reembolsos/disputas, URLs/portal, assinatura falsa/antiga/futura/live/Connect e nenhuma chamada real.
- BillingConfigurationTest: dev opt-in; ausente em prod e dev+prod; chaves live rejeitadas.
- Exportar OpenAPI com BillingIntegrationTest e -Dfin.exportOpenapi=true. O contexto habilita IA e Stripe com mocks para preservar os dois contratos no snapshot. Arquivo build/phase5-openapi.json. Nunca gerar tipos de uma instância com uma das features desligada.
- Frontend: pnpm test:api, pnpm build, scripts/billing-smoke.mjs e ui-smoke.mjs --auth-only, com transporte interceptado. O teste de navegador não autentica nem cobra na Stripe e não comprova o piloto real.

Fontes: SDK https://github.com/stripe/stripe-java ; webhooks https://docs.stripe.com/webhooks ; CLI https://docs.stripe.com/cli/listen ; portal https://docs.stripe.com/customer-management/configure-portal ; cliente https://docs.stripe.com/api/customers/delete .
