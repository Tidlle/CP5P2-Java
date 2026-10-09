package com.fiap.mercadoexpresssec.controller.api;

import com.fiap.mercadoexpresssec.dto.CompraRequest;
import com.fiap.mercadoexpresssec.dto.PedidoResponse;
import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.model.OrigemPedido;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.model.Usuario;
import com.fiap.mercadoexpresssec.service.MercadoService;
import com.fiap.mercadoexpresssec.service.PedidoService;
import com.fiap.mercadoexpresssec.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST de compras para testes no Postman/Insomnia (HTTP Basic com os usuarios da TDS_Users_Mercado).
 *
 * POST /api/compras          -> cria a compra e envia ao Banco Tranquilo (202 Accepted)
 * GET  /api/compras/{codigo} -> acompanha o status (AGUARDANDO_PAGAMENTO -> PAGO/RECUSADO)
 * GET  /api/compras          -> CLIENTE: as proprias compras; ADMIN: todas
 * GET  /api/produtos         -> produtos disponiveis (ids para montar a compra)
 */
@RestController
@RequestMapping("/api")
public class CompraApiController {

    private final PedidoService pedidoService;
    private final MercadoService mercadoService;
    private final UsuarioService usuarioService;

    public CompraApiController(PedidoService pedidoService, MercadoService mercadoService,
                               UsuarioService usuarioService) {
        this.pedidoService = pedidoService;
        this.mercadoService = mercadoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/produtos")
    public List<Mercado> produtos() {
        return mercadoService.listar(null);
    }

    @PostMapping("/compras")
    public ResponseEntity<PedidoResponse> comprar(@Valid @RequestBody CompraRequest request,
                                                  Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        String nome = usuario.getNome();
        String email = usuario.getEmail();

        // O ADMIN pode registrar a compra para outro cliente (ex.: para receber o e-mail no proprio endereco)
        if (request.clienteEmail() != null && !request.clienteEmail().isBlank()
                && !request.clienteEmail().equalsIgnoreCase(email)) {
            if (!isAdmin(authentication)) {
                throw new AccessDeniedException("Somente ADMIN pode comprar em nome de outro e-mail");
            }
            email = request.clienteEmail();
            nome = request.clienteNome() != null && !request.clienteNome().isBlank() ? request.clienteNome() : email;
        }

        Map<Long, Integer> itens = new LinkedHashMap<>();
        request.itens().forEach(i -> itens.merge(i.produtoId(), i.quantidade(), Integer::sum));

        Pedido criado = pedidoService.finalizar(itens, nome, email, request.formaPagamento(), OrigemPedido.API);
        // Relido depois do commit: se o RabbitMQ estava fora do ar o status ja e ERRO_COMUNICACAO
        Pedido pedido = pedidoService.buscarPorCodigo(criado.getCodigo());
        if (pedido.getStatus() == StatusPedido.ERRO_COMUNICACAO) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(PedidoResponse.de(pedido));
        }
        return ResponseEntity.accepted()
                .location(URI.create("/api/compras/" + pedido.getCodigo()))
                .body(PedidoResponse.de(pedido));
    }

    @GetMapping("/compras/{codigo}")
    public PedidoResponse consultar(@PathVariable String codigo, Authentication authentication) {
        Pedido pedido = pedidoService.buscarPorCodigo(codigo);
        if (!isAdmin(authentication) && !pedido.getClienteEmail().equalsIgnoreCase(authentication.getName())) {
            throw new AccessDeniedException("Pedido de outro cliente");
        }
        return PedidoResponse.de(pedido);
    }

    @GetMapping("/compras")
    public List<PedidoResponse> listar(Authentication authentication) {
        List<Pedido> pedidos = isAdmin(authentication)
                ? pedidoService.listarTodos()
                : pedidoService.listarDoCliente(authentication.getName());
        return pedidos.stream().map(PedidoResponse::de).toList();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

}
