package com.finance.ai.controller;

import com.finance.ai.audio.AudioValidator;
import com.finance.ai.dto.TransactionDraftResponse;
import com.finance.ai.exception.AiException;
import com.finance.ai.service.TransactionDraftService;
import com.finance.security.CurrentUser;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/transaction-drafts")
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
@Tag(name = "IA", description = "Rascunho de uma transação por áudio, somente em desenvolvimento")
@SecurityRequirement(name = "bearer-token")
public class TransactionDraftController {
    private final TransactionDraftService drafts;
    private final CurrentUser currentUser;
    public TransactionDraftController(TransactionDraftService drafts, CurrentUser currentUser) {
        this.drafts = drafts; this.currentUser = currentUser;
    }

    @PostMapping(consumes = {"audio/webm", "audio/mp4", "audio/wav", "application/octet-stream"},
            produces = "application/json")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rascunho para revisão obrigatória"),
            @ApiResponse(responseCode = "400", description = "Falha ao ler áudio", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "JWT obrigatório", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso selecionado indisponível", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "Áudio acima de 2 MiB", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type não aceito", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Áudio inválido, múltiplo, sem transação ou catálogo excessivo", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Resposta inválida da IA", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "IA ou validador indisponível/limite temporário", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @Operation(summary = "Preparar rascunho por áudio",
            description = "Corpo binário, até 2 MiB/30 s; não cria transação. Revisar e confirmar pelo POST /transactions.",
            requestBody = @RequestBody(required = true,
                    content = {
                            @Content(mediaType = "audio/webm", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "audio/mp4", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "audio/wav", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))
                    }))
    public TransactionDraftResponse create(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        if (request.getContentLengthLong() > AudioValidator.MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try { bytes = request.getInputStream().readNBytes(AudioValidator.MAX_BYTES + 1); }
        catch (java.io.IOException ex) {
            throw new AiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO_REQUEST", "Não foi possível ler o áudio enviado.");
        }
        if (bytes.length > AudioValidator.MAX_BYTES) throw tooLarge();
        return drafts.create(currentUser.id(jwt), bytes);
    }

    private AiException tooLarge() {
        return new AiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE", "O áudio deve ter no máximo 2 MiB.");
    }
}
