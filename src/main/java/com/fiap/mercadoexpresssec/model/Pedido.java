package com.fiap.mercadoexpresssec.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compra realizada no Mercado Express (Parte 2).
 * O "codigo" (UUID) e o identificador que trafega nas mensagens do RabbitMQ.
 */
@Entity
@Table(name = "TDS_Mercado_Pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String codigo;

    @Column(name = "cliente_nome", nullable = false, length = 100)
    private String clienteNome;

    @Column(name = "cliente_email", nullable = false, length = 120)
    private String clienteEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusPedido status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemPedido origem;

    @Column(name = "valor_total", nullable = false)
    private Double valorTotal;

    @Column(name = "custo_total", nullable = false)
    private Double custoTotal;

    // Preenchidos com a resposta do Banco Tranquilo
    @Column(name = "transacao_id", length = 36)
    private String transacaoId;

    @Column(name = "mensagem_banco", length = 200)
    private String mensagemBanco;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_resposta")
    private LocalDateTime dataResposta;

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ItemPedido> itens = new ArrayList<>();

    public void adicionarItem(ItemPedido item) {
        item.setPedido(this);
        itens.add(item);
    }

    public double getLucro() {
        return valorTotal - custoTotal;
    }

    public int getQuantidadeItens() {
        return itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    @PrePersist
    void prePersist() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }

}
