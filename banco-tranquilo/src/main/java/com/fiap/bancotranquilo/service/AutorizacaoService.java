package com.fiap.bancotranquilo.service;

import com.fiap.bancotranquilo.mensageria.PagamentoRequisicao;
import com.fiap.bancotranquilo.mensageria.PagamentoResposta;
import com.fiap.bancotranquilo.model.StatusTransacao;
import com.fiap.bancotranquilo.model.Transacao;
import com.fiap.bancotranquilo.repository.TransacaoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Regras de autorizacao do Banco Tranquilo:
 * - valor ausente ou menor/igual a zero -> RECUSADO
 * - cliente sem e-mail                  -> RECUSADO
 * - valor acima do limite (banco.limite-aprovacao, padrao R$ 1.000,00) -> RECUSADO
 * - caso contrario                      -> APROVADO
 *
 * Idempotente: se o mesmo pedido chegar de novo (reentrega do RabbitMQ), devolve a mesma resposta.
 */
@Slf4j
@Service
public class AutorizacaoService {

    private static final NumberFormat REAIS = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    private final TransacaoRepository transacaoRepository;
    private final BigDecimal limite;

    public AutorizacaoService(TransacaoRepository transacaoRepository,
                              @Value("${banco.limite-aprovacao}") BigDecimal limite) {
        this.transacaoRepository = transacaoRepository;
        this.limite = limite;
    }

    @Transactional
    public PagamentoResposta autorizar(PagamentoRequisicao requisicao) {
        Transacao transacao = transacaoRepository.findByPedidoCodigo(requisicao.pedidoCodigo())
                .orElseGet(() -> transacaoRepository.save(decidir(requisicao)));

        return new PagamentoResposta(transacao.getPedidoCodigo(), transacao.getId(),
                transacao.getStatus().name(), transacao.getMensagem(), transacao.getDataProcessamento());
    }

    private Transacao decidir(PagamentoRequisicao requisicao) {
        StatusTransacao status = StatusTransacao.APROVADO;
        String mensagem = "Pagamento aprovado pelo Banco Tranquilo";
        if (requisicao.formaPagamento() != null) {
            mensagem += " (" + requisicao.formaPagamento() + ")";
        }

        if (requisicao.valor() == null || requisicao.valor() <= 0) {
            status = StatusTransacao.RECUSADO;
            mensagem = "Valor da compra inválido";
        } else if (requisicao.clienteEmail() == null || requisicao.clienteEmail().isBlank()) {
            status = StatusTransacao.RECUSADO;
            mensagem = "Dados do cliente incompletos";
        } else if (BigDecimal.valueOf(requisicao.valor()).compareTo(limite) > 0) {
            status = StatusTransacao.RECUSADO;
            mensagem = "Limite insuficiente: compra de " + REAIS.format(requisicao.valor())
                    + " acima do limite de " + REAIS.format(limite);
        }

        log.info("[Banco Tranquilo] Pedido {} de {}: {} - {}", requisicao.pedidoCodigo(),
                requisicao.valor() == null ? "?" : REAIS.format(requisicao.valor()), status, mensagem);

        return Transacao.builder()
                .id(UUID.randomUUID().toString())
                .pedidoCodigo(requisicao.pedidoCodigo())
                .valor(requisicao.valor() == null ? 0 : requisicao.valor())
                .formaPagamento(requisicao.formaPagamento())
                .clienteEmail(requisicao.clienteEmail())
                .status(status)
                .mensagem(mensagem)
                .dataProcessamento(LocalDateTime.now())
                .build();
    }

    public BigDecimal getLimite() {
        return limite;
    }

}
