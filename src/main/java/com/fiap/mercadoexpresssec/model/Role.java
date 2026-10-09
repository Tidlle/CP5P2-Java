package com.fiap.mercadoexpresssec.model;

/**
 * Tipos de usuario do sistema.
 * ADMIN   - gerente do mercado: CRUD completo de produtos e painel administrativo.
 * CLIENTE - cliente cadastrado pela tela de Sign Up: apenas consulta produtos.
 */
public enum Role {
    ADMIN,
    CLIENTE
}
