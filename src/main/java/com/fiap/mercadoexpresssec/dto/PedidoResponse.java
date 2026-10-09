package com.fiap.mercadoexpresssec.dto;

import com.fiap.mercadoexpresssec.model.Pedido;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON devolvido pela API de compras.
 */
public record PedidoResponse(
        String codigo,
        String status,
        String clienteNome,
        String clienteEmail,
        String formaPagamento,
        String origem,
        Double valorTotal,
        String transacaoId,
        String mensagemBanco,
        LocalDateTime dataCriacao,
        LocalDateTime dataResposta,
        List<Item> itens
) {

    public record Item(Long produtoId, String produto, Integer quantidade, Double precoUnitario, Double subtotal) {
    }

    public static PedidoResponse de(Pedido p) {
        return new PedidoResponse(
                p.getCodigo(), p.getStatus().name(), p.getClienteNome(), p.getClienteEmail(),
                p.getFormaPagamento().name(), p.getOrigem().name(), p.getValorTotal(),
                p.getTransacaoId(), p.getMensagemBanco(), p.getDataCriacao(), p.getDataResposta(),
                p.getItens().stream()
                        .map(i -> new Item(i.getProdutoId(), i.getProdutoNome(), i.getQuantidade(),
                                i.getPrecoUnitario(), i.getSubtotal()))
                        .toList());
    }

}
