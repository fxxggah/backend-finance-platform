package com.finance.ai.audio;

import com.finance.ai.exception.AiException;
import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AudioValidatorTest {
    static byte[] wav(int seconds) throws Exception {
        try (var output = new ByteArrayOutputStream(); var stream = new AudioInputStream(
                new ByteArrayInputStream(new byte[seconds * 32000]),
                new AudioFormat(16000, 16, 1, true, false), seconds * 16000L)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        }
    }

    @Test void acceptsExactThirtySecondsWavAndPreservesBytesWithoutNativeTool() throws Exception {
        var inspector = mock(FfprobeAudioInspector.class);
        var validator = new AudioValidator(inspector);
        byte[] bytes = wav(30);
        var audio = validator.validate(bytes);
        assertThat(audio.duration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(audio.mimeType()).isEqualTo("audio/wav");
        assertThat(audio.bytes()).isSameAs(bytes);
        verifyNoInteractions(inspector);
    }

    @Test void rejectsOversizedLongUnknownAndTruncatedBeforeProvider() throws Exception {
        var validator = new AudioValidator(mock(FfprobeAudioInspector.class));
        assertThatThrownBy(() -> validator.validate(new byte[AudioValidator.MAX_BYTES + 1]))
                .isInstanceOf(AiException.class).extracting("code").isEqualTo("AUDIO_TOO_LARGE");
        byte[] valid = wav(1);
        for (byte[] bytes : new byte[][]{new byte[0], new byte[100], wav(31), Arrays.copyOf(valid, valid.length - 10)}) {
            assertThatThrownBy(() -> validator.validate(bytes)).isInstanceOf(AiException.class)
                    .extracting("code").isEqualTo("INVALID_AUDIO");
        }
    }

    @Test void compressedAudioRequiresMeasuredPositiveDuration() throws Exception {
        var inspector = mock(FfprobeAudioInspector.class);
        var validator = new AudioValidator(inspector);
        byte[] mp4 = "\0\0\0\u0018ftypM4A moovmp4amdat".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        when(inspector.inspect(mp4, "audio/mp4")).thenReturn(Duration.ofSeconds(30));
        assertThat(validator.validate(mp4).duration()).isEqualTo(Duration.ofSeconds(30));
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(31)}) {
            when(inspector.inspect(mp4, "audio/mp4")).thenReturn(duration);
            assertThatThrownBy(() -> validator.validate(mp4)).isInstanceOf(AiException.class);
        }
        when(inspector.inspect(mp4, "audio/mp4")).thenThrow(new IllegalStateException("fixture-private-path"));
        assertThatThrownBy(() -> validator.validate(mp4)).isInstanceOf(AiException.class)
                .hasMessageNotContaining("fixture-private-path").extracting("code").isEqualTo("AUDIO_VALIDATION_UNAVAILABLE");
    }
}
