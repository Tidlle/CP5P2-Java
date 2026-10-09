package com.fiap.mercadoexpresssec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Controle de envio de e-mails para os clientes (Parte 2).
 * Cada tentativa de envio gera uma linha, com sucesso (ENVIADO) ou erro (FALHA).
 */
@Entity
@Table(name = "TDS_Mercado_Email")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_codigo", nullable = false, length = 36)
    private String pedidoCodigo;

    @Column(nullable = false, length = 120)
    private String destinatario;

    @Column(nullable = false, length = 200)
    private String assunto;

    @ToString.Exclude
    @Column(columnDefinition = "TEXT")
    private String corpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusEmail status;

    @Column(length = 500)
    private String erro;

    @Column(name = "data_envio", nullable = false)
    private LocalDateTime dataEnvio;

    @PrePersist
    void prePersist() {
        if (dataEnvio == null) {
            dataEnvio = LocalDateTime.now();
        }
    }

}
