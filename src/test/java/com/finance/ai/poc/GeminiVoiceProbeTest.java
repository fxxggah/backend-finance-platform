package com.finance.ai.poc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiVoiceProbeTest {
    private static final String VALID = """
            {"status":"SINGLE","type":"EXPENSE","amount":"45.00","date":"2026-11-13",
             "installments":1,"categoryRef":"cat_food","accountRef":"acc_wallet","creditCardRef":null}
            """;

    @TempDir
    Path temporary;

    @Test
    void parsesExactMoneyAndExpectedSample() throws Exception {
        var draft = GeminiVoiceProbe.parse(VALID);
        assertThat(draft.amount()).isEqualByComparingTo("45.00");
        assertThat(draft.amount().scale()).isEqualTo(2);
        assertThat(draft.missingFields()).isEmpty();
        assertThat(GeminiVoiceProbe.matchesSample(draft, LocalDate.of(2026, 11, 13))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00", "1.001", "1e3", "NaN", "1000000000.00", "1,50"})
    void rejectsInvalidMoneyWithoutRounding(String amount) {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("45.00", amount)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "25", "1.5", "\"3\"", "2147483648"})
    void rejectsInvalidInstallments(String value) {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("\"installments\":1", "\"installments\":" + value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNumericMoneyRatherThanCoercingIt() {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("\"45.00\"", "45.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownReferences() {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("acc_wallet", "acc_other_user")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBothAccountAndCard() {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("\"creditCardRef\":null", "\"creditCardRef\":\"card_nubank\"")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCardIncome() {
        String cardIncome = VALID.replace("EXPENSE", "INCOME").replace("cat_food", "cat_salary")
                .replace("\"accountRef\":\"acc_wallet\"", "\"accountRef\":null")
                .replace("\"creditCardRef\":null", "\"creditCardRef\":\"card_nubank\"");
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(cardIncome)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInstallmentsOnAccountAndWrongCategoryType() {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("\"installments\":1", "\"installments\":3")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("cat_food", "cat_salary")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsIncompleteDraftWithoutInventingFields() throws Exception {
        var draft = GeminiVoiceProbe.parse(VALID.replace("\"45.00\"", "null")
                .replace("\"acc_wallet\"", "null"));
        assertThat(draft.missingFields()).containsExactly("amount", "paymentSource");
    }

    @Test
    void requiresEmptyFieldsForMultipleTransactions() throws Exception {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("SINGLE", "MULTIPLE")))
                .isInstanceOf(IllegalArgumentException.class);
        var multiple = GeminiVoiceProbe.parse("""
                {"status":"MULTIPLE","type":null,"amount":null,"date":null,"installments":null,
                "categoryRef":null,"accountRef":null,"creditCardRef":null}
                """);
        assertThat(multiple.status()).isEqualTo("MULTIPLE");
        assertThat(multiple.amount()).isNull();
    }

    @Test
    void rejectsExtraMissingDuplicateOrTrailingFields() {
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("{", "{\"userId\":9,"))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("\"installments\":1,", ""))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID.replace("{", "{\"status\":\"SINGLE\","))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> GeminiVoiceProbe.parse(VALID + "{}")).isInstanceOf(Exception.class);
    }

    @Test
    void usesClockAndDisablesToolsRetriesAndContinuation() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        var config = GeminiVoiceProbe.config(clock);
        assertThat(config.systemInstruction().orElseThrow().parts().orElseThrow().getFirst().text().orElseThrow())
                .contains("Hoje é 2026-11-13");
        assertThat(config.tools()).isEmpty();
        assertThat(config.automaticFunctionCalling().orElseThrow().disable()).contains(true);
        assertThat(config.automaticContinuation()).contains(false);
        assertThat(config.maxOutputTokens()).contains(512);
        assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().toString()).isEqualTo("MINIMAL");
        assertThat(GeminiVoiceProbe.httpOptions().timeout()).contains(30_000);
        assertThat(GeminiVoiceProbe.httpOptions().retryOptions().orElseThrow().attempts()).contains(1);
    }

    @Test
    void acceptsRealPcmWav() throws Exception {
        Path path = wav(1);
        assertThat(GeminiVoiceProbe.readWav(path)).isEqualTo(Files.readAllBytes(path));
    }

    @Test
    void rejectsAudioBeyondThirtySeconds() throws Exception {
        Path path = wav(31);
        assertThatThrownBy(() -> GeminiVoiceProbe.readWav(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedAndFakeWav() throws Exception {
        Path path = wav(1);
        byte[] bytes = Files.readAllBytes(path);
        Files.write(path, java.util.Arrays.copyOf(bytes, bytes.length - 10));
        assertThatThrownBy(() -> GeminiVoiceProbe.readWav(path)).isInstanceOf(Exception.class);
        Files.write(path, new byte[100]);
        assertThatThrownBy(() -> GeminiVoiceProbe.readWav(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyAndOversizedFilesBeforeSending() throws Exception {
        Path path = temporary.resolve("input.wav");
        Files.write(path, new byte[0]);
        assertThatThrownBy(() -> GeminiVoiceProbe.readWav(path)).isInstanceOf(IllegalArgumentException.class);
        Files.write(path, new byte[GeminiVoiceProbe.MAX_BYTES + 1]);
        assertThatThrownBy(() -> GeminiVoiceProbe.readWav(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsWavByContentDespiteExtension() throws Exception {
        Path original = wav(1);
        Path renamed = temporary.resolve("renamed.webm");
        Files.copy(original, renamed);
        assertThat(GeminiVoiceProbe.readAudio(renamed).mimeType()).isEqualTo("audio/wav");
    }

    @Test
    void recognizesWebmOpusMarkersAndPreservesBytes() throws Exception {
        // Fixture somente de reconhecimento; não simula um arquivo decodificável.
        byte[] markers = "\u001a\u0045\u00df\u00a3webmA_OPUS\u001f\u0043\u00b6\u0075sample"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        Path path = temporary.resolve("audio.bin");
        Files.write(path, markers);
        var audio = GeminiVoiceProbe.readAudio(path);
        assertThat(audio.mimeType()).isEqualTo("audio/webm");
        assertThat(audio.bytes()).isEqualTo(markers);
        Files.writeString(path, "fakewebmA_OPUSsample");
        assertThatThrownBy(() -> GeminiVoiceProbe.readAudio(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recognizesMp4AudioMarkersAndRejectsVideo() throws Exception {
        // O reconhecimento não valida duração, codec completo ou estrutura dos boxes.
        byte[] markers = "\u0000\u0000\u0000\u0018ftypM4A moovmp4amdatdata"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        Path path = temporary.resolve("audio.m4a");
        Files.write(path, markers);
        assertThat(GeminiVoiceProbe.readAudio(path).mimeType()).isEqualTo("audio/mp4");
        Files.write(path, "\u0000\u0000\u0000\u0018ftypM4A moovmp4avide mdat".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        assertThatThrownBy(() -> GeminiVoiceProbe.readAudio(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownTruncatedAndOversizedCompressedAudio() throws Exception {
        Path path = temporary.resolve("input.webm");
        Files.writeString(path, "not audio at all, despite file extension");
        assertThatThrownBy(() -> GeminiVoiceProbe.readAudio(path)).isInstanceOf(IllegalArgumentException.class);
        Files.write(path, new byte[4]);
        assertThatThrownBy(() -> GeminiVoiceProbe.readAudio(path)).isInstanceOf(IllegalArgumentException.class);
        Files.write(path, new byte[GeminiVoiceProbe.MAX_BYTES + 1]);
        assertThatThrownBy(() -> GeminiVoiceProbe.readAudio(path)).isInstanceOf(IllegalArgumentException.class);
    }

    private Path wav(int seconds) throws Exception {
        Path path = temporary.resolve("input.wav");
        AudioFormat format = new AudioFormat(16_000, 16, 1, true, false);
        try (var stream = new AudioInputStream(new ByteArrayInputStream(new byte[seconds * 32_000]),
                format, seconds * 16_000L)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
        return path;
    }
}
