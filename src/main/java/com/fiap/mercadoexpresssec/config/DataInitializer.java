package com.fiap.mercadoexpresssec.config;

import com.fiap.mercadoexpresssec.model.FormaPagamento;
import com.fiap.mercadoexpresssec.model.ItemPedido;
import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.model.OrigemPedido;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.Role;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import com.fiap.mercadoexpresssec.repository.PedidoRepository;
import com.fiap.mercadoexpresssec.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Carga inicial: garante um usuario ADMIN (o Sign Up publico so cria CLIENTE),
 * alguns produtos de exemplo quando a tabela estiver vazia e, na Parte 2,
 * um historico de compras de exemplo (origem SEED) para o dashboard da gerencia.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    // Custo padrao para produtos cadastrados na Parte 1, antes de existir a coluna "custo"
    private static final double CUSTO_PADRAO_SOBRE_PRECO = 0.6;

    private final UsuarioService usuarioService;
    private final MercadoRepository mercadoRepository;
    private final PedidoRepository pedidoRepository;

    @Value("${app.admin.nome}")
    private String adminNome;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.senha}")
    private String adminSenha;

    @Value("${app.seed-produtos:true}")
    private boolean seedProdutos;

    @Value("${app.seed-pedidos:true}")
    private boolean seedPedidos;

    public DataInitializer(UsuarioService usuarioService, MercadoRepository mercadoRepository,
                           PedidoRepository pedidoRepository) {
        this.usuarioService = usuarioService;
        this.mercadoRepository = mercadoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Override
    public void run(String... args) {
        if (!usuarioService.emailCadastrado(adminEmail)) {
            usuarioService.criar(adminNome, adminEmail, adminSenha, Role.ADMIN);
            log.info("Usuario ADMIN inicial criado: {}", adminEmail);
        }

        if (seedProdutos && mercadoRepository.count() == 0) {
            mercadoRepository.saveAll(List.of(
                    Mercado.builder().nome("Meia cano alto algodão").tipo("Vestuário").setor("Meias").tamanho(39.0).preco(19.90).custo(9.50).estoque(120).build(),
                    Mercado.builder().nome("Meia esportiva kit 3 pares").tipo("Vestuário").setor("Meias").tamanho(42.0).preco(34.90).custo(18.00).estoque(60).build(),
                    Mercado.builder().nome("Detergente neutro 500ml").tipo("Limpeza").setor("Produtos de limpeza").tamanho(0.5).preco(2.99).custo(1.60).estoque(300).build(),
                    Mercado.builder().nome("Água sanitária 2L").tipo("Limpeza").setor("Produtos de limpeza").tamanho(2.0).preco(7.49).custo(4.10).estoque(150).build(),
                    Mercado.builder().nome("Banana prata (kg)").tipo("Fruta").setor("Hortifruti").tamanho(1.0).preco(6.99).custo(3.80).estoque(80).build(),
                    Mercado.builder().nome("Maçã gala (kg)").tipo("Fruta").setor("Hortifruti").tamanho(1.0).preco(9.98).custo(5.90).estoque(70).build()
            ));
            log.info("Produtos de exemplo cadastrados na tabela TDS_Sec_MVC_TB_Mercado");
        }

        preencherCustoDeProdutosAntigos();

        if (seedPedidos && pedidoRepository.count() == 0 && mercadoRepository.count() > 0) {
            criarHistoricoDeExemplo();
        }
    }

    private void preencherCustoDeProdutosAntigos() {
        List<Mercado> semCusto = mercadoRepository.findByCustoIsNull();
        semCusto.forEach(m -> m.setCusto(Math.round(m.getPreco() * CUSTO_PADRAO_SOBRE_PRECO * 100.0) / 100.0));
        if (!semCusto.isEmpty()) {
            mercadoRepository.saveAll(semCusto);
            log.info("Custo padrao (60% do preco) aplicado a {} produto(s) da Parte 1", semCusto.size());
        }
    }

    /**
     * Gera ~3 pedidos por dia nos ultimos 14 dias (semente fixa: sempre os mesmos dados).
     * Esses pedidos nao passam pelo RabbitMQ nem mexem no estoque; ficam marcados com origem SEED.
     * Desative com SEED_PEDIDOS=false.
     */
    private void criarHistoricoDeExemplo() {
        Random random = new Random(2026);
        List<Mercado> produtos = mercadoRepository.findAllByOrderByIdAsc();
        FormaPagamento[] formas = FormaPagamento.values();
        List<Pedido> pedidos = new ArrayList<>();

        for (int dia = 13; dia >= 1; dia--) {
            int quantidadePedidos = 1 + random.nextInt(4);
            for (int n = 0; n < quantidadePedidos; n++) {
                LocalDateTime quando = LocalDate.now().minusDays(dia).atTime(8 + random.nextInt(12), random.nextInt(60));
                boolean recusado = random.nextInt(10) == 0;
                Pedido pedido = Pedido.builder()
                        .codigo(UUID.randomUUID().toString())
                        .clienteNome("Cliente Demonstração")
                        .clienteEmail("cliente.demo@mercado.com")
                        .formaPagamento(formas[random.nextInt(formas.length)])
                        .status(recusado ? StatusPedido.RECUSADO : StatusPedido.PAGO)
                        .origem(OrigemPedido.SEED)
                        .transacaoId(UUID.randomUUID().toString())
                        .mensagemBanco(recusado ? "Histórico de exemplo: limite insuficiente" : "Histórico de exemplo: pagamento aprovado")
                        .dataCriacao(quando)
                        .dataResposta(quando.plusSeconds(2))
                        .build();

                double total = 0;
                double custo = 0;
                int itensDiferentes = 1 + random.nextInt(3);
                List<Mercado> embaralhados = new ArrayList<>(produtos);
                Collections.shuffle(embaralhados, random);
                for (Mercado produto : embaralhados.subList(0, Math.min(itensDiferentes, embaralhados.size()))) {
                    int quantidade = 1 + random.nextInt(4);
                    pedido.adicionarItem(ItemPedido.builder()
                            .produtoId(produto.getId())
                            .produtoNome(produto.getNome())
                            .produtoSetor(produto.getSetor())
                            .quantidade(quantidade)
                            .precoUnitario(produto.getPreco())
                            .custoUnitario(produto.getCusto())
                            .build());
                    total += produto.getPreco() * quantidade;
                    custo += produto.getCusto() * quantidade;
                }
                pedido.setValorTotal(Math.round(total * 100.0) / 100.0);
                pedido.setCustoTotal(Math.round(custo * 100.0) / 100.0);
                pedidos.add(pedido);
            }
        }
        pedidoRepository.saveAll(pedidos);
        log.info("Historico de exemplo criado: {} pedido(s) com origem SEED", pedidos.size());
    }

}
