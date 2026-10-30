package com.finance.creditcard.controller;

import com.finance.creditcard.dto.request.CreditCardRequest;
import com.finance.creditcard.dto.request.InvoicePaymentRequest;
import com.finance.creditcard.dto.response.CreditCardResponse;
import com.finance.creditcard.dto.response.InvoiceResponse;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.service.CreditCardService;
import com.finance.creditcard.service.InvoiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/credit-cards")
@RequiredArgsConstructor
@Tag(name = "Cartões de Crédito", description = "Gestão de cartões, faturas e pagamentos")
public class CreditCardController {

    private final CreditCardService creditCardService;
    private final InvoiceService invoiceService;

    @GetMapping
    public List<CreditCardResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return creditCardService.list(userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditCardResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreditCardRequest request) {
        return creditCardService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    public CreditCardResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CreditCardRequest request) {
        return creditCardService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        creditCardService.delete(userId(jwt), id);
    }

    @GetMapping("/{id}/invoices")
    public List<InvoiceResponse> listInvoices(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        CreditCard card = creditCardService.getCardOrThrow(id, userId(jwt));
        return invoiceService.listInvoices(card);
    }

    @PostMapping("/{id}/invoices/{reference}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public void payInvoice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable String reference, // ex: "2026-10"
            @Valid @RequestBody InvoicePaymentRequest request) {

        CreditCard card = creditCardService.getCardOrThrow(id, userId(jwt));
        invoiceService.payInvoice(userId(jwt), card, reference, request);
    }

    @DeleteMapping("/{id}/invoices/{reference}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable String reference,
            @PathVariable Long paymentId) {

        creditCardService.getCardOrThrow(id, userId(jwt));
        invoiceService.deletePayment(userId(jwt), paymentId);
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}