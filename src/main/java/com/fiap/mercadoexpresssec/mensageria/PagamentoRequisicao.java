package com.fiap.mercadoexpresssec.mensageria;

import java.time.LocalDateTime;

/**
 * Mensagem enviada ao Banco Tranquilo (fila banco.tranquilo.pagamentos) pedindo a autorizacao
 * de pagamento de uma compra.
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
