package com.fiap.mercadoexpresssec.model;

/**
 * Canal em que a compra foi feita.
 * WEB  - tela do carrinho
 * API  - endpoint REST (Postman/Insomnia)
 * SEED - historico de exemplo gerado na inicializacao (nao passa pelo banco)
 */
public enum OrigemPedido {
    WEB,
    API,
    SEED
}
