package com.fiap.mercadoexpresssec.dto;

import com.fiap.mercadoexpresssec.model.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo JSON do POST /api/compras (Postman/Insomnia).
 * clienteNome/clienteEmail sao opcionais: se omitidos, a compra fica no nome do usuario autenticado.
 * Somente ADMIN pode comprar em nome de outro e-mail.
 */
public record CompraRequest(
        @Size(max = 100) String clienteNome,
        @Email(message = "E-mail inválido") @Size(max = 120) String clienteEmail,
        @NotNull(message = "Informe a forma de pagamento (PIX, CARTAO_CREDITO ou CARTAO_DEBITO)") FormaPagamento formaPagamento,
        @NotEmpty(message = "Informe ao menos um item") List<@Valid Item> itens
) {

    public record Item(
            @NotNull(message = "Informe o produtoId") Long produtoId,
            @NotNull(message = "Informe a quantidade") @Min(value = 1, message = "A quantidade mínima é 1")
            @Max(value = 999, message = "A quantidade máxima é 999") Integer quantidade
    ) {
    }

}
