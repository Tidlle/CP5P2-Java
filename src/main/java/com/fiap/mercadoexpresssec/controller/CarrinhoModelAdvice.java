package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.service.CarrinhoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Disponibiliza a quantidade de itens do carrinho para o badge do menu em todas as telas.
 * Usa getSession(false) para nao criar sessao para visitantes anonimos.
 */
@ControllerAdvice(annotations = Controller.class)
public class CarrinhoModelAdvice {

    private final CarrinhoService carrinhoService;

    public CarrinhoModelAdvice(CarrinhoService carrinhoService) {
        this.carrinhoService = carrinhoService;
    }

    @ModelAttribute("qtdCarrinho")
    public int quantidadeNoCarrinho(HttpServletRequest request) {
        return carrinhoService.quantidadeTotal(request.getSession(false));
    }

}
