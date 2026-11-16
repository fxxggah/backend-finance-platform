package com.finance.ai.audio;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Decodificação local compartilhada pela feature e PoC; sem disco ou envio a terceiros. */
public class FfprobeAudioInspector {
    public static final int MAX_REPORT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_ERROR_BYTES = 16 * 1024;
    private static final BigDecimal MAX_SECONDS = new BigDecimal("30");
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final String configured;
    public FfprobeAudioInspector(String configured) { this.configured = configured; }

    public Duration inspect(byte[] audio, String mime) throws Exception {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Configure FIN_FFPROBE_PATH para validar áudio comprimido.");
        }
        Path executable = Path.of(configured);
        if (!executable.isAbsolute() || !Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IllegalStateException("Executável ffprobe inválido.");
        }
        return parseReport(execute(executable, audio), mime);
    }

    static List<String> command(Path executable) {
        return List.of(executable.toString(), "-v", "error", "-max_alloc", "16777216",
                "-threads", "1", "-protocol_whitelist", "pipe", "-format_whitelist", "mov,matroska,webm",
                "-codec_whitelist", "aac,opus", "-show_frames",
                "-show_entries", "frame=stream_index,pts,nb_samples:frame_side_data=:stream=index,codec_type,codec_name,sample_rate,time_base:stream_tags=:stream_disposition=:format=format_name,duration:format_tags=",
                "-of", "json", "-i", "pipe:0");
    }

    private static byte[] execute(Path executable, byte[] audio) throws Exception {
        if (audio.length == 0 || audio.length > AudioValidator.MAX_BYTES) {
            throw new IllegalArgumentException("Tamanho do áudio inválido.");
        }
        ProcessBuilder builder = new ProcessBuilder(command(executable));
        builder.environment().clear();
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
        Process process = builder.start();
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var output = workers.submit(() -> readLimited(process.getInputStream(), MAX_REPORT_BYTES, process));
            var errors = workers.submit(() -> readLimited(process.getErrorStream(), MAX_ERROR_BYTES, process));
            var input = workers.submit(() -> {
                try (var destination = process.getOutputStream()) { destination.write(audio); }
                return true;
            });
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IllegalArgumentException("Tempo limite ao validar áudio.");
            }
            byte[] report = output.get(1, TimeUnit.SECONDS);
            byte[] stderr = errors.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || stderr.length != 0) {
                throw new IllegalArgumentException("Não foi possível decodificar o áudio com segurança.");
            }
            input.get(1, TimeUnit.SECONDS);
            return report;
        } finally {
            process.destroyForcibly();
            workers.shutdownNow();
            closeQuietly(process.getOutputStream());
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
        }
    }

    private static void closeQuietly(java.io.Closeable stream) {
        try { stream.close(); } catch (java.io.IOException ignored) { /* Não ocultar a causa original. */ }
    }

    private static byte[] readLimited(InputStream stream, int limit, Process process) throws Exception {
        try (stream) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) {
                process.destroyForcibly();
                throw new IllegalArgumentException("Análise do áudio excedeu o limite de saída.");
            }
            return bytes;
        }
    }

    public static Duration parseReport(byte[] report, String mime) throws Exception {
        if (report.length == 0 || report.length > MAX_REPORT_BYTES) throw invalid();
        JsonNode root = JSON.readTree(report);
        if (root == null || !root.isObject()) throw invalid();
        JsonNode streams = root.path("streams");
        JsonNode frames = root.path("frames");
        if (!streams.isArray() || streams.size() != 1 || !frames.isArray() || frames.isEmpty()
                || frames.size() > 50_000) throw invalid();
        JsonNode stream = streams.get(0);
        String codec = stream.path("codec_name").asText();
        String format = root.path("format").path("format_name").asText();
        boolean webm = "audio/webm".equals(mime) && "opus".equals(codec)
                && List.of(format.split(",")).contains("matroska");
        boolean mp4 = "audio/mp4".equals(mime) && "aac".equals(codec)
                && List.of(format.split(",")).contains("mov");
        if (!"audio".equals(stream.path("codec_type").asText()) || (!webm && !mp4)) throw invalid();
        int index = positiveOrZeroInt(stream.path("index"));
        String rateText = stream.path("sample_rate").asText();
        if (!rateText.matches("[0-9]{1,6}")) throw invalid();
        int rate = Integer.parseInt(rateText);
        if (rate < 8_000 || rate > 192_000) throw invalid();
        String timeBase = stream.path("time_base").asText();
        if (!timeBase.matches("[1-9][0-9]{0,8}/[1-9][0-9]{0,8}")) throw invalid();
        String[] ratio = timeBase.split("/");
        BigDecimal numerator = new BigDecimal(ratio[0]);
        BigDecimal denominator = new BigDecimal(ratio[1]);
        long samples = 0;
        Long previous = null;
        Long firstPts = null;
        BigDecimal lastEndUnits = BigDecimal.ZERO;
        BigDecimal rateDecimal = BigDecimal.valueOf(rate);
        BigDecimal durationDenominator = denominator.multiply(rateDecimal);
        for (JsonNode frame : frames) {
            if (positiveOrZeroInt(frame.path("stream_index")) != index
                    || !frame.path("pts").isIntegralNumber() || !frame.path("pts").canConvertToLong()) throw invalid();
            int count = positiveOrZeroInt(frame.path("nb_samples"));
            if (count == 0 || count > 8192) throw invalid();
            samples = Math.addExact(samples, count);
            if (samples > rate * 30L) throw tooLong();
            long pts = frame.path("pts").longValue();
            if (previous != null && pts < previous) throw invalid();
            previous = pts;
            if (firstPts == null) firstPts = pts;
            BigDecimal endUnits = BigDecimal.valueOf(pts).subtract(BigDecimal.valueOf(firstPts))
                    .multiply(numerator).multiply(rateDecimal).add(BigDecimal.valueOf(count).multiply(denominator));
            lastEndUnits = lastEndUnits.max(endUnits);
        }
        // Comparar razões inteiras antes de arredondar: exatamente 30 s precisa ser aceito.
        BigDecimal durationUnits = BigDecimal.valueOf(samples).multiply(denominator).max(lastEndUnits);
        JsonNode declared = root.path("format").path("duration");
        if (!declared.isMissingNode() && !declared.isNull() && !"N/A".equals(declared.asText())) {
            if (!declared.asText().matches("[0-9]{1,6}(\\.[0-9]{1,9})?")) throw invalid();
            durationUnits = durationUnits.max(new BigDecimal(declared.asText()).multiply(durationDenominator));
        }
        if (durationUnits.signum() <= 0) throw invalid();
        if (durationUnits.compareTo(MAX_SECONDS.multiply(durationDenominator)) > 0) throw tooLong();
        return Duration.ofNanos(durationUnits.movePointRight(9)
                .divide(durationDenominator, 0, RoundingMode.UP).longValueExact());
    }

    private static int positiveOrZeroInt(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) throw invalid();
        return value.intValue();
    }

    private static AudioValidationException invalid() {
        return new AudioValidationException("Não foi possível comprovar formato e duração do áudio.");
    }

    private static AudioValidationException tooLong() {
        return new AudioValidationException("O áudio deve ter até 30 segundos.");
    }

    static final class AudioValidationException extends IllegalArgumentException {
        private AudioValidationException(String message) { super(message); }
    }
}
