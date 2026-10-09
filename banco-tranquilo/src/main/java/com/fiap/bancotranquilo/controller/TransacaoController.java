package com.fiap.bancotranquilo.controller;

import com.fiap.bancotranquilo.model.Transacao;
import com.fiap.bancotranquilo.repository.TransacaoRepository;
import com.fiap.bancotranquilo.service.AutorizacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API de consulta do Banco Tranquilo (http://localhost:8081).
 */
@RestController
@RequestMapping("/api")
public class TransacaoController {

    private final TransacaoRepository transacaoRepository;
    private final AutorizacaoService autorizacaoService;

    public TransacaoController(TransacaoRepository transacaoRepository, AutorizacaoService autorizacaoService) {
        this.transacaoRepository = transacaoRepository;
        this.autorizacaoService = autorizacaoService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "servico", "Banco Tranquilo",
                "status", "UP",
                "limiteAprovacao", autorizacaoService.getLimite(),
                "transacoesProcessadas", transacaoRepository.count());
    }

    @GetMapping("/transacoes")
    public List<Transacao> listar() {
        return transacaoRepository.findAllByOrderByDataProcessamentoDesc();
    }

    @GetMapping("/transacoes/{pedidoCodigo}")
    public ResponseEntity<Transacao> porPedido(@PathVariable String pedidoCodigo) {
        return ResponseEntity.of(transacaoRepository.findByPedidoCodigo(pedidoCodigo));
    }

}
