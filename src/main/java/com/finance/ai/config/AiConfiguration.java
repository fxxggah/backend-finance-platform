package com.finance.ai.config;

import com.finance.account.repository.AccountRepository;
import com.finance.category.repository.CategoryRepository;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.ai.audio.AudioValidator;
import com.finance.ai.audio.FfprobeAudioInspector;
import com.finance.ai.extractor.ExtractionParser;
import com.finance.ai.extractor.GeminiTransactionExtractor;
import com.finance.ai.extractor.TransactionExtractor;
import com.finance.ai.service.AiCatalogService;
import com.finance.ai.service.TransactionDraftService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiConfiguration {
    @Bean public AudioValidator audioValidator(@Value("${app.ai.ffprobe-path:}") String path) {
        return new AudioValidator(new FfprobeAudioInspector(path));
    }
    @Bean public AiCatalogService aiCatalogService(AccountRepository accounts, CategoryRepository categories,
            CreditCardRepository cards) { return new AiCatalogService(accounts, categories, cards); }
    @Bean public TransactionExtractor transactionExtractor(@Value("${GEMINI_API_KEY:}") String key) {
        return new GeminiTransactionExtractor(key);
    }
    @Bean public ExtractionParser extractionParser() { return new ExtractionParser(); }
    @Bean public TransactionDraftService transactionDraftService(AudioValidator audio, AiCatalogService catalogs,
            TransactionExtractor extractor, ExtractionParser parser, Clock clock) {
        return new TransactionDraftService(audio, catalogs, extractor, parser, clock);
    }
}
