package com.finance.ai.audio;

import com.finance.ai.exception.AiException;
import com.finance.ai.model.AudioInput;
import org.springframework.http.HttpStatus;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class AudioValidator {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private final FfprobeAudioInspector inspector;

    public AudioValidator(FfprobeAudioInspector inspector) { this.inspector = inspector; }

    public AudioInput validate(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new AiException(HttpStatus.PAYLOAD_TOO_LARGE,
                "AUDIO_TOO_LARGE", "O áudio deve ter no máximo 2 MiB.");
        if (bytes.length < 16) throw invalid();
        String markers = new String(bytes, StandardCharsets.ISO_8859_1);
        String mime;
        Duration duration;
        try {
            if (markers.startsWith("RIFF") && markers.startsWith("WAVE", 8)) {
                mime = "audio/wav";
                try (var stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                    AudioFormat format = stream.getFormat();
                    if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                            || !Float.isFinite(format.getFrameRate()) || format.getFrameRate() <= 0
                            || stream.getFrameLength() <= 0 || format.getFrameSize() <= 0) throw invalid();
                    BigDecimal nanos = BigDecimal.valueOf(stream.getFrameLength()).movePointRight(9)
                            .divide(new BigDecimal(Float.toString(format.getFrameRate())), 0, RoundingMode.UP);
                    duration = Duration.ofNanos(nanos.longValueExact());
                    if (duration.compareTo(Duration.ofSeconds(30)) > 0) throw invalid();
                    long expected = Math.multiplyExact(stream.getFrameLength(), format.getFrameSize());
                    if (expected > MAX_BYTES || stream.readNBytes(MAX_BYTES + 1).length != expected) throw invalid();
                } catch (java.io.IOException ex) { throw invalid(); }
            } else if ((bytes[0] & 255) == 0x1a && (bytes[1] & 255) == 0x45
                    && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3
                    && markers.contains("webm")) {
                mime = "audio/webm";
                duration = inspector.inspect(bytes, mime);
            } else if (markers.startsWith("ftyp", 4)) {
                mime = "audio/mp4";
                duration = inspector.inspect(bytes, mime);
            } else throw invalid();
            if (duration == null || duration.isNegative() || duration.isZero()
                    || duration.compareTo(Duration.ofSeconds(30)) > 0) throw invalid();
            return new AudioInput(bytes, mime, duration);
        } catch (AiException ex) { throw ex; }
        catch (IllegalStateException | java.io.IOException ex) {
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_VALIDATION_UNAVAILABLE",
                    "A validação de áudio está indisponível. Preencha manualmente.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_VALIDATION_UNAVAILABLE",
                    "A validação de áudio foi interrompida. Preencha manualmente.");
        } catch (Exception ex) { throw invalid(); }
    }

    private static AiException invalid() {
        return new AiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AUDIO",
                "Envie um áudio WAV PCM, WebM/Opus ou MP4/AAC válido, com duração positiva de até 30 segundos.");
    }
}
