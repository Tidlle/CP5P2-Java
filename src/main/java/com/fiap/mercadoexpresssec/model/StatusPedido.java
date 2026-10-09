package com.fiap.mercadoexpresssec.model;

/**
 * Ciclo de vida de uma compra (Parte 2).
 * AGUARDANDO_PAGAMENTO - pedido criado e enviado ao Banco Tranquilo pela fila
 * PAGO                 - Banco Tranquilo aprovou; dispara o e-mail de confirmacao
 * RECUSADO             - Banco Tranquilo recusou; o estoque reservado e devolvido
 * ERRO_COMUNICACAO     - nao foi possivel publicar no RabbitMQ; o estoque e devolvido
 */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGO,
    RECUSADO,
    ERRO_COMUNICACAO
}
