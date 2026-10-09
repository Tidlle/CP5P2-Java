package com.fiap.mercadoexpresssec.config;

import com.fiap.mercadoexpresssec.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Trata falhas de login:
 * - e-mail nao cadastrado -> redireciona para a tela de Sign Up (/cadastro), ja com o e-mail preenchido;
 * - e-mail cadastrado, senha errada -> volta para /login?error.
 *
 * O Spring Security esconde o UsernameNotFoundException (vira BadCredentialsException),
 * por isso a existencia do e-mail e verificada aqui, direto no banco.
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String SESSAO_EMAIL_NAO_ENCONTRADO = "EMAIL_NAO_ENCONTRADO";

    private final UsuarioService usuarioService;

    public LoginFailureHandler(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String email = request.getParameter(SecurityConfig.PARAM_EMAIL);

        if (email != null && !email.isBlank() && !usuarioService.emailCadastrado(email)) {
            // Guarda o e-mail na sessao (e nao na URL) para pre-preencher o cadastro
            request.getSession().setAttribute(SESSAO_EMAIL_NAO_ENCONTRADO, UsuarioService.normalizarEmail(email));
            response.sendRedirect(request.getContextPath() + "/cadastro?naoEncontrado");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/login?error");
    }

}
