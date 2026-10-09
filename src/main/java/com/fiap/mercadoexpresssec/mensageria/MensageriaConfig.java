package com.fiap.mercadoexpresssec.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ usada na integracao Mercado Express <-> Banco Tranquilo.
 *
 * <pre>
 *  Mercado Express                         Banco Tranquilo (microsservico banco-tranquilo/)
 *  ---------------                         ------------------------------------------------
 *  finalizar compra
 *    -> banco.tranquilo.exchange  --[pagamento.solicitado]-->  banco.tranquilo.pagamentos
 *                                                                 (aprova ou recusa)
 *  mercado.express.pagamentos.respostas  <--[pagamento.respondido]--  mercado.express.exchange
 *    (PAGO -> publica e-mail)
 *    -> mercado.express.exchange  --[email.confirmacao]-->  mercado.express.notificacoes.email
 *                                                             (envia e grava em TDS_Mercado_Email)
 *
 *  Mensagens que falham 3 vezes vao para as DLQs (banco.tranquilo.dlq / mercado.express.dlq).
 * </pre>
 *
 * As declaracoes da exchange e da fila do banco sao identicas as do microsservico banco-tranquilo,
 * entao tanto faz qual aplicacao sobe primeiro: nenhuma mensagem se perde.
 */
@Configuration
public class MensageriaConfig {

    // ---- Banco Tranquilo (requisicoes de pagamento) ----
    public static final String BANCO_EXCHANGE = "banco.tranquilo.exchange";
    public static final String BANCO_FILA_PAGAMENTOS = "banco.tranquilo.pagamentos";
    public static final String BANCO_ROUTING_PAGAMENTO = "pagamento.solicitado";
    public static final String BANCO_DLX = "banco.tranquilo.dlx";
    public static final String BANCO_DLQ = "banco.tranquilo.dlq";

    // ---- Mercado Express (respostas do banco e e-mails) ----
    public static final String MERCADO_EXCHANGE = "mercado.express.exchange";
    public static final String MERCADO_FILA_RESPOSTAS = "mercado.express.pagamentos.respostas";
    public static final String MERCADO_ROUTING_RESPOSTA = "pagamento.respondido";
    public static final String MERCADO_FILA_EMAILS = "mercado.express.notificacoes.email";
    public static final String MERCADO_ROUTING_EMAIL = "email.confirmacao";
    public static final String MERCADO_DLX = "mercado.express.dlx";
    public static final String MERCADO_DLQ = "mercado.express.dlq";

    /**
     * JSON nas mensagens (legivel no painel do RabbitMQ e facil de publicar na mao).
     * O tipo do objeto e deduzido pelo parametro do @RabbitListener, e nao pelo cabecalho
     * __TypeId__, porque as classes do banco e do mercado sao diferentes.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    // ---------- Banco Tranquilo ----------

    @Bean
    public DirectExchange bancoExchange() {
        return new DirectExchange(BANCO_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange bancoDlx() {
        return new FanoutExchange(BANCO_DLX, true, false);
    }

    @Bean
    public Queue bancoFilaPagamentos() {
        return QueueBuilder.durable(BANCO_FILA_PAGAMENTOS).deadLetterExchange(BANCO_DLX).build();
    }

    @Bean
    public Queue bancoDlq() {
        return QueueBuilder.durable(BANCO_DLQ).build();
    }

    @Bean
    public Binding bancoPagamentosBinding() {
        return BindingBuilder.bind(bancoFilaPagamentos()).to(bancoExchange()).with(BANCO_ROUTING_PAGAMENTO);
    }

    @Bean
    public Binding bancoDlqBinding() {
        return BindingBuilder.bind(bancoDlq()).to(bancoDlx());
    }

    // ---------- Mercado Express ----------

    @Bean
    public DirectExchange mercadoExchange() {
        return new DirectExchange(MERCADO_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange mercadoDlx() {
        return new FanoutExchange(MERCADO_DLX, true, false);
    }

    @Bean
    public Queue mercadoFilaRespostas() {
        return QueueBuilder.durable(MERCADO_FILA_RESPOSTAS).deadLetterExchange(MERCADO_DLX).build();
    }

    @Bean
    public Queue mercadoFilaEmails() {
        return QueueBuilder.durable(MERCADO_FILA_EMAILS).deadLetterExchange(MERCADO_DLX).build();
    }

    @Bean
    public Queue mercadoDlq() {
        return QueueBuilder.durable(MERCADO_DLQ).build();
    }

    @Bean
    public Binding mercadoRespostasBinding() {
        return BindingBuilder.bind(mercadoFilaRespostas()).to(mercadoExchange()).with(MERCADO_ROUTING_RESPOSTA);
    }

    @Bean
    public Binding mercadoEmailsBinding() {
        return BindingBuilder.bind(mercadoFilaEmails()).to(mercadoExchange()).with(MERCADO_ROUTING_EMAIL);
    }

    @Bean
    public Binding mercadoDlqBinding() {
        return BindingBuilder.bind(mercadoDlq()).to(mercadoDlx());
    }

}
