package com.fiap.bancotranquilo.mensageria;

import com.fiap.bancotranquilo.config.RabbitConfig;
import com.fiap.bancotranquilo.service.AutorizacaoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PagamentoListener {

    private final AutorizacaoService autorizacaoService;
    private final RabbitTemplate rabbitTemplate;
    private final long tempoAnaliseMs;

    public PagamentoListener(AutorizacaoService autorizacaoService, RabbitTemplate rabbitTemplate,
                             @Value("${banco.tempo-analise-ms:0}") long tempoAnaliseMs) {
        this.autorizacaoService = autorizacaoService;
        this.rabbitTemplate = rabbitTemplate;
        this.tempoAnaliseMs = tempoAnaliseMs;
    }

    @RabbitListener(queues = RabbitConfig.BANCO_FILA_PAGAMENTOS)
    public void receber(PagamentoRequisicao requisicao) throws InterruptedException {
        if (requisicao == null || requisicao.pedidoCodigo() == null || requisicao.pedidoCodigo().isBlank()) {
            // Mensagem sem pedido nao tem para quem responder: vai direto para a DLQ
            throw new AmqpRejectAndDontRequeueException("Mensagem sem pedidoCodigo");
        }
        log.info("[Banco Tranquilo] Requisicao recebida: pedido {} - {}", requisicao.pedidoCodigo(), requisicao);

        if (tempoAnaliseMs > 0) {
            Thread.sleep(tempoAnaliseMs); // analise antifraude simulada
        }

        PagamentoResposta resposta = autorizacaoService.autorizar(requisicao);
        rabbitTemplate.convertAndSend(RabbitConfig.MERCADO_EXCHANGE, RabbitConfig.MERCADO_ROUTING_RESPOSTA, resposta);
        log.info("[Banco Tranquilo] Resposta enviada ao Mercado Express: pedido {} -> {}",
                resposta.pedidoCodigo(), resposta.status());
    }

}
