package com.fiap.bancotranquilo.mensageria;

import java.time.LocalDateTime;

/**
 * Resposta enviada ao Mercado Express (exchange mercado.express.exchange, routing key pagamento.respondido).
 */
public record PagamentoResposta(
        String pedidoCodigo,
        String transacaoId,
        String status,
        String mensagem,
        LocalDateTime dataProcessamento
) {
}
