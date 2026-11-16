package com.finance.ai.poc;

import com.finance.ai.audio.FfprobeAudioInspector;
import java.time.Duration;

/** Adaptador da prova manual para o mesmo decoder usado na feature. */
final class MediaDurationProbe {
    static final int MAX_REPORT_BYTES = FfprobeAudioInspector.MAX_REPORT_BYTES;
    static final int MAX_ERROR_BYTES = FfprobeAudioInspector.MAX_ERROR_BYTES;
    private MediaDurationProbe() { }

    static Duration validate(byte[] audio, String mime) throws Exception {
        String configured = System.getProperty("fin.poc.ffprobe-path", System.getenv("FIN_FFPROBE_PATH"));
        try { return new FfprobeAudioInspector(configured).inspect(audio, mime); }
        catch (IllegalArgumentException ex) { throw new AudioValidationException("Não foi possível comprovar duração de até 30 segundos."); }
    }

    static Duration parseReport(byte[] report, String mime) throws Exception {
        try { return FfprobeAudioInspector.parseReport(report, mime); }
        catch (IllegalArgumentException ex) { throw new AudioValidationException(ex.getMessage()); }
    }

    static final class AudioValidationException extends IllegalArgumentException {
        private AudioValidationException(String message) { super(message); }
    }
}
