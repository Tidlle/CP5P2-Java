package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.model.Role;
import com.fiap.mercadoexpresssec.service.MercadoService;
import com.fiap.mercadoexpresssec.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Painel administrativo - acesso restrito ao perfil ADMIN.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final MercadoService mercadoService;
    private final UsuarioService usuarioService;

    public AdminController(MercadoService mercadoService, UsuarioService usuarioService) {
        this.mercadoService = mercadoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String painel(Model model) {
        model.addAttribute("totalProdutos", mercadoService.contar());
        model.addAttribute("totalAdmins", usuarioService.contarPorRole(Role.ADMIN));
        model.addAttribute("totalClientes", usuarioService.contarPorRole(Role.CLIENTE));
        model.addAttribute("usuarios", usuarioService.listarTodos());
        return "admin/painel";
    }

}
