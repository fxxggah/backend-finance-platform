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

    public TransactionDraftService(AudioValidator audioValidator, AiCatalogService catalogs,
            TransactionExtractor extractor, ExtractionParser parser, Clock clock) {
        this.audioValidator = audioValidator; this.catalogs = catalogs;
        this.extractor = extractor; this.parser = parser; this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionDraftResponse create(Long userId, byte[] bytes) {
        var audio = audioValidator.validate(bytes);
        var catalog = catalogs.load(userId);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        var extracted = parser.parse(extractor.extract(audio, catalog, today));
        if (extracted.date() != null && extracted.date().isAfter(today.plusYears(1))) throw AiException.invalidResponse();
        var resolved = catalogs.validate(userId, catalog, extracted);
        Integer installments = extracted.installments();
        if (resolved.accountId() != null && installments == null) installments = 1;
        var missing = new ArrayList<String>();
        if (extracted.type() == null) missing.add("type");
        if (extracted.amount() == null) missing.add("amount");
        if (extracted.date() == null) missing.add("date");
        if (resolved.categoryId() == null) missing.add("categoryId");
        if (resolved.accountId() == null && resolved.creditCardId() == null) missing.add("paymentSource");
        if (installments == null) missing.add("installments");
        return new TransactionDraftResponse(extracted.type(), extracted.amount(), extracted.date(),
                resolved.categoryId(), resolved.accountId(), resolved.creditCardId(), installments, missing, true);
    }
}
