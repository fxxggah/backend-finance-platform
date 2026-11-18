package com.finance.billing.provider;

import com.finance.billing.config.BillingProperties;
import com.finance.billing.exception.BillingException;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class StripeBillingGatewayTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    static final String SECRET = "whsec_synthetic_fixture_only";
    static BillingProperties properties() {
        var p = new BillingProperties(); p.setSecretKey("sk_test_synthetic_fixture_only"); p.setWebhookSecret(SECRET);
        p.setPriceId("price_fixture"); p.setPortalConfigurationId("bpc_fixture"); return p;
    }
    static String payload(String extra) {
        return "{\"id\":\"evt_fixture\",\"type\":\"invoice.paid\",\"api_version\":\"" + Stripe.API_VERSION
                + "\",\"livemode\":false,\"data\":{\"object\":{\"id\":\"in_fixture\"}}" + extra + "}";
    }
    static String signature(String body, long timestamp) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void verifiesOriginalBytesUsingSdkWithoutTrustingEventStatusOrBrowser() throws Exception {
        var gateway = new StripeBillingGateway(properties(), CLOCK);
        String body = payload("");
        var event = gateway.verify(body.getBytes(StandardCharsets.UTF_8), signature(body, CLOCK.instant().getEpochSecond()));
        assertThat(event.id()).isEqualTo("evt_fixture"); assertThat(event.resourceId()).isEqualTo("in_fixture");
        assertThatThrownBy(() -> gateway.verify(body.replace("in_fixture", "in_other").getBytes(StandardCharsets.UTF_8),
                signature(body, CLOCK.instant().getEpochSecond()))).isInstanceOf(BillingException.class).hasMessageNotContaining("in_other");
        for (String invalid : new String[]{payload(",\"account\":\"acct_other\""), payload(",\"context\":\"acct_other\""),
                payload("").replace("false", "true"), payload("").replace(Stripe.API_VERSION, "2020-01-01"), payload(",\"livemode\":false")}) {
            assertThatThrownBy(() -> gateway.verify(invalid.getBytes(StandardCharsets.UTF_8), signature(invalid, CLOCK.instant().getEpochSecond())))
                    .isInstanceOf(BillingException.class).extracting("code").isEqualTo("INVALID_STRIPE_WEBHOOK");
        }
    }
    @ParameterizedTest @ValueSource(longs={-301,301,3600})
    void rejectsOldAndFutureSignedEvents(long delta) throws Exception {
        var gateway = new StripeBillingGateway(properties(), CLOCK); String body = payload("");
        assertThatThrownBy(() -> gateway.verify(body.getBytes(StandardCharsets.UTF_8), signature(body, CLOCK.instant().getEpochSecond()+delta)))
                .isInstanceOf(BillingException.class);
    }
    @Test void refusesLiveKeysBeforeAnyNetworkAndRejectsMissingSignature() {
        var p = properties(); p.setSecretKey("sk_live_synthetic_fixture");
        assertThatThrownBy(() -> new StripeBillingGateway(p,CLOCK)).isInstanceOf(BillingException.class);
        var gateway = new StripeBillingGateway(properties(),CLOCK);
        assertThatThrownBy(() -> gateway.verify(payload("").getBytes(StandardCharsets.UTF_8),null)).isInstanceOf(BillingException.class);
    }
    private static final String PLAN = """
            {"id":"price_fixture","livemode":false,"active":true,"currency":"brl","type":"recurring",
            "unit_amount":3990,"billing_scheme":"per_unit","transform_quantity":null,
            "recurring":{"interval":"month","interval_count":1,"usage_type":"licensed"}}
            """;
    @Test void authoritativeInvoiceAndChargeValidationHandlesRefundsDisputesAndRejectsForeignData() throws Exception {
        var scenario = new java.util.concurrent.atomic.AtomicReference<>("paid");
        long start=CLOCK.instant().getEpochSecond(), end=start+30*86400L;
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange->{
            String path=exchange.getRequestURI().getPath(), mode=scenario.get(), response;
            if(path.equals("/v1/prices/price_fixture")) response=PLAN;
            else if(path.equals("/v1/customers/cus_fixture")) response="{\"id\":\"cus_fixture\",\"livemode\":false,\"metadata\":{\"fin_reference\":\"reference\"}}";
            else if(path.equals("/v1/subscriptions")) response="""
                    {"has_more":%s,"data":[{"id":"sub_fixture","customer":"cus_fixture","livemode":false,
                    "status":"active","cancel_at_period_end":false,"metadata":{"fin_reference":"reference"},
                    "items":{"has_more":false,"data":[{"quantity":1,"price":{"id":"price_fixture"},"current_period_end":%d}]}}]}
                    """.formatted(mode.equals("paginated"),end);
            else if(path.equals("/v1/invoices")) response="""
                    {"has_more":false,"data":[{"id":"in_fixture","livemode":false,"customer":"cus_fixture","status":"%s",
                    "currency":"brl","total":3990,"amount_paid":%d,"amount_remaining":0,
                    "parent":{"subscription_details":{"subscription":"sub_fixture"}},"lines":{"has_more":false,"data":[{
                    "quantity":1,"amount":3990,"pricing":{"price_details":{"price":"price_fixture"}},
                    "period":{"start":%d,"end":%d}}]}}]}
                    """.formatted(mode.equals("unpaid")?"open":"paid",mode.equals("wrongAmount")?1:3990,start,end);
            else if(path.equals("/v1/invoice_payments")) response="{\"has_more\":false,\"data\":[{\"amount_paid\":3990,\"invoice\":\"in_fixture\",\"currency\":\"brl\",\"status\":\"paid\",\"payment\":{\"type\":\"payment_intent\",\"payment_intent\":\"pi_fixture\"}}]}";
            else if(path.equals("/v1/payment_intents/pi_fixture")) response="{\"livemode\":false,\"customer\":\"cus_fixture\",\"status\":\"succeeded\",\"currency\":\"brl\",\"amount_received\":3990,\"latest_charge\":\"ch_fixture\"}";
            else if(path.equals("/v1/disputes")) response="{\"has_more\":false,\"data\":[{\"status\":\""+(mode.equals("won")?"won":"needs_response")+"\"}]}";
            else if(path.equals("/v1/charges/ch_fixture")) response="""
                    {"id":"ch_fixture","livemode":false,"customer":"%s","paid":true,"captured":true,"status":"succeeded",
                    "currency":"brl","amount":3990,"amount_refunded":%d,"refunded":%s,"disputed":%s,"payment_method_details":{"type":"card"}}
                    """.formatted(mode.equals("foreign")?"cus_other":"cus_fixture",mode.equals("full")?3990:mode.equals("partial")?100:0,
                    mode.equals("full"),mode.equals("disputed")||mode.equals("won"));
            else { exchange.sendResponseHeaders(404,-1);exchange.close();return; }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=StripeClient.builder().setApiKey(properties().getSecretKey()).setApiBase("http://127.0.0.1:"+server.getAddress().getPort()).build();
            var gateway=new StripeBillingGateway(properties(),CLOCK,client);
            var invoice=gateway.snapshot("cus_fixture","reference").invoices().getFirst();
            assertThat(invoice.amount()).isEqualByComparingTo("39.90");assertThat(invoice.revoked()).isFalse();
            for(String mode:List.of("full","disputed")) {scenario.set(mode);assertThat(gateway.snapshot("cus_fixture","reference").invoices().getFirst().revoked()).isTrue();}
            scenario.set("won");assertThat(gateway.snapshot("cus_fixture","reference").invoices().getFirst().revoked()).isFalse();
            scenario.set("partial");var partial=gateway.snapshot("cus_fixture","reference").invoices().getFirst();
            assertThat(partial.reviewRequired()).isTrue();assertThat(partial.revoked()).isFalse();
            scenario.set("unpaid");assertThat(gateway.snapshot("cus_fixture","reference").invoices()).isEmpty();
            for(String mode:List.of("foreign","wrongAmount","paginated")) {
                scenario.set(mode);assertThatThrownBy(()->gateway.snapshot("cus_fixture","reference")).isInstanceOf(BillingException.class);
            }
        } finally {server.stop(0);}
    }
    @Test void checkoutUsesOnlyServerPriceHostedUrlAndStableProviderIdempotency() throws Exception {
        var count = new AtomicInteger(); var bodies = new ArrayList<String>();
        var paymentMethods = new java.util.concurrent.atomic.AtomicReference<>(",\"allowed_payment_method_types\":[\"card\"]");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            count.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Stripe-Version")).isEqualTo(Stripe.API_VERSION);
            String response = PLAN;
            if (exchange.getRequestURI().getPath().contains("checkout")) {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("fin-checkout-operation");
                bodies.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                response = "{\"id\":\"cs_test_fixture\",\"url\":\"https://checkout.stripe.com/c/pay/fixture\",\"customer\":\"cus_fixture\",\"mode\":\"subscription\",\"livemode\":false,\"expires_at\":1800000000" + paymentMethods.get() + "}";
            }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            var client = StripeClient.builder().setApiKey(properties().getSecretKey()).setApiBase("http://127.0.0.1:"+server.getAddress().getPort()).build();
            var gateway = new StripeBillingGateway(properties(),CLOCK,client);
            var result = gateway.checkout("cus_fixture","opaque-reference","operation",Instant.ofEpochSecond(1800000000));
            assertThat(result.url()).startsWith("https://checkout.stripe.com/"); assertThat(count).hasValue(2);
            assertThat(java.net.URLDecoder.decode(bodies.getFirst(), StandardCharsets.UTF_8))
                    .contains("line_items[0][price]=price_fixture","line_items[0][quantity]=1")
                    .doesNotContain("payment_method_types", "userId", "trial", "coupon");
            paymentMethods.set(",\"allowed_payment_method_types\":null,\"payment_method_types\":[\"card\"]");
            assertThat(gateway.checkout("cus_fixture","opaque-reference","operation",Instant.ofEpochSecond(1800000000)).url())
                    .startsWith("https://checkout.stripe.com/");
            paymentMethods.set(",\"allowed_payment_method_types\":[\"card\",\"pix\"],\"payment_method_types\":[\"card\"]");
            assertThatThrownBy(() -> gateway.checkout("cus_fixture","opaque-reference","operation",Instant.ofEpochSecond(1800000000)))
                    .isInstanceOfSatisfying(BillingException.class, e -> assertThat(e.code()).isEqualTo("BILLING_CHECKOUT_CARD_REQUIRED"));
        } finally { server.stop(0); }
    }
    @Test void portalEnforcesEndOfPeriodCancellationAndNoPlanChangesOrForeignRedirects() throws Exception {
        var mode=new java.util.concurrent.atomic.AtomicReference<>("valid");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String response=exchange.getRequestURI().getPath().contains("configurations")
                    ? "{\"active\":true,\"livemode\":false,\"features\":{\"subscription_update\":{\"enabled\":"+mode.get().equals("update")+"},\"subscription_cancel\":{\"enabled\":true,\"mode\":\""+(mode.get().equals("immediate")?"immediately":"at_period_end")+"\"}}}"
                    : "{\"url\":\"https://"+(mode.get().equals("foreign")?"billing.stripe.com.evil.example":"billing.stripe.com")+"/p/fixture\"}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=StripeClient.builder().setApiKey(properties().getSecretKey()).setApiBase("http://127.0.0.1:"+server.getAddress().getPort()).build();
            var gateway=new StripeBillingGateway(properties(),CLOCK,client);
            assertThat(gateway.portal("cus_fixture")).startsWith("https://billing.stripe.com/");
            for(String value:List.of("update","immediate","foreign")) {mode.set(value);assertThatThrownBy(()->gateway.portal("cus_fixture")).isInstanceOf(BillingException.class);}
        } finally {server.stop(0);}
    }
    @ParameterizedTest @ValueSource(ints={400,429,500})
    void providerErrorsHaveNoRetryOrRawErrorLeak(int status) throws Exception {
        var calls = new AtomicInteger(); var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();byte[] bytes="{\"error\":{\"message\":\"private-provider-content\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=StripeClient.builder().setApiKey(properties().getSecretKey()).setApiBase("http://127.0.0.1:"+server.getAddress().getPort()).build();
            assertThatThrownBy(() -> new StripeBillingGateway(properties(),CLOCK,client).plan()).isInstanceOf(BillingException.class)
                    .hasNoCause().hasMessageNotContaining("private-provider-content"); assertThat(calls).hasValue(1);
        } finally {server.stop(0);}
    }
}
