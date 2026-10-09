package com.fiap.mercadoexpresssec;

import com.fiap.mercadoexpresssec.mensageria.EmailConfirmacaoListener;
import com.fiap.mercadoexpresssec.mensageria.EmailConfirmacaoMensagem;
import com.fiap.mercadoexpresssec.mensageria.MensageriaConfig;
import com.fiap.mercadoexpresssec.mensageria.PagamentoRequisicao;
import com.fiap.mercadoexpresssec.mensageria.PagamentoResposta;
import com.fiap.mercadoexpresssec.mensageria.PagamentoRespostaListener;
import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.Role;
import com.fiap.mercadoexpresssec.model.StatusEmail;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.repository.EmailEnviadoRepository;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import com.fiap.mercadoexpresssec.repository.PedidoRepository;
import com.fiap.mercadoexpresssec.service.UsuarioService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Parte 2: carrinho -> RabbitMQ (Banco Tranquilo) -> resposta -> e-mail -> dashboard.
 * O RabbitMQ e o SMTP sao mocks; as mensagens capturadas sao entregues direto aos listeners.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompraFluxoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MercadoRepository mercadoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private EmailEnviadoRepository emailRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PagamentoRespostaListener respostaListener;

    @Autowired
    private EmailConfirmacaoListener emailListener;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    private Mercado produto;
    private String emailCliente;

    @BeforeEach
    void preparar() {
        reset(rabbitTemplate, mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));

        produto = mercadoRepository.save(Mercado.builder().nome("Kiwi (kg) " + UUID.randomUUID())
                .tipo("Fruta").setor("Hortifruti").tamanho(1.0).preco(10.0).custo(6.0).estoque(10).build());

        emailCliente = "cliente-" + UUID.randomUUID() + "@teste.com";
        usuarioService.criar("Cliente Teste", emailCliente, "segredo1", Role.CLIENTE);
    }

    @Test
    void compraAprovadaPeloBancoEnviaEmailEApareceNoDashboard() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. adiciona 3 unidades ao carrinho e finaliza
        mvc.perform(post("/carrinho/adicionar").session(session).with(csrf()).with(user(emailCliente).roles("CLIENTE"))
                        .param("produtoId", produto.getId().toString()).param("quantidade", "3"))
                .andExpect(redirectedUrlPattern("/mercado*"));
        mvc.perform(get("/carrinho").session(session).with(user(emailCliente).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(view().name("carrinho/carrinho"));
        mvc.perform(post("/carrinho/finalizar").session(session).with(csrf()).with(user(emailCliente).roles("CLIENTE"))
                        .param("formaPagamento", "PIX"))
                .andExpect(redirectedUrlPattern("/pedidos/*"));

        // 2. a requisicao foi publicada na exchange do Banco Tranquilo, depois do commit
        ArgumentCaptor<Object> mensagem = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(MensageriaConfig.BANCO_EXCHANGE),
                eq(MensageriaConfig.BANCO_ROUTING_PAGAMENTO), mensagem.capture());
        PagamentoRequisicao requisicao = (PagamentoRequisicao) mensagem.getValue();
        assertThat(requisicao.valor()).isEqualTo(30.0);
        assertThat(requisicao.clienteEmail()).isEqualTo(emailCliente);

        Pedido pedido = pedidoRepository.findByCodigo(requisicao.pedidoCodigo()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(estoque()).isEqualTo(7); // estoque reservado

        // 3. o Banco Tranquilo aprova
        clearInvocations(rabbitTemplate);
        respostaListener.receber(new PagamentoResposta(pedido.getCodigo(), UUID.randomUUID().toString(),
                PagamentoResposta.APROVADO, "Pagamento aprovado", LocalDateTime.now()));
        assertThat(pedidoRepository.findByCodigo(pedido.getCodigo()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);

        // 4. a aprovacao coloca o e-mail na fila
        ArgumentCaptor<Object> emailMsg = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(MensageriaConfig.MERCADO_EXCHANGE),
                eq(MensageriaConfig.MERCADO_ROUTING_EMAIL), emailMsg.capture());

        // 5. o consumidor da fila envia o e-mail e registra no banco; reentrega nao duplica
        emailListener.receber((EmailConfirmacaoMensagem) emailMsg.getValue());
        emailListener.receber((EmailConfirmacaoMensagem) emailMsg.getValue());
        verify(mailSender).send(any(MimeMessage.class));
        assertThat(emailRepository.findByPedidoCodigoOrderByDataEnvioDesc(pedido.getCodigo()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(StatusEmail.ENVIADO);
                    assertThat(e.getDestinatario()).isEqualTo(emailCliente);
                    assertThat(e.getCorpo()).contains("Banco Tranquilo").contains("R$ 30,00");
                });

        // 6. resposta repetida do banco e ignorada (idempotencia)
        respostaListener.receber(new PagamentoResposta(pedido.getCodigo(), "outra", PagamentoResposta.RECUSADO,
                "repetida", LocalDateTime.now()));
        assertThat(pedidoRepository.findByCodigo(pedido.getCodigo()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);

        // 7. telas do cliente e dashboard do ADMIN
        mvc.perform(get("/pedidos/" + pedido.getCodigo()).with(user(emailCliente).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(view().name("pedido/view"));
        mvc.perform(get("/pedidos/" + pedido.getCodigo()).with(user("outro@teste.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/dashboard").with(user("admin@teste.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/dashboard"));
        mvc.perform(get("/admin/dashboard").with(user(emailCliente).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void compraRecusadaDevolveOEstoqueENaoEnviaEmail() throws Exception {
        String json = """
                {"formaPagamento": "CARTAO_CREDITO", "itens": [{"produtoId": %d, "quantidade": 4}]}
                """.formatted(produto.getId());

        String resposta = mvc.perform(post("/api/compras").with(httpBasic(emailCliente, "segredo1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.valorTotal").value(40.0))
                .andExpect(jsonPath("$.origem").value("API"))
                .andReturn().getResponse().getContentAsString();
        String codigo = com.jayway.jsonpath.JsonPath.read(resposta, "$.codigo");
        assertThat(estoque()).isEqualTo(6);

        respostaListener.receber(new PagamentoResposta(codigo, UUID.randomUUID().toString(),
                PagamentoResposta.RECUSADO, "Limite insuficiente", LocalDateTime.now()));

        mvc.perform(get("/api/compras/" + codigo).with(httpBasic(emailCliente, "segredo1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"))
                .andExpect(jsonPath("$.mensagemBanco").value("Limite insuficiente"));
        assertThat(estoque()).isEqualTo(10);
        verify(rabbitTemplate, never()).convertAndSend(eq(MensageriaConfig.MERCADO_EXCHANGE),
                eq(MensageriaConfig.MERCADO_ROUTING_EMAIL), any(Object.class));
    }

    @Test
    void apiValidaEstoqueCredenciaisEPermissoes() throws Exception {
        String semEstoque = """
                {"formaPagamento": "PIX", "itens": [{"produtoId": %d, "quantidade": 50}]}
                """.formatted(produto.getId());
        mvc.perform(post("/api/compras").with(httpBasic(emailCliente, "segredo1"))
                        .contentType(MediaType.APPLICATION_JSON).content(semEstoque))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Compra inválida"));

        mvc.perform(post("/api/compras").with(httpBasic(emailCliente, "segredo1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itens\": []}"))
                .andExpect(status().isBadRequest());

        // Sem credenciais: 401 (e nao redirecionamento para a tela de login)
        mvc.perform(get("/api/compras")).andExpect(status().isUnauthorized());

        // CLIENTE nao compra em nome de outro e-mail; ADMIN pode
        String paraOutro = """
                {"clienteEmail": "alguem@teste.com", "formaPagamento": "PIX", "itens": [{"produtoId": %d, "quantidade": 1}]}
                """.formatted(produto.getId());
        mvc.perform(post("/api/compras").with(httpBasic(emailCliente, "segredo1"))
                        .contentType(MediaType.APPLICATION_JSON).content(paraOutro))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/compras").with(httpBasic("admin@teste.com", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content(paraOutro))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.clienteEmail").value("alguem@teste.com"));

        assertThat(estoque()).isEqualTo(9);
    }

    @Test
    void rabbitForaDoArMarcaErroDeComunicacaoEDevolveEstoque() throws Exception {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        String json = """
                {"formaPagamento": "PIX", "itens": [{"produtoId": %d, "quantidade": 2}]}
                """.formatted(produto.getId());
        String resposta = mvc.perform(post("/api/compras").with(httpBasic(emailCliente, "segredo1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("ERRO_COMUNICACAO"))
                .andReturn().getResponse().getContentAsString();
        String codigo = com.jayway.jsonpath.JsonPath.read(resposta, "$.codigo");

        assertThat(pedidoRepository.findByCodigo(codigo).orElseThrow().getStatus()).isEqualTo(StatusPedido.ERRO_COMUNICACAO);
        assertThat(estoque()).isEqualTo(10);
    }

    @Test
    void falhaNoSmtpFicaRegistradaComoFalha() throws Exception {
        String json = """
                {"formaPagamento": "PIX", "itens": [{"produtoId": %d, "quantidade": 1}]}
                """.formatted(produto.getId());
        String resposta = mvc.perform(post("/api/compras").with(httpBasic(emailCliente, "segredo1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
        String codigo = com.jayway.jsonpath.JsonPath.read(resposta, "$.codigo");
        respostaListener.receber(new PagamentoResposta(codigo, UUID.randomUUID().toString(),
                PagamentoResposta.APROVADO, "ok", LocalDateTime.now()));

        doThrow(new MailSendException("SMTP indisponivel")).when(mailSender).send(any(MimeMessage.class));
        emailListener.receber(new EmailConfirmacaoMensagem(codigo, false));

        assertThat(emailRepository.findByPedidoCodigoOrderByDataEnvioDesc(codigo))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(StatusEmail.FALHA);
                    assertThat(e.getErro()).contains("SMTP indisponivel");
                });
    }

    private int estoque() {
        return mercadoRepository.findById(produto.getId()).orElseThrow().getEstoque();
    }

}
