package com.finance.ai.model;

import java.time.Duration;

public record AudioInput(byte[] bytes, String mimeType, Duration duration) {
    @Override public String toString() { return "AudioInput[conteúdo omitido]"; }
}
