package com.fiap.bancotranquilo;

import com.fiap.bancotranquilo.config.RabbitConfig;
import com.fiap.bancotranquilo.mensageria.PagamentoListener;
import com.fiap.bancotranquilo.mensageria.PagamentoRequisicao;
import com.fiap.bancotranquilo.mensageria.PagamentoResposta;
import com.fiap.bancotranquilo.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PagamentoListenerTest {

    @Autowired
    private PagamentoListener listener;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void limpar() {
        reset(rabbitTemplate);
    }

    @Test
    void aprovaCompraDentroDoLimiteERespondeAoMercado() throws Exception {
        String pedido = UUID.randomUUID().toString();
        listener.receber(requisicao(pedido, 150.0));

        PagamentoResposta resposta = respostaPublicada(1);
        assertThat(resposta.pedidoCodigo()).isEqualTo(pedido);
        assertThat(resposta.status()).isEqualTo("APROVADO");
        assertThat(resposta.transacaoId()).isNotBlank();
        assertThat(transacaoRepository.findByPedidoCodigo(pedido)).isPresent();
    }

    @Test
    void recusaCompraAcimaDoLimite() throws Exception {
        listener.receber(requisicao(UUID.randomUUID().toString(), 1500.0));

        PagamentoResposta resposta = respostaPublicada(1);
        assertThat(resposta.status()).isEqualTo("RECUSADO");
        assertThat(resposta.mensagem()).contains("Limite insuficiente");
    }

    @Test
    void mensagemRepetidaDevolveAMesmaTransacao() throws Exception {
        String pedido = UUID.randomUUID().toString();
        listener.receber(requisicao(pedido, 80.0));
        listener.receber(requisicao(pedido, 80.0));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(2)).convertAndSend(eq(RabbitConfig.MERCADO_EXCHANGE),
                eq(RabbitConfig.MERCADO_ROUTING_RESPOSTA), captor.capture());
        PagamentoResposta primeira = (PagamentoResposta) captor.getAllValues().get(0);
        PagamentoResposta segunda = (PagamentoResposta) captor.getAllValues().get(1);
        assertThat(segunda.transacaoId()).isEqualTo(primeira.transacaoId());
        assertThat(transacaoRepository.findAll()).filteredOn(t -> t.getPedidoCodigo().equals(pedido)).hasSize(1);
    }

    @Test
    void mensagemSemPedidoVaiParaDlq() {
        assertThatThrownBy(() -> listener.receber(requisicao(null, 10.0)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private PagamentoResposta respostaPublicada(int vezes) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(vezes)).convertAndSend(eq(RabbitConfig.MERCADO_EXCHANGE),
                eq(RabbitConfig.MERCADO_ROUTING_RESPOSTA), captor.capture());
        return (PagamentoResposta) captor.getValue();
    }

    private static PagamentoRequisicao requisicao(String pedido, double valor) {
        return new PagamentoRequisicao(pedido, valor, "PIX", "Cliente", "cliente@teste.com", LocalDateTime.now());
    }

}
