package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.config.LoginFailureHandler;
import com.fiap.mercadoexpresssec.dto.CadastroForm;
import com.fiap.mercadoexpresssec.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final UsuarioService usuarioService;

    public HomeController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Landing page (index.html) - publica
    @GetMapping({"/", "/index"})
    public String index() {
        return "index";
    }

    // Tela de login personalizada - publica
    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (estaLogado(authentication)) {
            return "redirect:" + areaDoUsuario(authentication);
        }
        return "login";
    }

    // Tela de Sign Up - publica
    @GetMapping("/cadastro")
    public String cadastroForm(Authentication authentication, HttpSession session, Model model) {
        if (estaLogado(authentication)) {
            return "redirect:" + areaDoUsuario(authentication);
        }
        if (!model.containsAttribute("cadastroForm")) {
            CadastroForm form = new CadastroForm();
            Object email = session.getAttribute(LoginFailureHandler.SESSAO_EMAIL_NAO_ENCONTRADO);
            if (email != null) {
                form.setEmail(email.toString());
                session.removeAttribute(LoginFailureHandler.SESSAO_EMAIL_NAO_ENCONTRADO);
            }
            model.addAttribute("cadastroForm", form);
        }
        return "cadastro";
    }

    @PostMapping("/cadastro")
    public String cadastrar(@Valid @ModelAttribute("cadastroForm") CadastroForm form, BindingResult result,
                            RedirectAttributes redirectAttributes) {
        if (form.getSenha() != null && !form.getSenha().equals(form.getConfirmarSenha())) {
            result.rejectValue("confirmarSenha", "senha.diferente", "As senhas não conferem");
        }
        if (!result.hasFieldErrors("email") && usuarioService.emailCadastrado(form.getEmail())) {
            result.rejectValue("email", "email.duplicado", "Este e-mail já está cadastrado. Faça login.");
        }
        if (result.hasErrors()) {
            return "cadastro";
        }

        usuarioService.cadastrarCliente(form);
        redirectAttributes.addFlashAttribute("emailCadastrado", UsuarioService.normalizarEmail(form.getEmail()));
        return "redirect:/login?cadastrado";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "acesso-negado";
    }

    private boolean estaLogado(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String areaDoUsuario(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? "/admin" : "/mercado";
    }

}
