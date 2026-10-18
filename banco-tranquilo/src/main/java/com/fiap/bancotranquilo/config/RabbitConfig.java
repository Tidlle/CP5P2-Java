package com.fiap.bancotranquilo.config;

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

@Configuration
public class RabbitConfig {

    // Entrada: requisicoes de pagamento
    public static final String BANCO_EXCHANGE = "banco.tranquilo.exchange";
    public static final String BANCO_FILA_PAGAMENTOS = "banco.tranquilo.pagamentos";
    public static final String BANCO_ROUTING_PAGAMENTO = "pagamento.solicitado";
    public static final String BANCO_DLX = "banco.tranquilo.dlx";
    public static final String BANCO_DLQ = "banco.tranquilo.dlq";

    // Saida: respostas para o Mercado Express
    public static final String MERCADO_EXCHANGE = "mercado.express.exchange";
    public static final String MERCADO_ROUTING_RESPOSTA = "pagamento.respondido";

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

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

    // O banco so publica na exchange do mercado; a fila de respostas e declarada pelo Mercado Express
    @Bean
    public DirectExchange mercadoExchange() {
        return new DirectExchange(MERCADO_EXCHANGE, true, false);
    }

}
