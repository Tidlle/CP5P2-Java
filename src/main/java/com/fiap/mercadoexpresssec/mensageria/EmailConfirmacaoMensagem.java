package com.fiap.mercadoexpresssec.mensageria;

/**
 * Pedido de envio do e-mail de confirmacao de pagamento (fila mercado.express.notificacoes.email).
 * reenvio = true quando o ADMIN pede para mandar de novo um e-mail que ja foi enviado.
 */
public record EmailConfirmacaoMensagem(String pedidoCodigo, boolean reenvio) {
}
