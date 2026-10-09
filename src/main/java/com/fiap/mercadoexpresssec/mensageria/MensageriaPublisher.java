package com.fiap.mercadoexpresssec.mensageria;

import com.fiap.mercadoexpresssec.service.PedidoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica as mensagens no RabbitMQ somente depois que a transacao do banco de dados foi confirmada.
 */
@Slf4j
@Component
public class MensageriaPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PedidoService pedidoService;

    public MensageriaPublisher(RabbitTemplate rabbitTemplate, PedidoService pedidoService) {
        this.rabbitTemplate = rabbitTemplate;
        this.pedidoService = pedidoService;
    }

    /** Compra finalizada -> requisicao de pagamento para o Banco Tranquilo. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void solicitarPagamento(PedidoCriadoEvent event) {
        PagamentoRequisicao requisicao = event.requisicao();
        try {
            rabbitTemplate.convertAndSend(MensageriaConfig.BANCO_EXCHANGE,
                    MensageriaConfig.BANCO_ROUTING_PAGAMENTO, requisicao);
            log.info("[RabbitMQ] Pagamento do pedido {} (R$ {}) enviado ao Banco Tranquilo",
                    requisicao.pedidoCodigo(), requisicao.valor());
        } catch (AmqpException ex) {
            log.error("[RabbitMQ] Falha ao enviar o pedido {} ao Banco Tranquilo: {}",
                    requisicao.pedidoCodigo(), ex.getMessage());
            pedidoService.registrarFalhaComunicacao(requisicao.pedidoCodigo());
        }
    }

    /** Pagamento aprovado -> mensagem na fila de e-mails. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void solicitarEmailConfirmacao(PedidoPagoEvent event) {
        publicarEmailConfirmacao(event.pedidoCodigo(), false);
    }

    public void publicarEmailConfirmacao(String pedidoCodigo, boolean reenvio) {
        rabbitTemplate.convertAndSend(MensageriaConfig.MERCADO_EXCHANGE,
                MensageriaConfig.MERCADO_ROUTING_EMAIL, new EmailConfirmacaoMensagem(pedidoCodigo, reenvio));
        log.info("[RabbitMQ] E-mail de confirmacao do pedido {} colocado na fila", pedidoCodigo);
    }

}
