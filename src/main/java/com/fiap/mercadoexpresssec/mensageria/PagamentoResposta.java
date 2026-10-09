package com.fiap.mercadoexpresssec.mensageria;

import java.time.LocalDateTime;

/**
 * Resposta do Banco Tranquilo (fila mercado.express.pagamentos.respostas).
 * status: APROVADO ou RECUSADO.
 */
public record PagamentoResposta(
        String pedidoCodigo,
        String transacaoId,
        String status,
        String mensagem,
        LocalDateTime dataProcessamento
) {

    public static final String APROVADO = "APROVADO";
    public static final String RECUSADO = "RECUSADO";

    public boolean aprovado() {
        return APROVADO.equalsIgnoreCase(status);
    }

}
