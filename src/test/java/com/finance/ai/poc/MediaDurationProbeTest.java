package com.finance.ai.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MediaDurationProbeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ObjectNode report(boolean mp4, int frames) {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("streams").addObject().put("index", 0).put("codec_type", "audio")
                .put("codec_name", mp4 ? "aac" : "opus").put("sample_rate", "48000")
                .put("time_base", "1/48000");
        root.putObject("format").put("format_name", mp4 ? "mov,mp4,m4a,3gp,3g2,mj2" : "matroska,webm");
        var output = root.putArray("frames");
        for (int i = 0; i < frames; i++) {
            output.addObject().put("stream_index", 0).put("pts", i * 4800L).put("nb_samples", 4800);
        }
        return root;
    }

    private Duration parse(ObjectNode root, boolean mp4) throws Exception {
        return MediaDurationProbe.parseReport(JSON.writeValueAsBytes(root), mp4 ? "audio/mp4" : "audio/webm");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsExactlyThirtySecondsAndMissingDeclaredDuration(boolean mp4) throws Exception {
        assertEquals(Duration.ofSeconds(30), parse(report(mp4, 300), mp4));
        assertEquals(Duration.ofMillis(29900), parse(report(mp4, 299), mp4));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsOverThirtyEvenWhenHeaderUnderstatesDuration(boolean mp4) {
        var root = report(mp4, 301);
        ((ObjectNode) root.get("format")).put("duration", "0.513333");
        var error = assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(root, mp4));
        assertEquals("O áudio deve ter até 30 segundos.", error.getMessage());
    }

    @Test
    void rejectsOneSampleOverLimit() {
        var root = report(false, 300);
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.get("frames")).addObject()
                .put("stream_index", 0).put("pts", 1440000).put("nb_samples", 1);
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(root, false));
    }

    @Test
    void acceptsExactLimitWithFractionalTimestampsWithoutRoundingDrift() throws Exception {
        var root = report(true, 0);
        ((ObjectNode) root.get("streams").get(0)).put("sample_rate", "44100").put("time_base", "1/44100");
        var frames = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("frames");
        int total = 44100 * 30;
        for (int offset = 0; offset < total; offset += 1024) {
            frames.addObject().put("stream_index", 0).put("pts", offset - 2112)
                    .put("nb_samples", Math.min(1024, total - offset));
        }
        assertEquals(Duration.ofSeconds(30), parse(root, true));
    }

    @Test
    void timestampGapAndDeclaredDurationAlsoCount() {
        var gap = report(false, 2);
        ((ObjectNode) gap.get("frames").get(1)).put("pts", 1440000);
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(gap, false));
        var declared = report(true, 1);
        ((ObjectNode) declared.get("format")).put("duration", "30.000000001");
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(declared, true));
    }

    @Test
    void rejectsMissingFramesAndInvalidFrameData() {
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(report(false, 0), false));
        for (String field : new String[]{"pts", "nb_samples", "stream_index"}) {
            var root = report(false, 1);
            ((ObjectNode) root.get("frames").get(0)).remove(field);
            assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(root, false), field);
        }
        var backwards = report(false, 2);
        ((ObjectNode) backwards.get("frames").get(1)).put("pts", -1);
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(backwards, false));
        var zero = report(false, 1);
        ((ObjectNode) zero.get("frames").get(0)).put("nb_samples", 0);
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(zero, false));
    }

    @Test
    void rejectsWrongCodecExtraStreamsAndUnknownTimeBase() {
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(report(true, 1), false));
        var extra = report(false, 1);
        ((com.fasterxml.jackson.databind.node.ArrayNode) extra.get("streams")).addObject().put("codec_type", "video");
        assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(extra, false));
        for (String field : new String[]{"sample_rate", "time_base"}) {
            var root = report(false, 1);
            ((ObjectNode) root.get("streams").get(0)).put(field, "N/A");
            assertThrows(MediaDurationProbe.AudioValidationException.class, () -> parse(root, false));
        }
    }

    @Test
    void rejectsAmbiguousOrExcessiveJson() {
        for (String json : new String[]{"{} {}", "{\"frames\":[],\"frames\":[]}", "null", "[]"}) {
            assertThrows(Exception.class, () -> MediaDurationProbe.parseReport(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "audio/webm"));
        }
        assertThrows(MediaDurationProbe.AudioValidationException.class,
                () -> MediaDurationProbe.parseReport(new byte[MediaDurationProbe.MAX_REPORT_BYTES + 1], "audio/webm"));
    }
}
