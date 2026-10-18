package com.fiap.mercadoexpresssec.service;

import com.fiap.mercadoexpresssec.mensageria.PagamentoRequisicao;
import com.fiap.mercadoexpresssec.mensageria.PagamentoResposta;
import com.fiap.mercadoexpresssec.mensageria.PedidoCriadoEvent;
import com.fiap.mercadoexpresssec.mensageria.PedidoPagoEvent;
import com.fiap.mercadoexpresssec.model.FormaPagamento;
import com.fiap.mercadoexpresssec.model.ItemPedido;
import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.model.OrigemPedido;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import com.fiap.mercadoexpresssec.repository.PedidoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final MercadoRepository mercadoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PedidoService(PedidoRepository pedidoRepository, MercadoRepository mercadoRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.mercadoRepository = mercadoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Pedido finalizar(Map<Long, Integer> itens, String clienteNome, String clienteEmail,
                            FormaPagamento formaPagamento, OrigemPedido origem) {
        if (itens == null || itens.isEmpty()) {
            throw new CompraInvalidaException("O carrinho está vazio.");
        }
        if (formaPagamento == null) {
            throw new CompraInvalidaException("Escolha a forma de pagamento.");
        }

        Pedido pedido = Pedido.builder()
                .codigo(UUID.randomUUID().toString())
                .clienteNome(clienteNome)
                .clienteEmail(UsuarioService.normalizarEmail(clienteEmail))
                .formaPagamento(formaPagamento)
                .status(StatusPedido.AGUARDANDO_PAGAMENTO)
                .origem(origem)
                .build();

        double total = 0;
        double custo = 0;
        // TreeMap: trava os produtos sempre na mesma ordem (evita deadlock entre duas compras)
        for (Map.Entry<Long, Integer> entrada : new TreeMap<>(itens).entrySet()) {
            int quantidade = entrada.getValue() == null ? 0 : entrada.getValue();
            if (quantidade < 1) {
                throw new CompraInvalidaException("Quantidade inválida para o produto " + entrada.getKey() + ".");
            }
            Mercado produto = mercadoRepository.findByIdParaAtualizarEstoque(entrada.getKey())
                    .orElseThrow(() -> new CompraInvalidaException("Produto " + entrada.getKey() + " não encontrado."));
            if (produto.getEstoque() < quantidade) {
                throw new CompraInvalidaException("Estoque insuficiente para \"" + produto.getNome()
                        + "\" (disponível: " + produto.getEstoque() + ").");
            }

            // Reserva o estoque; se o banco recusar, ele e devolvido
            produto.setEstoque(produto.getEstoque() - quantidade);

            double custoUnitario = produto.getCusto() == null ? 0 : produto.getCusto();
            pedido.adicionarItem(ItemPedido.builder()
                    .produtoId(produto.getId())
                    .produtoNome(produto.getNome())
                    .produtoSetor(produto.getSetor())
                    .quantidade(quantidade)
                    .precoUnitario(produto.getPreco())
                    .custoUnitario(custoUnitario)
                    .build());
            total += produto.getPreco() * quantidade;
            custo += custoUnitario * quantidade;
        }
        pedido.setValorTotal(arredondar(total));
        pedido.setCustoTotal(arredondar(custo));

        Pedido salvo = pedidoRepository.save(pedido);
        eventPublisher.publishEvent(new PedidoCriadoEvent(new PagamentoRequisicao(
                salvo.getCodigo(), salvo.getValorTotal(), salvo.getFormaPagamento().name(),
                salvo.getClienteNome(), salvo.getClienteEmail(), LocalDateTime.now())));
        log.info("Pedido {} criado: R$ {} ({} item(ns)) para {}", salvo.getCodigo(), salvo.getValorTotal(),
                salvo.getQuantidadeItens(), salvo.getClienteEmail());
        return salvo;
    }

    @Transactional
    public void processarResposta(PagamentoResposta resposta) {
        Pedido pedido = pedidoRepository.findByCodigo(resposta.pedidoCodigo()).orElse(null);
        if (pedido == null) {
            log.warn("Resposta do banco para pedido inexistente: {}", resposta.pedidoCodigo());
            return;
        }
        // Idempotencia: uma resposta repetida (reentrega do RabbitMQ) nao processa o pedido de novo
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            log.info("Pedido {} ja estava {}; resposta ignorada", pedido.getCodigo(), pedido.getStatus());
            return;
        }

        pedido.setTransacaoId(resposta.transacaoId());
        pedido.setMensagemBanco(resposta.mensagem());
        pedido.setDataResposta(resposta.dataProcessamento() != null ? resposta.dataProcessamento() : LocalDateTime.now());

        if (resposta.aprovado()) {
            pedido.setStatus(StatusPedido.PAGO);
            eventPublisher.publishEvent(new PedidoPagoEvent(pedido.getCodigo()));
        } else {
            pedido.setStatus(StatusPedido.RECUSADO);
            devolverEstoque(pedido);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalhaComunicacao(String codigo) {
        pedidoRepository.findByCodigo(codigo).ifPresent(pedido -> {
            if (pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO) {
                pedido.setStatus(StatusPedido.ERRO_COMUNICACAO);
                pedido.setMensagemBanco("Não foi possível falar com o Banco Tranquilo. Tente novamente.");
                pedido.setDataResposta(LocalDateTime.now());
                devolverEstoque(pedido);
            }
        });
    }

    @Transactional(readOnly = true)
    public Pedido buscarPorCodigo(String codigo) {
        return pedidoRepository.findByCodigo(codigo).orElseThrow(() -> new PedidoNaoEncontradoException(codigo));
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarDoCliente(String email) {
        return pedidoRepository.findByClienteEmailIgnoreCaseOrderByDataCriacaoDesc(UsuarioService.normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarTodos() {
        return pedidoRepository.findAllByOrderByDataCriacaoDesc();
    }

    private void devolverEstoque(Pedido pedido) {
        for (ItemPedido item : pedido.getItens()) {
            mercadoRepository.findByIdParaAtualizarEstoque(item.getProdutoId())
                    .ifPresent(produto -> produto.setEstoque(produto.getEstoque() + item.getQuantidade()));
        }
    }

    static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    public static class CompraInvalidaException extends RuntimeException {
        public CompraInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    public static class PedidoNaoEncontradoException extends RuntimeException {
        public PedidoNaoEncontradoException(String codigo) {
            super("Pedido não encontrado: " + codigo);
        }
    }

}
