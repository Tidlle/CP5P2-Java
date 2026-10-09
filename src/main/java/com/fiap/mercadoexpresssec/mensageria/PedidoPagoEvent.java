package com.fiap.mercadoexpresssec.mensageria;

/**
 * Evento interno publicado quando o Banco Tranquilo aprova o pagamento.
 * Depois do commit vira uma mensagem na fila de e-mails.
 */
public record PedidoPagoEvent(String pedidoCodigo) {
}
