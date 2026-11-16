package com.finance.ai.service;

import com.finance.account.repository.AccountRepository;
import com.finance.category.repository.CategoryRepository;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.ai.exception.AiException;
import com.finance.ai.model.ExtractedTransaction;
import com.finance.ai.model.ExtractionCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AiCatalogService {
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final CreditCardRepository cards;

    public AiCatalogService(AccountRepository accounts, CategoryRepository categories, CreditCardRepository cards) {
        this.accounts = accounts; this.categories = categories; this.cards = cards;
    }

    @Transactional(readOnly = true)
    public ExtractionCatalog load(Long userId) {
        var page = PageRequest.of(0, 100, Sort.by("id"));
        String prefix = UUID.randomUUID().toString();
        var categoryPage = categories.findAllByUserIdAndDeletedFalse(userId, page);
        var accountPage = accounts.findAllByUserIdAndArchivedFalse(userId, page);
        var cardPage = cards.findAllByUserIdAndArchivedFalse(userId, page);
        checkSize(categoryPage); checkSize(accountPage); checkSize(cardPage);
        Map<String, ExtractionCatalog.Entry> categoryRefs = new LinkedHashMap<>();
        Map<String, ExtractionCatalog.Entry> accountRefs = new LinkedHashMap<>();
        Map<String, ExtractionCatalog.Entry> cardRefs = new LinkedHashMap<>();
        for (var value : categoryPage) categoryRefs.put(prefix + "-c" + categoryRefs.size(),
                new ExtractionCatalog.Entry(value.getId(), value.getName(), value.getType()));
        for (var value : accountPage) accountRefs.put(prefix + "-a" + accountRefs.size(),
                new ExtractionCatalog.Entry(value.getId(), value.getName(), null));
        for (var value : cardPage) cardRefs.put(prefix + "-k" + cardRefs.size(),
                new ExtractionCatalog.Entry(value.getId(), value.getName(), null));
        return new ExtractionCatalog(categoryRefs, accountRefs, cardRefs);
    }

    @Transactional(readOnly = true)
    public Resolved validate(Long userId, ExtractionCatalog catalog, ExtractedTransaction extracted) {
        Long categoryId = resolve(catalog.categories(), extracted.categoryRef());
        Long accountId = resolve(catalog.accounts(), extracted.accountRef());
        Long cardId = resolve(catalog.creditCards(), extracted.creditCardRef());
        if (categoryId != null) {
            var category = categories.findByIdAndUserIdAndDeletedFalse(categoryId, userId).orElseThrow(AiCatalogService::notFound);
            if (extracted.type() != null && category.getType() != extracted.type()) throw AiException.invalidResponse();
        }
        if (accountId != null && accounts.findByIdAndUserId(accountId, userId)
                .filter(value -> !value.isArchived()).isEmpty()) throw notFound();
        if (cardId != null && cards.findByIdAndUserId(cardId, userId)
                .filter(value -> !value.isArchived()).isEmpty()) throw notFound();
        return new Resolved(categoryId, accountId, cardId);
    }

    private Long resolve(Map<String, ExtractionCatalog.Entry> values, String ref) {
        if (ref == null) return null;
        var entry = values.get(ref);
        if (entry == null) throw AiException.invalidResponse();
        return entry.id();
    }

    private void checkSize(Page<?> page) {
        if (page.hasNext()) throw new AiException(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_TOO_LARGE",
                "O catálogo excede o limite de contexto da IA. Preencha manualmente.");
    }

    private static AiException notFound() {
        return new AiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "Um recurso selecionado não está mais disponível. Revise o preenchimento manualmente.");
    }

    public record Resolved(Long categoryId, Long accountId, Long creditCardId) { }
}
