package com.finance.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Componente auxiliar que extrai o id interno do usuário autenticado
 * a partir do SecurityContext.
 * <p>
 * Uso no controller:
 * 
 * <pre>
 * public SomeResponse get(@AuthenticationPrincipal Jwt jwt) {
 *     return service.get(currentUser.id(jwt));
 * }
 * </pre>
 * 
 * O controller injeta o {@link Jwt} diretamente via
 * {@link AuthenticationPrincipal}
 * e passa para o método {@link #id(Jwt)}.
 */
@Component
public class CurrentUser {

    /**
     * Extrai o userId do claim {@code sub} do JWT.
     */
    public Long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
