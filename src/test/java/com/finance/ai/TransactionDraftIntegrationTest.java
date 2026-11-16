package com.finance.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.ai.extractor.TransactionExtractor;
import com.finance.ai.model.ExtractionCatalog;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.AccountType;
import com.finance.common.enums.TransactionType;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.name=ai-phase2-test", "app.ai.enabled=true",
        "app.ai.quota.global-attempts-per-minute=1000", "app.ai.quota.global-attempts-per-day=10000",
        "app.jwt.secret=phase-two-tests-only-secret-at-least-32-bytes", "app.jwt.access-token-expiration=900000",
        "app.jwt.refresh-token-expiration=604800000", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "app.google.client-id=phase-two-test-client", "app.cors.origins=http://localhost:3000",
        "spring.jpa.show-sql=false", "logging.level.org.springframework=WARN", "logging.level.com.finance=INFO"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@Import(TransactionDraftIntegrationTest.FixedTime.class)
class TransactionDraftIntegrationTest {
    private static final String URL = "/api/v1/ai/transaction-drafts";
    private static final ObjectMapper JSON = new ObjectMapper();
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired CreditCardRepository cards;
    @Autowired TransactionRepository transactions;
    @Autowired com.finance.ai.repository.AiQuotaGuardRepository quotaGuards;
    @Autowired com.finance.ai.config.AiQuotaProperties quotaLimits;
    @MockitoBean TransactionExtractor extractor;
    private User owner, other;
    private Account account, otherAccount;
    private Category category;
    private CreditCard card;
    private byte[] audio;

