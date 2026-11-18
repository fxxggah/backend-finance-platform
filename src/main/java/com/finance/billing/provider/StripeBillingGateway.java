package com.finance.billing.provider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.finance.billing.config.BillingProperties;
import com.finance.billing.exception.BillingException;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.net.*;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.stripe.net.ApiResource.RequestMethod.*;

/** API fixa, SDK oficial e nenhuma URL/chave arbitrária recebida do navegador. */
public class StripeBillingGateway implements BillingGateway {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> TYPES = Set.of("invoice.paid", "invoice.payment_failed", "customer.subscription.created",
            "customer.subscription.updated", "customer.subscription.deleted", "customer.subscription.paused", "customer.subscription.resumed",
            "checkout.session.completed", "checkout.session.expired", "checkout.session.async_payment_succeeded",
            "checkout.session.async_payment_failed", "charge.refunded", "charge.dispute.created", "charge.dispute.updated", "charge.dispute.closed");
    private final BillingProperties properties;
    private final StripeClient client;
    private final Clock clock;
    public StripeBillingGateway(BillingProperties properties, Clock clock) {
        this(properties, clock, StripeClient.builder().setApiKey(properties.getSecretKey()).build());
    }
    StripeBillingGateway(BillingProperties properties, Clock clock, StripeClient client) {
        this.properties = properties; this.clock = clock; this.client = client;
        if (!(properties.getSecretKey().startsWith("sk_test_") || properties.getSecretKey().startsWith("rk_test_"))
                || !properties.getWebhookSecret().startsWith("whsec_") || !properties.getPriceId().matches("price_[A-Za-z0-9]+")
                || !properties.getPortalConfigurationId().matches("bpc_[A-Za-z0-9]+")) throw BillingException.unavailable();
        URI origin = URI.create(properties.getFrontendOrigin());
        if (origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || !properties.getFrontendOrigin().equals(origin.getScheme() + "://" + origin.getAuthority())
                || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()) && Set.of("localhost", "127.0.0.1").contains(origin.getHost())))
            throw BillingException.unavailable();
    }
    @Override public Plan plan() {
        return readPlan(true);
    }
    private Plan readPlan(boolean mustBeActive) {
        var value = request(GET, "/v1/prices/" + properties.getPriceId(), Map.of(), null);
        sandbox(value); require((!mustBeActive || value.path("active").asBoolean()) && properties.getPriceId().equals(text(value, "id"))
                && "brl".equals(text(value, "currency")) && "recurring".equals(text(value, "type"))
                && "month".equals(value.path("recurring").path("interval").asText())
                && number(value.path("recurring"), "interval_count") == 1
                && "licensed".equals(value.path("recurring").path("usage_type").asText())
                && "per_unit".equals(text(value, "billing_scheme")) && value.path("transform_quantity").isNull());
        long amount = number(value, "unit_amount"); require(amount > 0 && amount <= 99_999_999_999L);
        return new Plan(properties.getPriceId(), BigDecimal.valueOf(amount, 2));
    }
    @Override public String createCustomer(String reference) {
        var value = request(POST, "/v1/customers", Map.of("metadata[fin_reference]", reference), "fin-customer-" + reference);
        sandbox(value); require(reference.equals(value.path("metadata").path("fin_reference").asText()));
        return id(value, "cus_");
    }
    @Override public Checkout checkout(String customerId, String reference, String operation, Instant expiresAt) {
        plan();
        var parameters = new LinkedHashMap<String,String>();
        parameters.put("customer", customerId); parameters.put("mode", "subscription");
        parameters.put("line_items[0][price]", properties.getPriceId()); parameters.put("line_items[0][quantity]", "1");
        parameters.put("subscription_data[metadata][fin_reference]", reference);
        parameters.put("metadata[fin_reference]", reference); parameters.put("client_reference_id", reference);
        parameters.put("success_url", properties.getFrontendOrigin() + "/settings?billing=return");
        parameters.put("cancel_url", properties.getFrontendOrigin() + "/settings?billing=canceled");
        parameters.put("expires_at", Long.toString(expiresAt.getEpochSecond()));
        var value = request(POST, "/v1/checkout/sessions", parameters, "fin-checkout-" + operation);
        sandbox(value); require(customerId.equals(text(value, "customer")) && "subscription".equals(text(value, "mode")));
        var methods = value.get("allowed_payment_method_types");
        if (methods == null || methods.isNull()) methods = value.path("payment_method_types");
        if (!methods.isArray() || methods.size() != 1 || !"card".equals(methods.get(0).textValue())) {
            throw new BillingException(HttpStatus.BAD_GATEWAY, "BILLING_CHECKOUT_CARD_REQUIRED",
                    "O checkout está indisponível. A configuração da Stripe deve permitir apenas cartão.");
        }
        String url = text(value, "url"); hosted(url, "checkout.stripe.com");
        return new Checkout(id(value, "cs_test_"), url, instant(value, "expires_at"));
    }
    @Override public String portal(String customerId) {
        var config = request(GET, "/v1/billing_portal/configurations/" + properties.getPortalConfigurationId(), Map.of(), null);
        sandbox(config); require(config.path("active").asBoolean()
                && !config.path("features").path("subscription_update").path("enabled").asBoolean(true)
                && config.path("features").path("subscription_cancel").path("enabled").asBoolean()
                && "at_period_end".equals(config.path("features").path("subscription_cancel").path("mode").asText()));
        var value = request(POST, "/v1/billing_portal/sessions", Map.of("customer", customerId,
                "configuration", properties.getPortalConfigurationId(), "return_url", properties.getFrontendOrigin() + "/settings"), null);
        String url = text(value, "url"); hosted(url, "billing.stripe.com"); return url;
    }
    @Override public Snapshot snapshot(String customerId, String reference) {
        var customer = request(GET, "/v1/customers/" + safeId(customerId), Map.of(), null);
        sandbox(customer); require(customerId.equals(text(customer, "id")) && !customer.path("deleted").asBoolean()
                && reference.equals(customer.path("metadata").path("fin_reference").asText()));
        Plan plan = readPlan(false);
        var subscriptions = new ArrayList<Subscription>(); var known = new HashSet<String>();
        for (var value : list("/v1/subscriptions", Map.of("customer", customerId, "status", "all", "limit", "100"))) {
            sandbox(value); require(customerId.equals(text(value, "customer")));
            if (!reference.equals(value.path("metadata").path("fin_reference").asText())) throw BillingException.review();
            var items = array(value.path("items")); require(items.size() == 1);
            var item = items.get(0); require(plan.id().equals(item.path("price").path("id").asText()) && number(item, "quantity") == 1);
            String subId = id(value, "sub_"); known.add(subId);
            String status = text(value, "status");
            require(Set.of("incomplete", "incomplete_expired", "trialing", "active", "past_due", "canceled", "unpaid", "paused").contains(status));
            subscriptions.add(new Subscription(subId, status, value.path("cancel_at_period_end").asBoolean(), instant(item, "current_period_end")));
        }
        var invoices = new ArrayList<Invoice>();
        for (var invoice : list("/v1/invoices", Map.of("customer", customerId, "limit", "100"))) {
            sandbox(invoice); require(customerId.equals(text(invoice, "customer")));
            String subId = invoice.path("parent").path("subscription_details").path("subscription").asText();
            if (!known.contains(subId) || !"paid".equals(invoice.path("status").asText())) continue;
            var lines = array(invoice.path("lines")); require(lines.size() == 1);
            var line = lines.get(0); require(plan.id().equals(line.path("pricing").path("price_details").path("price").asText())
                    && number(line, "quantity") == 1 && !line.path("parent").path("subscription_item_details").path("proration").asBoolean());
            Instant start = instant(line.path("period"), "start"), end = instant(line.path("period"), "end");
            require(end.isAfter(start) && Duration.between(start, end).toDays() >= 27 && Duration.between(start, end).toDays() <= 32);
            long amount = plan.amount().movePointRight(2).longValueExact();
            require("brl".equals(text(invoice, "currency")) && number(invoice, "amount_paid") == amount
                    && number(invoice, "total") == amount && number(invoice, "amount_remaining") == 0
                    && number(line, "amount") == amount);
            String invoiceId = id(invoice, "in_");
            var payments = list("/v1/invoice_payments", Map.of("invoice", invoiceId, "status", "paid", "limit", "100"));
            require(payments.size() == 1);
            var payment = payments.get(0); require(number(payment, "amount_paid") == amount && "paid".equals(text(payment, "status"))
                    && invoiceId.equals(text(payment, "invoice")) && "brl".equals(text(payment, "currency"))
                    && "payment_intent".equals(payment.path("payment").path("type").asText()));
            var intent = request(GET, "/v1/payment_intents/" + safeId(payment.path("payment").path("payment_intent").asText()), Map.of(), null);
            sandbox(intent); require(customerId.equals(text(intent, "customer")) && "succeeded".equals(text(intent, "status"))
                    && "brl".equals(text(intent, "currency")) && number(intent, "amount_received") == amount);
            var charge = request(GET, "/v1/charges/" + safeId(text(intent, "latest_charge")), Map.of(), null);
            sandbox(charge); require(customerId.equals(text(charge, "customer")) && charge.path("paid").asBoolean()
                    && charge.path("captured").asBoolean() && "succeeded".equals(text(charge, "status"))
                    && "brl".equals(text(charge, "currency")) && number(charge, "amount") == amount
                    && "card".equals(charge.path("payment_method_details").path("type").asText()));
            long refunded = number(charge, "amount_refunded"); require(refunded >= 0 && refunded <= amount);
            boolean revoked = refunded == amount || charge.path("refunded").asBoolean();
            if (charge.path("disputed").asBoolean()) {
                var disputes = list("/v1/disputes", Map.of("charge", id(charge, "ch_"), "limit", "100"));
                revoked |= disputes.isEmpty() || disputes.stream().anyMatch(d -> !Set.of("won", "warning_closed").contains(d.path("status").asText()));
            }
            invoices.add(new Invoice(invoiceId, subId, id(charge, "ch_"), plan.amount(), start, end,
                    revoked, refunded > 0 && refunded < amount));
        }
        return new Snapshot(customerId, reference, List.copyOf(subscriptions), List.copyOf(invoices));
    }
    @Override public String eventCustomer(Event event) {
        String path = event.type().startsWith("customer.subscription.") ? "/v1/subscriptions/"
                : event.type().startsWith("invoice.") ? "/v1/invoices/"
                : event.type().startsWith("checkout.session.") ? "/v1/checkout/sessions/"
                : event.type().startsWith("charge.dispute.") ? "/v1/disputes/" : "/v1/charges/";
        var value = request(GET, path + safeId(event.resourceId()), Map.of(), null); sandbox(value);
        if (event.type().startsWith("charge.dispute.")) {
            value = request(GET, "/v1/charges/" + safeId(text(value, "charge")), Map.of(), null); sandbox(value);
        }
        return text(value, "customer");
    }
    @Override public Event verify(byte[] payload, String signature) {
        try {
            if (payload.length > 256 * 1024 || signature == null || signature.length() > 4096) throw new IllegalArgumentException();
            String body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(payload)).toString();
            Webhook.Signature.verifyHeader(body, signature, properties.getWebhookSecret(), 300, clock);
            var times = Arrays.stream(signature.split(",")).filter(s -> s.startsWith("t=")).toList();
            if (times.size() != 1) throw new IllegalArgumentException();
            long signedAt = Long.parseLong(times.getFirst().substring(2)), now = clock.instant().getEpochSecond();
            if (signedAt < now - 300 || signedAt > now + 300) throw new IllegalArgumentException();
            var root = JSON.readTree(body);
            require(root.isObject() && root.path("livemode").isBoolean() && !root.path("livemode").asBoolean()
                    && (root.path("account").isMissingNode() || root.path("account").isNull()) && !root.hasNonNull("context")
                    && Stripe.API_VERSION.equals(root.path("api_version").asText()));
            String type = text(root, "type"); if (!TYPES.contains(type)) return null;
            return new Event(id(root, "evt_"), type, safeId(root.path("data").path("object").path("id").asText()));
        } catch (Exception ex) { throw new BillingException(HttpStatus.BAD_REQUEST, "INVALID_STRIPE_WEBHOOK", "Notificação de cobrança inválida."); }
    }
    @Override public void deleteCustomer(String customerId) {
        var previous = request(GET, "/v1/customers/" + safeId(customerId), Map.of(), null);
        require(customerId.equals(text(previous, "id")));
        if (previous.path("deleted").asBoolean()) return;
        sandbox(previous);
        var value = request(DELETE, "/v1/customers/" + safeId(customerId), Map.of(), null);
        require(customerId.equals(text(value, "id")) && value.path("deleted").asBoolean());
    }
    private List<JsonNode> list(String path, Map<String,String> parameters) {
        return array(request(GET, path, parameters, null));
    }
    private List<JsonNode> array(JsonNode value) {
        require(value.path("data").isArray() && !value.path("has_more").asBoolean(true) && value.path("data").size() <= 100);
        var result = new ArrayList<JsonNode>(); value.path("data").forEach(result::add); return result;
    }
    private JsonNode request(ApiResource.RequestMethod method, String path, Map<String,String> parameters, String key) {
        try {
            String form = parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
            var options = RawRequestOptions.builder().setConnectTimeout(5000).setReadTimeout(10_000).setMaxNetworkRetries(0)
                    .setAdditionalHeaders(Map.of("Content-Type", "application/x-www-form-urlencoded", "Stripe-Version", Stripe.API_VERSION));
            if (key != null) options.setIdempotencyKey(key);
            var response = client.rawRequest(method, method == GET && !form.isEmpty() ? path + "?" + form : path,
                    method == POST ? form : null, options.build());
            if (response.code() < 200 || response.code() >= 300 || response.body().length() > 4 * 1024 * 1024) throw BillingException.unavailable();
            var result = JSON.readTree(response.body()); require(result.isObject()); return result;
        } catch (BillingException ex) { throw ex; }
        catch (Exception ex) { throw BillingException.unavailable(); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void sandbox(JsonNode value) { require(value.path("livemode").isBoolean() && !value.path("livemode").asBoolean()); }
    private static String text(JsonNode node, String name) { require(node.path(name).isTextual()); return node.path(name).textValue(); }
    private static String safeId(String value) { require(value != null && value.matches("[A-Za-z0-9_]{1,255}")); return value; }
    private static String id(JsonNode value, String prefix) { String id = safeId(text(value, "id")); require(id.startsWith(prefix)); return id; }
    private static long number(JsonNode node, String name) { require(node.path(name).isIntegralNumber() && node.path(name).canConvertToLong()); return node.path(name).longValue(); }
    private static Instant instant(JsonNode node, String name) { return Instant.ofEpochSecond(number(node, name)); }
    private static void hosted(String url, String host) { URI value = URI.create(url); require("https".equals(value.getScheme()) && host.equals(value.getHost()) && value.getUserInfo() == null && value.getPort() == -1); }
    private static void require(boolean value) { if (!value) throw BillingException.invalid(); }
}
