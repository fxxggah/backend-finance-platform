package com.finance.ai.model;

import com.finance.common.enums.TransactionType;
import java.util.Map;

public record ExtractionCatalog(Map<String, Entry> categories, Map<String, Entry> accounts,
                                Map<String, Entry> creditCards) {
    public ExtractionCatalog {
        categories = Map.copyOf(categories);
        accounts = Map.copyOf(accounts);
        creditCards = Map.copyOf(creditCards);
    }
    public record Entry(Long id, String name, TransactionType type) { }
    @Override public String toString() { return "ExtractionCatalog[conteúdo omitido]"; }
}
