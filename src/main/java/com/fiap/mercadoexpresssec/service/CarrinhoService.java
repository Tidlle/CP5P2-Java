package com.fiap.mercadoexpresssec.service;

import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import jakarta.servlet.http.HttpSession;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrinho de compras guardado na sessao HTTP do usuario logado (produtoId -> quantidade).
 */
@Service
public class CarrinhoService {

    static final String ATRIBUTO_SESSAO = "CARRINHO_MERCADO_EXPRESS";

    private final MercadoRepository mercadoRepository;

    public CarrinhoService(MercadoRepository mercadoRepository) {
        this.mercadoRepository = mercadoRepository;
    }

    @Value
    public static class Linha {
        Mercado produto;
        int quantidade;
        boolean estoqueInsuficiente;

        public double getSubtotal() {
            return produto.getPreco() * quantidade;
        }
    }

    @Value
    public static class Resumo {
        List<Linha> linhas;
        double total;
        int quantidadeItens;
        boolean temProblemaDeEstoque;

        public boolean isVazio() {
            return linhas.isEmpty();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Integer> itens(HttpSession session) {
        Object atual = session.getAttribute(ATRIBUTO_SESSAO);
        if (atual instanceof LinkedHashMap<?, ?> mapa) {
            return (Map<Long, Integer>) mapa;
        }
        LinkedHashMap<Long, Integer> novo = new LinkedHashMap<>();
        session.setAttribute(ATRIBUTO_SESSAO, novo);
        return novo;
    }

    @Transactional(readOnly = true)
    public Mercado adicionar(HttpSession session, Long produtoId, int quantidade) {
        Mercado produto = mercadoRepository.findById(produtoId)
                .orElseThrow(() -> new MercadoService.MercadoNaoEncontradoException(produtoId));
        Map<Long, Integer> itens = itens(session);
        int novaQuantidade = itens.getOrDefault(produtoId, 0) + Math.max(1, quantidade);
        if (novaQuantidade > produto.getEstoque()) {
            throw new PedidoService.CompraInvalidaException("Estoque insuficiente para \"" + produto.getNome()
                    + "\" (disponível: " + produto.getEstoque() + ").");
        }
        itens.put(produtoId, novaQuantidade);
        salvar(session, itens);
        return produto;
    }

    public void atualizar(HttpSession session, Long produtoId, int quantidade) {
        Map<Long, Integer> itens = itens(session);
        if (quantidade <= 0) {
            itens.remove(produtoId);
        } else if (itens.containsKey(produtoId)) {
            itens.put(produtoId, Math.min(quantidade, 999));
        }
        salvar(session, itens);
    }

    public void remover(HttpSession session, Long produtoId) {
        Map<Long, Integer> itens = itens(session);
        itens.remove(produtoId);
        salvar(session, itens);
    }

    public void limpar(HttpSession session) {
        session.removeAttribute(ATRIBUTO_SESSAO);
    }

    public int quantidadeTotal(HttpSession session) {
        if (session == null || !(session.getAttribute(ATRIBUTO_SESSAO) instanceof Map<?, ?> mapa)) {
            return 0;
        }
        return mapa.values().stream().mapToInt(v -> (Integer) v).sum();
    }

    @Transactional(readOnly = true)
    public Resumo resumo(HttpSession session) {
        Map<Long, Integer> itens = itens(session);
        List<Linha> linhas = new ArrayList<>();
        double total = 0;
        int quantidade = 0;
        boolean problema = false;
        // Produtos excluidos pelo ADMIN enquanto estavam no carrinho saem automaticamente
        itens.keySet().removeIf(id -> !mercadoRepository.existsById(id));
        for (Map.Entry<Long, Integer> e : itens.entrySet()) {
            Mercado produto = mercadoRepository.findById(e.getKey()).orElseThrow();
            boolean faltando = produto.getEstoque() < e.getValue();
            problema |= faltando;
            linhas.add(new Linha(produto, e.getValue(), faltando));
            total += produto.getPreco() * e.getValue();
            quantidade += e.getValue();
        }
        salvar(session, itens);
        return new Resumo(linhas, PedidoService.arredondar(total), quantidade, problema);
    }

    // Regrava o atributo para que a sessao perceba a alteracao (importante com sessao distribuida)
    private void salvar(HttpSession session, Map<Long, Integer> itens) {
        session.setAttribute(ATRIBUTO_SESSAO, itens);
    }

}
