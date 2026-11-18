package com.finance.billing.controller;

import com.finance.billing.dto.BillingResponses;
import com.finance.billing.service.BillingService;
import com.finance.billing.exception.BillingException;
import com.finance.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController @RequestMapping("/api/v1/billing") @Profile("dev & !prod")
@ConditionalOnProperty(prefix="app.billing", name="enabled", havingValue="true")
@Tag(name="Assinatura PRO", description="Stripe sandbox, somente desenvolvimento") @SecurityRequirement(name="bearer-token")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="Parâmetros/corpo/chave inválidos", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="JWT obrigatório", content=@io.swagger.v3.oas.annotations.media.Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="Usuário/cliente não encontrado", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409", description="Operação em andamento/assinatura existente/checkout expirado; consultar Retry-After", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="422", description="Revisão operacional necessária", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="502", description="Resposta do provedor inválida", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503", description="Cobrança indisponível", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ProblemDetail.class)))
})
public class BillingController {
    private final BillingService billing;
    private final CurrentUser currentUser;
    public BillingController(BillingService billing, CurrentUser currentUser) { this.billing = billing; this.currentUser = currentUser; }
    @GetMapping("/plan") @Operation(summary="Consultar preço mensal configurado no servidor")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Preço mensal do servidor", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=BillingResponses.Plan.class)))
    public ResponseEntity<BillingResponses.Plan> plan(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        empty(request); return response(billing.plan(currentUser.id(jwt)));
    }
    @GetMapping("/subscription") @Operation(summary="Consultar assinatura e validade efetiva do próprio usuário")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Estado e acesso do próprio usuário", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=BillingResponses.Status.class)))
    public ResponseEntity<BillingResponses.Status> status(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        empty(request); return response(billing.status(currentUser.id(jwt)));
    }
    @PostMapping("/checkout-sessions") @Operation(summary="Abrir checkout hospedado para o plano mensal fixo")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="URL do checkout hospedado", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=BillingResponses.Session.class)))
    public ResponseEntity<BillingResponses.Session> checkout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @io.swagger.v3.oas.annotations.Parameter(required=true, description="UUID novo por intenção de checkout")
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        empty(request); return response(billing.checkout(currentUser.id(jwt), key));
    }
    @PostMapping("/portal-sessions") @Operation(summary="Gerenciar a própria assinatura no portal hospedado")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="URL do portal do próprio cliente", content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json", schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=BillingResponses.Session.class)))
    public ResponseEntity<BillingResponses.Session> portal(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        empty(request); return response(billing.portal(currentUser.id(jwt)));
    }
    private static <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    private static void empty(HttpServletRequest request) {
        try {
            if (request.getQueryString() != null || request.getInputStream().read() != -1) throw new IllegalArgumentException();
        } catch (Exception ex) { throw new BillingException(HttpStatus.BAD_REQUEST, "INVALID_BILLING_REQUEST", "Esta operação não aceita parâmetros nem corpo de requisição."); }
    }
}
