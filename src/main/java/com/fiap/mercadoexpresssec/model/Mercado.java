package com.fiap.mercadoexpresssec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "TDS_Sec_MVC_TB_Mercado")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mercado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe o nome do produto")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "Informe o tipo do produto")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String tipo;

    @NotBlank(message = "Informe o setor do mercado")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String setor;

    @NotNull(message = "Informe o tamanho/medida")
    @DecimalMin(value = "0.01", message = "O tamanho deve ser maior que zero")
    private Double tamanho;

    @NotNull(message = "Informe o preço")
    @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero")
    private Double preco;

    // Parte 2: custo unitario, usado no calculo de lucro do dashboard.
    // Coluna aceita nulo para nao quebrar tabelas criadas na Parte 1 (o DataInitializer preenche).
    @NotNull(message = "Informe o custo")
    @DecimalMin(value = "0.00", message = "O custo não pode ser negativo")
    private Double custo;

    @NotNull(message = "Informe a quantidade em estoque")
    @Min(value = 0, message = "O estoque não pode ser negativo")
    private Integer estoque;

}
