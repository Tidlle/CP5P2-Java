package com.fiap.mercadoexpresssec.mensageria;

import com.fiap.mercadoexpresssec.service.PedidoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome as respostas do Banco Tranquilo e atualiza o status do pedido.
 */
@Slf4j
@Component
public class PagamentoRespostaListener {

    private final PedidoService pedidoService;

    public PagamentoRespostaListener(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @RabbitListener(queues = MensageriaConfig.MERCADO_FILA_RESPOSTAS)
    public void receber(PagamentoResposta resposta) {
        log.info("[RabbitMQ] Resposta do Banco Tranquilo para o pedido {}: {} ({})",
                resposta.pedidoCodigo(), resposta.status(), resposta.mensagem());
        pedidoService.processarResposta(resposta);
    }

}
