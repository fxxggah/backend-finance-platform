package com.finance.ai.service;

import com.finance.ai.audio.AudioValidator;
import com.finance.ai.exception.AiException;
import com.finance.ai.extractor.ExtractionParser;
import com.finance.ai.extractor.TransactionExtractor;
import com.finance.ai.model.AudioInput;
import com.finance.ai.model.ExtractionCatalog;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TransactionDraftServiceTest {
    @Test void usesInjectedClockInSaoPauloAndNormalizesOnlyAfterResourceValidation() {
        var audio = mock(AudioValidator.class);
        var catalogs = mock(AiCatalogService.class);
        var extractor = mock(TransactionExtractor.class);
        var catalog = new ExtractionCatalog(Map.of(), Map.of(), Map.of());
        byte[] bytes = new byte[1];
        var input = new AudioInput(bytes, "audio/wav", Duration.ofSeconds(1));
        when(audio.validate(bytes)).thenReturn(input);
        when(catalogs.load(7L)).thenReturn(catalog);
        when(catalogs.validate(eq(7L), eq(catalog), any())).thenReturn(new AiCatalogService.Resolved(3L, 5L, null));
        when(extractor.extract(eq(input), eq(catalog), eq(LocalDate.of(2026, 11, 13)))).thenReturn("""
                {"events":[{"type":"EXPENSE","amount":"10.00","date":"2026-11-13","installments":null,
                "categoryRef":"c","accountRef":"a","creditCardRef":null}]}
                """);
        var service = new TransactionDraftService(audio, catalogs, extractor, new ExtractionParser(),
                Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneOffset.UTC));
        var result = service.create(7L, bytes);
        assertThat(result.installments()).isEqualTo(1);
        assertThat(result.missingFields()).isEmpty();
        assertThat(result.requiresConfirmation()).isTrue();
        var order = inOrder(audio, catalogs, extractor);
        order.verify(audio).validate(bytes); order.verify(catalogs).load(7L);
        order.verify(extractor).extract(input, catalog, LocalDate.of(2026, 11, 13));
        order.verify(catalogs).validate(eq(7L), eq(catalog), any());
        assertThat(result.toString()).doesNotContain("10.00", "2026-11-13");
    }

    @Test void refusesFutureDateBeyondOneYearBeforeResolvingResources() {
        var audio = mock(AudioValidator.class); var catalogs = mock(AiCatalogService.class);
        var extractor = mock(TransactionExtractor.class);
        var catalog = new ExtractionCatalog(Map.of(), Map.of(), Map.of());
        when(audio.validate(any())).thenReturn(new AudioInput(new byte[1], "audio/wav", Duration.ofSeconds(1)));
        when(catalogs.load(7L)).thenReturn(catalog);
        when(extractor.extract(any(), any(), any())).thenReturn("""
                {"events":[{"type":null,"amount":null,"date":"2027-11-14","installments":null,
                "categoryRef":null,"accountRef":null,"creditCardRef":null}]}
                """);
        var service = new TransactionDraftService(audio, catalogs, extractor, new ExtractionParser(),
                Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> service.create(7L, new byte[1])).isInstanceOf(AiException.class);
        verify(catalogs, never()).validate(any(), any(), any());
    }
}
