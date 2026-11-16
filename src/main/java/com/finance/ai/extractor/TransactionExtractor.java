package com.finance.ai.extractor;

import com.finance.ai.model.AudioInput;
import com.finance.ai.model.ExtractionCatalog;
import java.time.LocalDate;

public interface TransactionExtractor {
    String extract(AudioInput audio, ExtractionCatalog catalog, LocalDate today);
}
