package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.mensageria.MensageriaPublisher;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.service.DashboardService;
import com.fiap.mercadoexpresssec.service.PedidoService;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Dashboard da gerencia do Mercado Express (Parte 2) - somente ADMIN.
 */
@Controller
@RequestMapping("/admin")
public class DashboardController {

    private final DashboardService dashboardService;
    private final PedidoService pedidoService;
    private final MensageriaPublisher mensageriaPublisher;

    public DashboardController(DashboardService dashboardService, PedidoService pedidoService,
                               MensageriaPublisher mensageriaPublisher) {
        this.dashboardService = dashboardService;
        this.pedidoService = pedidoService;
        this.mensageriaPublisher = mensageriaPublisher;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("d", dashboardService.montar());
        model.addAttribute("estoqueBaixoLimite", DashboardService.ESTOQUE_BAIXO);
        return "admin/dashboard";
    }

    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        model.addAttribute("pedidos", pedidoService.listarTodos());
        return "admin/pedidos";
    }

    /** Coloca o e-mail de confirmacao de novo na fila (ex.: o SMTP estava fora do ar). */
    @PostMapping("/pedidos/{codigo}/reenviar-email")
    public String reenviarEmail(@PathVariable String codigo, RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoService.buscarPorCodigo(codigo);
        if (pedido.getStatus() != StatusPedido.PAGO) {
            redirectAttributes.addFlashAttribute("erro", "Só é possível enviar a confirmação de pedidos pagos.");
        } else {
            try {
                mensageriaPublisher.publicarEmailConfirmacao(codigo, true);
                redirectAttributes.addFlashAttribute("sucesso", "E-mail de confirmação colocado na fila de envio.");
            } catch (AmqpException ex) {
                redirectAttributes.addFlashAttribute("erro", "RabbitMQ indisponível: " + ex.getMessage());
            }
        }
        return "redirect:/pedidos/" + codigo;
    }

}
