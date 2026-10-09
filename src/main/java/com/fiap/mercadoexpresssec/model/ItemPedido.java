package com.fiap.mercadoexpresssec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Item de uma compra. Nome, setor, preco e custo sao copiados do produto no momento
 * da compra, para que editar ou excluir o produto depois nao altere o historico.
 */
@Entity
@Table(name = "TDS_Mercado_Item_Pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "produto_nome", nullable = false, length = 100)
    private String produtoNome;

    @Column(name = "produto_setor", nullable = false, length = 60)
    private String produtoSetor;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unitario", nullable = false)
    private Double precoUnitario;

    @Column(name = "custo_unitario", nullable = false)
    private Double custoUnitario;

    public double getSubtotal() {
        return precoUnitario * quantidade;
    }

    public double getLucro() {
        return (precoUnitario - custoUnitario) * quantidade;
    }

}
