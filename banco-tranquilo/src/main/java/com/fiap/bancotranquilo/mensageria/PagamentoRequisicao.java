package com.fiap.bancotranquilo.mensageria;

import java.time.LocalDateTime;

/**
 * Mensagem recebida do Mercado Express (fila banco.tranquilo.pagamentos).
 */
public record PagamentoRequisicao(
        String pedidoCodigo,
        Double valor,
        String formaPagamento,
        String clienteNome,
        String clienteEmail,
        LocalDateTime dataSolicitacao
) {
}
