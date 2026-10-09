package com.fiap.mercadoexpresssec.mensageria;

import com.fiap.mercadoexpresssec.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a fila de e-mails e envia a confirmacao de pagamento ao cliente.
 */
@Component
public class EmailConfirmacaoListener {

    private final EmailService emailService;

    public EmailConfirmacaoListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = MensageriaConfig.MERCADO_FILA_EMAILS)
    public void receber(EmailConfirmacaoMensagem mensagem) {
        emailService.enviarConfirmacao(mensagem.pedidoCodigo(), mensagem.reenvio());
    }

}
