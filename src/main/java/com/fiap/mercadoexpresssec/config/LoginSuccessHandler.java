package com.fiap.mercadoexpresssec.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Apos o login, redireciona cada tipo de usuario para a sua tela restrita:
 * ADMIN   -> /admin   (painel administrativo)
 * CLIENTE -> /mercado (catalogo de produtos)
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        String destino = admin ? "/admin" : "/mercado";
        response.sendRedirect(request.getContextPath() + destino);
    }

}
