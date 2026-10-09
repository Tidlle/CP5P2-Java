package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.service.EmailService;
import com.fiap.mercadoexpresssec.service.PedidoService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Minhas compras" - cada usuario ve os proprios pedidos; o ADMIN pode abrir qualquer pedido.
 */
@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final EmailService emailService;

    public PedidoController(PedidoService pedidoService, EmailService emailService) {
        this.pedidoService = pedidoService;
        this.emailService = emailService;
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        model.addAttribute("pedidos", pedidoService.listarDoCliente(authentication.getName()));
        return "pedido/list";
    }

    @GetMapping("/{codigo}")
    public String detalhe(@PathVariable String codigo, Authentication authentication, Model model) {
        Pedido pedido = pedidoService.buscarPorCodigo(codigo);
        if (!podeVer(pedido, authentication)) {
            throw new AccessDeniedException("Pedido de outro cliente");
        }
        model.addAttribute("pedido", pedido);
        model.addAttribute("codigoCurto", EmailService.codigoCurto(pedido.getCodigo()));
        model.addAttribute("emails", emailService.listarDoPedido(codigo));
        return "pedido/view";
    }

    @ExceptionHandler(PedidoService.PedidoNaoEncontradoException.class)
    public String naoEncontrado(PedidoService.PedidoNaoEncontradoException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        return "redirect:/pedidos";
    }

    static boolean podeVer(Pedido pedido, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin || pedido.getClienteEmail().equalsIgnoreCase(authentication.getName());
    }

}
