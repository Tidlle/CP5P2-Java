package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.model.FormaPagamento;
import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.model.OrigemPedido;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.model.Usuario;
import com.fiap.mercadoexpresssec.service.CarrinhoService;
import com.fiap.mercadoexpresssec.service.MercadoService;
import com.fiap.mercadoexpresssec.service.PedidoService;
import com.fiap.mercadoexpresssec.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;

/**
 * Carrinho de compras (Parte 2) - ADMIN e CLIENTE.
 * Ao finalizar, o pedido e enviado ao Banco Tranquilo pelo RabbitMQ.
 */
@Controller
@RequestMapping("/carrinho")
public class CarrinhoController {

    private final CarrinhoService carrinhoService;
    private final PedidoService pedidoService;
    private final UsuarioService usuarioService;

    public CarrinhoController(CarrinhoService carrinhoService, PedidoService pedidoService,
                              UsuarioService usuarioService) {
        this.carrinhoService = carrinhoService;
        this.pedidoService = pedidoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String ver(HttpSession session, Model model) {
        model.addAttribute("carrinho", carrinhoService.resumo(session));
        model.addAttribute("formasPagamento", FormaPagamento.values());
        return "carrinho/carrinho";
    }

    @PostMapping("/adicionar")
    public String adicionar(@RequestParam Long produtoId, @RequestParam(defaultValue = "1") int quantidade,
                            @RequestParam(required = false) String voltarPara,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            Mercado produto = carrinhoService.adicionar(session, produtoId, quantidade);
            redirectAttributes.addFlashAttribute("sucesso", "\"" + produto.getNome() + "\" adicionado ao carrinho.");
        } catch (PedidoService.CompraInvalidaException | MercadoService.MercadoNaoEncontradoException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        }
        // Volta para a tela de origem (somente caminhos internos, para nao virar open redirect)
        boolean destinoInterno = voltarPara != null && voltarPara.startsWith("/mercado") && !voltarPara.startsWith("//");
        return "redirect:" + (destinoInterno ? voltarPara : "/mercado");
    }

    @PostMapping("/atualizar/{produtoId}")
    public String atualizar(@PathVariable Long produtoId, @RequestParam int quantidade, HttpSession session) {
        carrinhoService.atualizar(session, produtoId, quantidade);
        return "redirect:/carrinho";
    }

    @PostMapping("/remover/{produtoId}")
    public String remover(@PathVariable Long produtoId, HttpSession session) {
        carrinhoService.remover(session, produtoId);
        return "redirect:/carrinho";
    }

    @PostMapping("/finalizar")
    public String finalizar(@RequestParam(required = false) FormaPagamento formaPagamento,
                            Authentication authentication, HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        try {
            Pedido pedido = pedidoService.finalizar(new LinkedHashMap<>(carrinhoService.itens(session)),
                    usuario.getNome(), usuario.getEmail(), formaPagamento, OrigemPedido.WEB);
            // O envio ao RabbitMQ acontece no commit: relê o pedido para saber se ele chegou ao banco
            if (pedidoService.buscarPorCodigo(pedido.getCodigo()).getStatus() == StatusPedido.ERRO_COMUNICACAO) {
                redirectAttributes.addFlashAttribute("erro",
                        "Não foi possível falar com o Banco Tranquilo agora. O estoque foi devolvido e o carrinho mantido; tente novamente.");
                return "redirect:/pedidos/" + pedido.getCodigo();
            }
            carrinhoService.limpar(session);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Compra enviada ao Banco Tranquilo! Aguardando a confirmação do pagamento.");
            return "redirect:/pedidos/" + pedido.getCodigo();
        } catch (PedidoService.CompraInvalidaException ex) {
            redirectAttributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/carrinho";
        }
    }

}
