package com.fiap.mercadoexpresssec.mensageria;

/**
 * Evento interno publicado quando um pedido e gravado. A mensagem para o Banco Tranquilo so e
 * enviada depois do commit (MensageriaPublisher), para o banco nunca responder sobre um pedido
 * que ainda nao existe no PostgreSQL.
 */
public record PedidoCriadoEvent(PagamentoRequisicao requisicao) {
}
