package com.finance.ai.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.TransactionType;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.ai.exception.AiException;
import com.finance.ai.model.ExtractedTransaction;
import com.finance.ai.model.ExtractionCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiCatalogServiceTest {
    @Test void loadsOnlyActiveUserResourcesAndCreatesFreshTemporaryRefs() {
        var accounts = mock(AccountRepository.class); var categories = mock(CategoryRepository.class);
        var cards = mock(CreditCardRepository.class);
        var account = new Account(); account.setId(9L); account.setName("Carteira");
        when(accounts.findAllByUserIdAndArchivedFalse(eq(7L), any())).thenReturn(new PageImpl<>(List.of(account)));
        when(categories.findAllByUserIdAndDeletedFalse(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        when(cards.findAllByUserIdAndArchivedFalse(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        var service = new AiCatalogService(accounts, categories, cards);
        var first = service.load(7L); var second = service.load(7L);
        assertThat(first.accounts().values()).extracting(ExtractionCatalog.Entry::id).containsExactly(9L);
        assertThat(first.accounts().keySet()).doesNotContainAnyElementsOf(second.accounts().keySet()).doesNotContain("9");
        verify(accounts, times(2)).findAllByUserIdAndArchivedFalse(eq(7L), argThat(page -> page.getPageSize() == 100));
    }

    @Test void refusesOversizedCatalogInsteadOfSilentlyTruncating() {
        var accounts = mock(AccountRepository.class); var categories = mock(CategoryRepository.class);
        var cards = mock(CreditCardRepository.class);
        when(accounts.findAllByUserIdAndArchivedFalse(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        when(cards.findAllByUserIdAndArchivedFalse(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        when(categories.findAllByUserIdAndDeletedFalse(eq(7L), any())).thenReturn(
                new PageImpl<>(java.util.Collections.nCopies(100, new Category()), PageRequest.of(0, 100), 101));
        assertThatThrownBy(() -> new AiCatalogService(accounts, categories, cards).load(7L))
                .isInstanceOf(AiException.class).extracting("code").isEqualTo("CATALOG_TOO_LARGE");
    }

    @Test void revalidatesWithUserIdAndRejectsUnknownRefsAndCategoryTypeMismatch() {
        var accounts = mock(AccountRepository.class); var categories = mock(CategoryRepository.class);
        var cards = mock(CreditCardRepository.class); var service = new AiCatalogService(accounts, categories, cards);
        var catalog = new ExtractionCatalog(Map.of("c", new ExtractionCatalog.Entry(4L, "Salário", TransactionType.INCOME)),
                Map.of("a", new ExtractionCatalog.Entry(9L, "Conta", null)), Map.of());
        var unknown = new ExtractedTransaction(TransactionType.EXPENSE, null, null, null, null, "9", null, "Despesa");
        assertThatThrownBy(() -> service.validate(7L, catalog, unknown)).isInstanceOf(AiException.class)
                .extracting("code").isEqualTo("INVALID_AI_RESPONSE");
        verifyNoInteractions(accounts, categories, cards);
        var value = new ExtractedTransaction(null, null, null, null, null, "a", null, "Transação");
        when(accounts.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());
        var selected = value;
        assertThatThrownBy(() -> service.validate(7L, catalog, selected)).isInstanceOf(AiException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
        var category = new Category(); category.setType(TransactionType.INCOME);
        when(categories.findByIdAndUserIdAndDeletedFalse(4L, 7L)).thenReturn(Optional.of(category));
        var mismatch = new ExtractedTransaction(TransactionType.EXPENSE, null, null, null, "c", null, null, "Despesa");
        assertThatThrownBy(() -> service.validate(7L, catalog, mismatch)).isInstanceOf(AiException.class)
                .extracting("code").isEqualTo("INVALID_AI_RESPONSE");
    }
}
