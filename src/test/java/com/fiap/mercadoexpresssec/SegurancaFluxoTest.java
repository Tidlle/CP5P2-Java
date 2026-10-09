package com.fiap.mercadoexpresssec;

import com.fiap.mercadoexpresssec.model.Role;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import com.fiap.mercadoexpresssec.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaFluxoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MercadoRepository mercadoRepository;

    // Sem RabbitMQ e sem SMTP nos testes (mesmos mocks do CompraFluxoTest: o contexto Spring e reaproveitado)
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void landingPageEPublica() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
    }

    @Test
    void iconesEFonteSaoPublicos() throws Exception {
        mvc.perform(get("/webjars/bootstrap-icons/1.13.1/font/bootstrap-icons.min.css")).andExpect(status().isOk());
        mvc.perform(get("/webjars/bootstrap-icons/1.13.1/font/fonts/bootstrap-icons.woff2")).andExpect(status().isOk());
        mvc.perform(get("/webjars/fontsource__poppins/4.5.1/400.css")).andExpect(status().isOk());
    }

    @Test
    void arquivosEstaticosFicamEmCache() throws Exception {
        mvc.perform(get("/webjars/bootstrap-icons/1.13.1/font/fonts/bootstrap-icons.woff2"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")));

        // Os templates referenciam o CSS com hash de conteudo na URL
        mvc.perform(get("/"))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*/css/style-[0-9a-f]{32}\\.css.*")));
    }

    @Test
    void telaDeLoginPersonalizada() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("form-login")));
    }

    @Test
    void anonimoNaoAcessaProdutos() throws Exception {
        mvc.perform(get("/mercado")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void adminInicialCriadoEDirecionadoAoPainel() throws Exception {
        assertThat(usuarioRepository.findByEmailIgnoreCase("admin@teste.com"))
                .get().extracting("role").isEqualTo(Role.ADMIN);

        mvc.perform(post("/login").with(csrf()).param("email", "admin@teste.com").param("senha", "admin123"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void emailNaoCadastradoRedirecionaParaSignUp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("email", "Novo@Cliente.com").param("senha", "qualquer"))
                .andExpect(redirectedUrl("/cadastro?naoEncontrado"));

        // O cadastro chega com o e-mail pre-preenchido
        mvc.perform(get("/cadastro").session(session).param("naoEncontrado", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("cadastroForm",
                        org.hamcrest.Matchers.hasProperty("email", org.hamcrest.Matchers.is("novo@cliente.com"))));
    }

    @Test
    void senhaErradaVoltaParaLoginComErro() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("email", "admin@teste.com").param("senha", "errada"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void cadastroCriaClienteQueLogaECaiNoCatalogo() throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Maria Cliente")
                        .param("email", "maria@cliente.com")
                        .param("senha", "segredo1")
                        .param("confirmarSenha", "segredo1"))
                .andExpect(redirectedUrl("/login?cadastrado"));

        var usuario = usuarioRepository.findByEmailIgnoreCase("maria@cliente.com").orElseThrow();
        assertThat(usuario.getRole()).isEqualTo(Role.CLIENTE);
        assertThat(usuario.getSenha()).startsWith("$2"); // hash BCrypt

        mvc.perform(post("/login").with(csrf()).param("email", "maria@cliente.com").param("senha", "segredo1"))
                .andExpect(redirectedUrl("/mercado"));
    }

    @Test
    void cadastroComSenhasDiferentesVoltaComErro() throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Joao")
                        .param("email", "joao@cliente.com")
                        .param("senha", "segredo1")
                        .param("confirmarSenha", "outra123"))
                .andExpect(status().isOk())
                .andExpect(view().name("cadastro"))
                .andExpect(model().attributeHasFieldErrors("cadastroForm", "confirmarSenha"));
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteConsultaMasNaoAltera() throws Exception {
        mvc.perform(get("/mercado")).andExpect(status().isOk()).andExpect(view().name("mercado/list"));
        mvc.perform(get("/mercado/novo")).andExpect(status().isForbidden());
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
        mvc.perform(post("/mercado").with(csrf()).param("nome", "X")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminFazCrudCompleto() throws Exception {
        long antes = mercadoRepository.count();

        mvc.perform(post("/mercado").with(csrf())
                        .param("nome", "Laranja pera (kg)").param("tipo", "Fruta").param("setor", "Hortifruti")
                        .param("tamanho", "1").param("preco", "5.49").param("custo", "3.10").param("estoque", "40"))
                .andExpect(redirectedUrl("/mercado"));
        assertThat(mercadoRepository.count()).isEqualTo(antes + 1);

        Long id = mercadoRepository.findByNomeContainingIgnoreCaseOrderByIdAsc("laranja").get(0).getId();

        mvc.perform(get("/mercado/" + id)).andExpect(status().isOk()).andExpect(view().name("mercado/view"));

        mvc.perform(post("/mercado/" + id).with(csrf())
                        .param("nome", "Laranja lima (kg)").param("tipo", "Fruta").param("setor", "Hortifruti")
                        .param("tamanho", "1").param("preco", "6.99").param("custo", "3.90").param("estoque", "35"))
                .andExpect(redirectedUrl("/mercado"));
        assertThat(mercadoRepository.findById(id)).get().extracting("nome").isEqualTo("Laranja lima (kg)");

        mvc.perform(post("/mercado/" + id + "/excluir").with(csrf())).andExpect(redirectedUrl("/mercado"));
        assertThat(mercadoRepository.existsById(id)).isFalse();

        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(view().name("admin/painel"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void produtoInvalidoVoltaParaFormulario() throws Exception {
        mvc.perform(post("/mercado").with(csrf()).param("nome", "").param("preco", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("mercado/form"))
                .andExpect(model().attributeHasFieldErrors("mercado", "nome", "preco"));
    }

}
