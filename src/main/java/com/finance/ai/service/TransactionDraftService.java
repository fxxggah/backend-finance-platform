package com.finance.ai.service;

import com.finance.ai.audio.AudioValidator;
import com.finance.ai.dto.TransactionDraftResponse;
import com.finance.ai.exception.AiException;
import com.finance.ai.extractor.ExtractionParser;
import com.finance.ai.extractor.TransactionExtractor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

/** Orquestração sem transação de banco durante a chamada externa e sem escrita financeira. */
public class TransactionDraftService {
    private final AudioValidator audioValidator;
    private final AiCatalogService catalogs;
    private final TransactionExtractor extractor;
    private final ExtractionParser parser;
    private final Clock clock;
    private final AiQuotaService quotas;

    public TransactionDraftService(AudioValidator audioValidator, AiCatalogService catalogs,
            TransactionExtractor extractor, ExtractionParser parser, Clock clock, AiQuotaService quotas) {
        this.audioValidator = audioValidator; this.catalogs = catalogs;
        this.extractor = extractor; this.parser = parser; this.clock = clock;
        this.quotas = quotas;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionDraftResponse create(Long userId, byte[] bytes, String key) {
        quotas.checkAccess(userId);
        AiQuotaService.validateKey(key);
        var audio = audioValidator.validate(bytes);
        var catalog = catalogs.load(userId);
        Long reservation = quotas.reserve(userId, key, bytes);
        try {
            var result = extract(userId, audio, catalog);
            quotas.succeed(userId, reservation);
            return result;
        } catch (RuntimeException ex) {
            try { quotas.fail(userId, reservation, ex instanceof AiException ai && "AI_RATE_LIMITED".equals(ai.code())); }
            catch (RuntimeException ignored) { /* Reserva permanece limitada pelo lease em caso de falha do banco. */ }
            if (ex instanceof AiException ai) throw ai;
            throw new AiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                    "Não foi possível concluir o rascunho. Tente depois ou preencha manualmente.");
        }
    }

    private TransactionDraftResponse extract(Long userId, com.finance.ai.model.AudioInput audio,
            com.finance.ai.model.ExtractionCatalog catalog) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        var extracted = parser.parse(extractor.extract(audio, catalog, today));
        if (extracted.date() != null && extracted.date().isAfter(today.plusYears(1))) throw AiException.invalidResponse();
        var resolved = catalogs.validate(userId, catalog, extracted);
        Integer installments = extracted.installments() == null ? 1 : extracted.installments();
        LocalDate date = extracted.date() == null ? today : extracted.date();
        var missing = new ArrayList<String>();
        if (extracted.type() == null) missing.add("type");
        if (extracted.amount() == null) missing.add("amount");
        if (resolved.categoryId() == null) missing.add("categoryId");
        if (resolved.accountId() == null && resolved.creditCardId() == null) missing.add("paymentSource");
        return new TransactionDraftResponse(extracted.type(), extracted.amount(), date,
                resolved.categoryId(), resolved.accountId(), resolved.creditCardId(), installments, missing, true,
                extracted.description());
    }
}
