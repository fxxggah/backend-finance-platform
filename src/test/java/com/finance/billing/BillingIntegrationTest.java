package com.finance.billing;

import com.finance.billing.config.BillingProperties;
import com.finance.billing.provider.*;
import com.finance.billing.service.*;
import com.finance.billing.repository.*;
import com.finance.billing.exception.BillingException;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import com.finance.user.service.UserService;
import com.finance.ai.service.AiQuotaService;
import com.finance.ai.exception.AiException;
import com.stripe.Stripe;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.name=billing-test","app.billing.enabled=true","app.billing.worker-enabled=false","app.ai.enabled=true",
        "app.billing.secret-key=sk_test_fixture","app.billing.webhook-secret=whsec_synthetic_fixture",
        "app.billing.price-id=price_fixture","app.billing.portal-configuration-id=bpc_fixture",
        "app.billing.frontend-origin=http://localhost:3000","app.jwt.secret=billing-test-fixture-only-at-least-32-bytes",
        "app.jwt.access-token-expiration=900","app.jwt.refresh-token-expiration=604800",
        "app.google.client-id=billing-test-client","app.cors.origins=http://localhost:3000",
        "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","logging.level.root=WARN"})
@ActiveProfiles("dev") @AutoConfigureMockMvc @Testcontainers @Import(BillingIntegrationTest.TimeConfig.class)
class BillingIntegrationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(Instant.parse("2026-11-16T12:00:00Z"));
        void advance(long seconds) { value.updateAndGet(i->i.plusSeconds(seconds)); }
        @Override public Instant instant() { return value.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock billingClock() { return new MutableClock(); } }
    @Autowired MockMvc mvc;
    @Autowired BillingService billing;
    @Autowired BillingStore store;
    @Autowired UserService userService;
    @Autowired AiQuotaService quota;
    @Autowired com.finance.security.JwtService jwtService;
    @Autowired MutableClock clock;
    @Autowired BillingProperties properties;
    @Autowired UserRepository users;
    @Autowired BillingCustomerRepository customers;
    @Autowired BillingInvoiceRepository invoices;
    @Autowired BillingEventRepository events;
    @MockitoBean BillingGateway gateway;
    @MockitoBean com.finance.ai.extractor.TransactionExtractor extractor;
    private User user,other;
    private String stage;
    private Instant start,end;
    private String subId,invoiceId,chargeId;
    private StripeBillingGateway verifier;

    @BeforeEach void setup() {
        clock.value.set(Instant.parse("2026-11-16T12:00:00Z"));
        String suffix=UUID.randomUUID().toString();
        user=users.saveAndFlush(new User("billing-"+suffix,suffix+"@example.test","Teste",null));
        other=users.saveAndFlush(new User("other-"+suffix,"other-"+suffix+"@example.test","Outro",null));
        stage="empty";start=clock.instant();end=start.plusSeconds(30*86400L);
        subId="sub_"+user.getId();invoiceId="in_"+user.getId();chargeId="ch_"+user.getId();
        verifier=new StripeBillingGateway(properties,clock);
        when(gateway.plan()).thenReturn(new BillingGateway.Plan("price_fixture",new BigDecimal("39.90")));
        when(gateway.createCustomer(anyString())).thenAnswer(a->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "cus_"+a.<String>getArgument(0).replace("-","");
        });
        when(gateway.snapshot(anyString(),anyString())).thenAnswer(a->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String customer=a.getArgument(0),ref=a.getArgument(1);
            if (stage.equals("empty")) return new BillingGateway.Snapshot(customer,ref,List.of(),List.of());
            return new BillingGateway.Snapshot(customer,ref,List.of(new BillingGateway.Subscription(subId,stage.equals("canceled")?"canceled":"active",stage.equals("canceling"),end)),
                List.of(new BillingGateway.Invoice(invoiceId,subId,chargeId,new BigDecimal("39.90"),start,end,stage.equals("refunded"),stage.equals("partial"))));
        });
        when(gateway.checkout(anyString(),anyString(),anyString(),any())).thenAnswer(a -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new BillingGateway.Checkout("cs_test_"+user.getId(),"https://checkout.stripe.com/c/pay/fixture",a.getArgument(3));
        });
        when(gateway.verify(any(),any())).thenAnswer(a->verifier.verify(a.getArgument(0),a.getArgument(1)));
        when(gateway.eventCustomer(any())).thenAnswer(a->customers.findByUserId(user.getId()).orElseThrow().getStripeCustomerId());
    }
    private String key() { return UUID.randomUUID().toString(); }
    private void checkout() { billing.checkout(user.getId(),key()); }
    private byte[] event(String id,String type) {
        return ("{\"id\":\""+id+"\",\"type\":\""+type+"\",\"api_version\":\""+Stripe.API_VERSION
                +"\",\"livemode\":false,\"data\":{\"object\":{\"id\":\""+(type.startsWith("invoice")?invoiceId:chargeId)+"\"}}}").getBytes(StandardCharsets.UTF_8);
    }
    private String sign(byte[] body) throws Exception {
        long timestamp=clock.instant().getEpochSecond();var mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+new String(body,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
    }
    private void deliver(String eventId,String type) throws Exception {
        byte[] bytes=event(eventId,type);
        mvc.perform(post("/api/v1/billing/webhooks/stripe").contentType("application/json").header("Stripe-Signature",sign(bytes)).content(bytes))
                .andExpect(status().isOk());
    }
    private void process(String eventId) { billing.process(store.claimEvent(events.findByStripeEventId(eventId).orElseThrow().getId())); }

    @Test void signedInboxGrantsOnlyAfterProcessingAndNeverDuplicatesPeriod() throws Exception {
        checkout();String eventId="evt_"+UUID.randomUUID().toString().replace("-","");stage="paid";
        deliver(eventId,"invoice.paid");deliver(eventId,"invoice.paid");
        assertThat(userService.get(user.getId()).plan()).isEqualTo("BASIC");
        assertThat(events.findByStripeEventId(eventId).orElseThrow().getStatus()).isEqualTo("PENDING");
        process(eventId);
        assertThat(userService.get(user.getId()).plan()).isEqualTo("PRO");
        assertThat(userService.get(user.getId()).proExpiresAt()).isEqualTo(end);
        assertThat(userService.get(other.getId()).plan()).isEqualTo("BASIC");
        quota.checkAccess(user.getId());assertThatThrownBy(()->quota.checkAccess(other.getId())).isInstanceOf(AiException.class);
        deliver(eventId,"invoice.paid");assertThat(store.claimEvent(events.findByStripeEventId(eventId).orElseThrow().getId())).isNull();
        assertThat(invoices.findAll().stream().filter(i->i.getStripeInvoiceId().equals(invoiceId)).count()).isEqualTo(1);
    }
    @Test void refundCannotBeUndoneByDelayedPaidEventAndManualGrantIsIndependent() throws Exception {
        checkout();stage="paid";billing.synchronize(user.getId());
        user.setProExpiresAt(end.plusSeconds(86400));users.saveAndFlush(user);
        stage="refunded";String refund="evt_refund"+user.getId();deliver(refund,"charge.refunded");process(refund);
        assertThat(billing.status(user.getId()).paidUntil()).isNull();
        assertThat(userService.get(user.getId()).proExpiresAt()).isEqualTo(end.plusSeconds(86400));
        String late="evt_late"+user.getId();deliver(late,"invoice.paid");process(late);
        assertThat(billing.status(user.getId()).paidUntil()).isNull();
        assertThat(invoices.findByStripeInvoiceIdAndUserId(invoiceId,user.getId()).orElseThrow().isRevoked()).isTrue();
    }
    @Test void cancellationAndPartialRefundDoNotExtendOrErasePaidPeriod() {
        checkout();stage="canceling";billing.synchronize(user.getId());
        assertThat(billing.status(user.getId()).cancelAtPeriodEnd()).isTrue();
        stage="partial";billing.synchronize(user.getId());assertThat(billing.status(user.getId()).reviewRequired()).isTrue();
        assertThat(userService.get(user.getId()).proExpiresAt()).isEqualTo(end);
        clock.advance(30*86400L);assertThat(userService.get(user.getId()).plan()).isEqualTo("BASIC");
        assertThatThrownBy(()->quota.checkAccess(user.getId())).isInstanceOf(AiException.class);
    }

    @Test void renewalKeepsOriginalExpiryUntilNewPeriodIsPaid() throws Exception {
        checkout();
        stage = "paid";
        billing.synchronize(user.getId());

        Instant firstPeriodEnd = end;
        Instant renewalEnd = firstPeriodEnd.plusSeconds(30 * 86400L);
        var firstInvoice = new BillingGateway.Invoice(invoiceId, subId, chargeId,
                new BigDecimal("39.90"), start, firstPeriodEnd, false, false);
        invoiceId += "_renewal";
        String failedEvent = "evt_renewal_failed" + user.getId();
        deliver(failedEvent, "invoice.payment_failed");
        doAnswer(a -> new BillingGateway.Snapshot(a.getArgument(0), a.getArgument(1),
                List.of(new BillingGateway.Subscription(subId, "past_due", false, renewalEnd)),
                List.of(firstInvoice))).when(gateway).snapshot(anyString(), anyString());
        process(failedEvent);
        assertThat(billing.status(user.getId()).paidUntil()).isEqualTo(firstPeriodEnd);

        clock.advance(firstPeriodEnd.getEpochSecond() - clock.instant().getEpochSecond());
        assertThat(userService.get(user.getId()).plan()).isEqualTo("BASIC");

        var paidRenewal = new BillingGateway.Invoice(invoiceId, subId, chargeId + "_renewal",
                new BigDecimal("39.90"), firstPeriodEnd, renewalEnd, false, false);
        doAnswer(a -> new BillingGateway.Snapshot(a.getArgument(0), a.getArgument(1),
                List.of(new BillingGateway.Subscription(subId, "active", false, renewalEnd)),
                List.of(firstInvoice, paidRenewal))).when(gateway).snapshot(anyString(), anyString());
        String paidEvent = "evt_renewal_paid" + user.getId();
        deliver(paidEvent, "invoice.paid");
        process(paidEvent);
        assertThat(userService.get(user.getId()).plan()).isEqualTo("PRO");
        assertThat(billing.status(user.getId()).paidUntil()).isEqualTo(renewalEnd);
    }
    @Test void sameOrNewCheckoutIntentReusesOnlyExistingHostedSession() {
        String key=key();var first=billing.checkout(user.getId(),key);
        assertThat(billing.checkout(user.getId(),key).url()).isEqualTo(first.url());
        assertThat(billing.checkout(user.getId(),key()).url()).isEqualTo(first.url());
        verify(gateway,times(1)).createCustomer(anyString());verify(gateway,times(1)).checkout(anyString(),anyString(),anyString(),any());
        stage="paid";assertThatThrownBy(()->billing.checkout(user.getId(),key())).isInstanceOf(BillingException.class).extracting("code").isEqualTo("SUBSCRIPTION_EXISTS");
    }
    @Test void providerOutageKeepsVerifiedEventForRetryWithoutGrantingAccess() throws Exception {
        checkout();String eventId="evt_retry"+user.getId();deliver(eventId,"invoice.paid");
        doThrow(BillingException.unavailable()).doAnswer(a->new BillingGateway.Snapshot(a.getArgument(0),a.getArgument(1),
                List.of(new BillingGateway.Subscription(subId,"active",false,end)),List.of(new BillingGateway.Invoice(invoiceId,subId,chargeId,
                new BigDecimal("39.90"),start,end,false,false)))).when(gateway).snapshot(anyString(),anyString());
        assertThatThrownBy(()->process(eventId)).isInstanceOf(BillingException.class);
        assertThat(events.findByStripeEventId(eventId).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(userService.get(user.getId()).plan()).isEqualTo("BASIC");
        clock.advance(61);process(eventId);assertThat(userService.get(user.getId()).plan()).isEqualTo("PRO");
    }
    @Test void paidFuturePeriodDoesNotGrantEarlyAndManualValidityIsNotAdded() {
        checkout();stage="paid";start=clock.instant().plusSeconds(86400);end=start.plusSeconds(30*86400L);
        billing.synchronize(user.getId());assertThat(userService.get(user.getId()).plan()).isEqualTo("BASIC");
        clock.advance(86400);assertThat(userService.get(user.getId()).proExpiresAt()).isEqualTo(end);
        user.setProExpiresAt(end.minusSeconds(1000));users.saveAndFlush(user);
        assertThat(userService.get(user.getId()).proExpiresAt()).isEqualTo(end);
    }
    @Test void concurrentRequestsCannotCreateTwoCheckouts() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(gateway.checkout(anyString(),anyString(),anyString(),any())).thenAnswer(a->{
            entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException();
            return new BillingGateway.Checkout("cs_test_concurrent","https://checkout.stripe.com/c/pay/concurrent",a.getArgument(3));
        });
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(()->billing.checkout(user.getId(),key()));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->billing.checkout(user.getId(),key())).isInstanceOf(BillingException.class).extracting("code").isEqualTo("BILLING_OPERATION_IN_PROGRESS");
            release.countDown();assertThat(first.get(10,TimeUnit.SECONDS).url()).contains("checkout.stripe.com");
        } finally {release.countDown();}
        verify(gateway,times(1)).checkout(anyString(),anyString(),anyString(),any());
    }
    @Test void staleWriterAndWrongCustomerCannotGrantAccess() {
        var old=store.claim(user.getId(),false);old=store.bind(old,"cus_fenced");clock.advance(181);
        var fresh=store.claim(user.getId(),false);var captured=old;
        assertThatThrownBy(()->store.apply(captured,new BillingGateway.Snapshot("cus_fenced",captured.reference(),List.of(),List.of())))
                .isInstanceOf(BillingException.class);
        assertThatThrownBy(()->store.apply(fresh,new BillingGateway.Snapshot("cus_other",fresh.reference(),List.of(),List.of())))
                .isInstanceOf(BillingException.class);
        assertThat(userService.get(user.getId()).plan()).isEqualTo("BASIC");store.release(fresh,false);
    }
    @Test void invalidSignatureAndTamperingNeverPersistOrContactProvider() throws Exception {
        long before=events.count();
        mvc.perform(post("/api/v1/billing/webhooks/stripe").contentType("application/json").content(event("evt_invalid","invoice.paid")))
                .andExpect(status().isBadRequest());assertThat(events.count()).isEqualTo(before);
        for(String endpoint:List.of("plan","subscription","checkout-sessions","portal-sessions")) {
            mvc.perform((endpoint.endsWith("sessions")?post("/api/v1/billing/"+endpoint):get("/api/v1/billing/"+endpoint)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/billing/checkout-sessions").with(jwt().jwt(j->j.subject(user.getId().toString())))
                .header("Idempotency-Key",key()).contentType("application/json").content("{\"userId\":1,\"amount\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/billing/subscription?userId="+other.getId()).with(jwt().jwt(j->j.subject(user.getId().toString()))))
                .andExpect(status().isBadRequest());verify(gateway,never()).checkout(anyString(),anyString(),anyString(),any());
    }
    @Test void deletionBlocksImmediatelyAndRetriesCancellationBeforeDeletingLocally() throws Exception {
        checkout();stage="paid";billing.synchronize(user.getId());
        String accessToken=jwtService.generateAccessToken(user.getId());
        mvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+accessToken)).andExpect(status().isOk());
        doThrow(BillingException.unavailable()).when(gateway).deleteCustomer(anyString());
        assertThatThrownBy(()->userService.delete(user.getId())).isInstanceOf(BillingException.class);
        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isFalse();
        mvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+accessToken)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->quota.checkAccess(user.getId())).isInstanceOf(AiException.class);
        doAnswer(a->{assertThat(users.existsById(user.getId())).isTrue();assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return null;})
                .when(gateway).deleteCustomer(anyString());
        billing.synchronize(user.getId());assertThat(users.existsById(user.getId())).isFalse();
        assertThat(users.existsById(other.getId())).isTrue();
    }
    @Test void swaggerExportsBillingContract() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/billing/checkout-sessions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/billing/webhooks/stripe'].post").exists())
                .andExpect(jsonPath("$.components.schemas.BillingPlanResponse.properties.amount").exists())
                .andExpect(jsonPath("$.paths['/api/v1/billing/plan'].get.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/BillingPlanResponse"))
                .andDo(r->{if(Boolean.getBoolean("fin.exportOpenapi"))java.nio.file.Files.writeString(java.nio.file.Path.of("build/phase5-openapi.json"),r.getResponse().getContentAsString(StandardCharsets.UTF_8));});
    }
}
