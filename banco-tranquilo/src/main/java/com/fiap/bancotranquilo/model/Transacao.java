package com.fiap.bancotranquilo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transacao processada pelo Banco Tranquilo (uma por pedido do Mercado Express).
 */
@Entity
@Table(name = "TDS_Banco_Tranquilo_Transacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transacao {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "pedido_codigo", nullable = false, unique = true, length = 36)
    private String pedidoCodigo;

    @Column(nullable = false)
    private Double valor;

    @Column(name = "forma_pagamento", length = 20)
    private String formaPagamento;

    @Column(name = "cliente_email", length = 120)
    private String clienteEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusTransacao status;

    @Column(nullable = false, length = 200)
    private String mensagem;

    @Column(name = "data_processamento", nullable = false)
    private LocalDateTime dataProcessamento;

}
