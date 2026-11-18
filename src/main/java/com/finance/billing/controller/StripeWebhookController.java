package com.finance.billing.controller;

import com.finance.billing.service.BillingService;
import com.finance.billing.exception.BillingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController @RequestMapping("/api/v1/billing/webhooks/stripe") @Profile("dev & !prod")
@ConditionalOnProperty(prefix="app.billing", name="enabled", havingValue="true") @Tag(name="Assinatura PRO")
public class StripeWebhookController {
    private final BillingService billing;
    public StripeWebhookController(BillingService billing) { this.billing = billing; }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Receber evento Stripe assinado; nunca concede PRO pelo retorno do navegador")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Evento persistido/duplicado/ignorado", content=@io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="Assinatura, corpo, modo ou versão inválidos", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413", description="Webhook acima de 256 KiB", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class)))
    })
    public ResponseEntity<Void> receive(HttpServletRequest request, @RequestHeader(value="Stripe-Signature", required=false) String signature) {
        byte[] payload;
        try { payload = request.getInputStream().readNBytes(256*1024+1); }
        catch (java.io.IOException ex) { throw new BillingException(HttpStatus.BAD_REQUEST, "INVALID_STRIPE_WEBHOOK", "Não foi possível ler a notificação."); }
        if (payload.length > 256*1024) throw new BillingException(HttpStatus.PAYLOAD_TOO_LARGE, "WEBHOOK_TOO_LARGE", "Notificação acima do limite permitido.");
        billing.webhook(payload, signature); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }
}