    @BeforeEach void setup() throws Exception {
        String suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("owner-" + suffix, suffix + "@example.com", "Owner", null));
        owner.setProExpiresAt(Instant.parse("2027-01-01T00:00:00Z")); owner = users.saveAndFlush(owner);
        var guard = quotaGuards.findById(1L).orElseThrow(); guard.setBlockedUntil(null); quotaGuards.saveAndFlush(guard);
        other = users.saveAndFlush(new User("other-" + suffix, "other-" + suffix + "@example.com", "Other", null));
        account = accounts.saveAndFlush(new Account(owner, "Carteira", AccountType.values()[0], BigDecimal.ZERO, null, null));
        otherAccount = accounts.saveAndFlush(new Account(other, "Conta privada", AccountType.values()[0], BigDecimal.ZERO, null, null));
        category = categories.saveAndFlush(new Category(owner, "Alimentação", TransactionType.EXPENSE, null, null));
        categories.saveAndFlush(new Category(other, "Categoria privada", TransactionType.EXPENSE, null, null));
        card = cards.saveAndFlush(new CreditCard(owner, "Cartão", new BigDecimal("1000.00"), 10, 20, null, null));
        cards.saveAndFlush(new CreditCard(other, "Cartão privado", new BigDecimal("1000.00"), 10, 20, null, null));
        var archivedAccount = new Account(owner, "Conta arquivada", AccountType.values()[0], BigDecimal.ZERO, null, null);
        archivedAccount.setArchived(true); accounts.saveAndFlush(archivedAccount);
        var deletedCategory = new Category(owner, "Categoria excluída", TransactionType.EXPENSE, null, null);
        deletedCategory.setDeleted(true); categories.saveAndFlush(deletedCategory);
        var archivedCard = new CreditCard(owner, "Cartão arquivado", new BigDecimal("1000.00"), 10, 20, null, null);
        archivedCard.setArchived(true); cards.saveAndFlush(archivedCard);
        try (var output = new ByteArrayOutputStream(); var stream = new AudioInputStream(
                new ByteArrayInputStream(new byte[32000]), new AudioFormat(16000, 16, 1, true, false), 16000)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output); audio = output.toByteArray();
        }
        when(extractor.extract(any(), any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(call.getArgument(2).toString()).isEqualTo("2026-11-13");
            return answer(call.getArgument(1), false, null);
        });
    }

    private String answer(ExtractionCatalog catalog, boolean useCard, Integer installments) throws Exception {
        var event = JSON.createObjectNode().put("description", "Compra na padaria").put("type", "EXPENSE").put("amount", "100.00").put("date", "2026-11-13");
        event.put("categoryRef", catalog.categories().keySet().iterator().next());
        if (useCard) { event.putNull("accountRef"); event.put("creditCardRef", catalog.creditCards().keySet().iterator().next()); }
        else { event.put("accountRef", catalog.accounts().keySet().iterator().next()); event.putNull("creditCardRef"); }
        if (installments == null) event.putNull("installments"); else event.put("installments", installments);
        var root = JSON.createObjectNode(); root.putArray("events").add(event); return JSON.writeValueAsString(root);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return post(URL).with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("audio/wav").content(audio);
    }

    @Test void isolatesContextIgnoresUserIdParameterAndDoesNotWriteUntilExplicitConfirmation() throws Exception {
        var captured = new AtomicReference<ExtractionCatalog>();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            ExtractionCatalog catalog = call.getArgument(1); captured.set(catalog);
            assertThat(catalog.accounts().values()).extracting(ExtractionCatalog.Entry::id).containsExactly(account.getId());
            assertThat(catalog.categories().values()).extracting(ExtractionCatalog.Entry::id).containsExactly(category.getId());
            assertThat(catalog.creditCards().values()).extracting(ExtractionCatalog.Entry::id).containsExactly(card.getId());
            return answer(catalog, false, null);
        }).when(extractor).extract(any(), any(), any());
        long before = transactions.count();
        String body = mvc.perform(request().param("userId", other.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId())).andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.description").value("Compra na padaria"))
                .andExpect(jsonPath("$.requiresConfirmation").value(true)).andExpect(jsonPath("$.missingFields").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(transactions.count()).isEqualTo(before);
        JsonNode draft = JSON.readTree(body);
        String confirmation = """
                {"type":"EXPENSE","amount":100.00,"date":"2026-11-13","categoryId":%d,"accountId":%d,"installments":1}
                """.formatted(draft.get("categoryId").longValue(), draft.get("accountId").longValue());
        mvc.perform(post("/api/v1/transactions").with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(confirmation)).andExpect(status().isCreated());
        assertThat(transactions.count()).isEqualTo(before + 1);
        assertThat(captured.get().accounts().keySet()).noneMatch(ref -> ref.equals(account.getId().toString()));
    }

    @Test void rejectsUnknownReferenceWithoutQueryingAnotherUsersResource() throws Exception {
        doAnswer(call -> answer(call.getArgument(1), false, null)
                .replaceAll("\"accountRef\":\"[^\"]+\"", "\"accountRef\":\"" + otherAccount.getId() + "\""))
                .when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("INVALID_AI_RESPONSE"));
    }

    @Test void resourceArchivedDuringExternalCallIsRevalidated() throws Exception {
        doAnswer(call -> {
            account.setArchived(true); accounts.saveAndFlush(account);
            return answer(call.getArgument(1), false, null);
        }).when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test void confirmationRevalidatesResourcesEvenAfterSuccessfulDraft() throws Exception {
        mvc.perform(request()).andExpect(status().isOk());
        account.setArchived(true); accounts.saveAndFlush(account);
        mvc.perform(post("/api/v1/transactions").with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EXPENSE","amount":100.00,"date":"2026-11-13","categoryId":%d,"accountId":%d}
                """.formatted(category.getId(), account.getId()))).andExpect(status().isUnprocessableEntity());
        assertThat(transactions.findAllByUserIdAndDeletedFalse(owner.getId(), PageRequest.of(0, 100))).isEmpty();
    }

    @Test void defaultsCardToOneAndAllowsReviewToThreeInstallmentsAsExistingObjectResponse() throws Exception {
        doAnswer(call -> answer(call.getArgument(1), true, null)).when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.missingFields").isEmpty());
        mvc.perform(post("/api/v1/transactions").with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EXPENSE","amount":100.00,"date":"2026-11-13","categoryId":%d,"creditCardId":%d,"installments":3}
                """.formatted(category.getId(), card.getId()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.installment.number").value(1)).andExpect(jsonPath("$.amount").value(33.33));
        var stored = transactions.findAllByUserIdAndDeletedFalse(owner.getId(), PageRequest.of(0, 100));
        assertThat(stored).hasSize(3);
        assertThat(stored.stream().map(value -> value.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
    }

    @Test void invalidOversizedAndUnauthenticatedRequestsNeverCallProvider() throws Exception {
        mvc.perform(post(URL).contentType("audio/wav").content(audio)).andExpect(status().isUnauthorized());
        mvc.perform(request().content(new byte[10])).andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(request().content(new byte[2 * 1024 * 1024 + 1])).andExpect(status().isContentTooLarge());
        mvc.perform(request().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(extractor);
    }

    @Test void missingFieldsAndMalformedResponsesAreControlled() throws Exception {
        doReturn("""
                {"events":[{"description":"Compra na padaria","type":null,"amount":"5.00","date":null,"installments":null,
                "categoryRef":null,"accountRef":null,"creditCardRef":null}]}
                """).when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(5.00))
                .andExpect(jsonPath("$.missingFields.length()").value(3))
                .andExpect(jsonPath("$.date").value("2026-11-13"))
                .andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.creditCardId").doesNotExist());
        doReturn("private-invalid-provider-output").when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value(
                "A IA retornou dados inválidos. Tente novamente ou preencha manualmente."));
    }

    @Test void multipleEventsAndProviderFailuresNeverPersistDraftOrExposeProviderContent() throws Exception {
        doAnswer(call -> {
            JsonNode one = JSON.readTree(answer(call.getArgument(1), false, 1)).get("events").get(0);
            var root = JSON.createObjectNode(); root.putArray("events").add(one).add(one);
            return JSON.writeValueAsString(root);
        }).when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MULTIPLE_TRANSACTIONS"))
                .andExpect(jsonPath("$.amount").doesNotExist());
        doThrow(new com.finance.ai.exception.AiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "AI_RATE_LIMITED", "A IA atingiu o limite temporário. Tente depois ou preencha manualmente."))
                .when(extractor).extract(any(), any(), any());
        mvc.perform(request()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"));
        assertThat(transactions.findAllByUserIdAndDeletedFalse(owner.getId(), PageRequest.of(0, 100))).isEmpty();
    }

    @Test void swaggerDescribesRawAudioAndResponse() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai/transaction-drafts'].post.requestBody.content['audio/webm']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/transaction-drafts'].post.responses['413']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/transaction-drafts'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/transaction-drafts'].post.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/transaction-drafts'].post.parameters[?(@.name == 'Idempotency-Key')].required")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.components.schemas.TransactionDraftResponse.properties.requiresConfirmation").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionDraftResponse.properties.description.maxLength").value(255))
                .andDo(result -> {
                    if (Boolean.getBoolean("fin.exportOpenapi")) {
                        java.nio.file.Files.writeString(java.nio.file.Path.of("build/phase4-openapi.json"),
                                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                    }
                });
    }

    @Test void basicAndMissingKeyAreRejectedAndProfileExposesEffectivePlan() throws Exception {
        mvc.perform(post(URL).with(jwt().jwt(value -> value.subject(other.getId().toString())))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("audio/wav").content(audio))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRO_REQUIRED"));
        mvc.perform(post(URL).with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .contentType("audio/wav").content(audio))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        mvc.perform(get("/api/v1/users/me").with(jwt().jwt(value -> value.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.proExpiresAt").exists());
        verifyNoInteractions(extractor);
    }

    @Test void repeatedSuccessfulRequestDoesNotCallProviderTwice() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = post(URL).with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .header("Idempotency-Key", key).contentType("audio/wav").content(audio);
        mvc.perform(first).andExpect(status().isOk());
        mvc.perform(post(URL).with(jwt().jwt(value -> value.subject(owner.getId().toString())))
                .header("Idempotency-Key", key).contentType("audio/wav").content(audio))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_REQUEST_ALREADY_USED"));
        verify(extractor, times(1)).extract(any(), any(), any());
    }

    @Test void personalLimitReturnsRetryAfterAndCorsSupportsIdempotencyHeader() throws Exception {
        int original = quotaLimits.getUserSuccessesPerDay(); quotaLimits.setUserSuccessesPerDay(1);
        try {
            mvc.perform(request().header("Origin", "http://localhost:3000")).andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
            mvc.perform(request()).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("USER_AI_QUOTA_EXCEEDED"))
                    .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[0-9]+")));
            verify(extractor, times(1)).extract(any(), any(), any());
            mvc.perform(options(URL).header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "idempotency-key,content-type,authorization"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Headers",
                            org.hamcrest.Matchers.containsString("idempotency-key")));
        } finally { quotaLimits.setUserSuccessesPerDay(original); }
    }
}
