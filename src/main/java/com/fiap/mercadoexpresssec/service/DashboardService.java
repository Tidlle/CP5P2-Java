package com.fiap.mercadoexpresssec.service;

import com.fiap.mercadoexpresssec.model.EmailEnviado;
import com.fiap.mercadoexpresssec.model.ItemPedido;
import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.model.OrigemPedido;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.StatusEmail;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.repository.EmailEnviadoRepository;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import com.fiap.mercadoexpresssec.repository.PedidoRepository;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Indicadores do dashboard da gerencia (Parte 2): vendas, lucro, estoque, pedidos e e-mails.
 * Faturamento e lucro consideram somente pedidos PAGOS.
 */
@Service
public class DashboardService {

    public static final int DIAS_GRAFICO = 14;
    public static final int ESTOQUE_BAIXO = 20;

    private final PedidoRepository pedidoRepository;
    private final MercadoRepository mercadoRepository;
    private final EmailEnviadoRepository emailRepository;

    public DashboardService(PedidoRepository pedidoRepository, MercadoRepository mercadoRepository,
                            EmailEnviadoRepository emailRepository) {
        this.pedidoRepository = pedidoRepository;
        this.mercadoRepository = mercadoRepository;
        this.emailRepository = emailRepository;
    }

    @Value
    @Builder
    public static class Dashboard {
        double faturamento;
        double lucro;
        double margemPercentual;
        double ticketMedio;
        long pedidosPagos;
        long itensVendidos;
        double taxaAprovacao;
        Map<StatusPedido, Long> pedidosPorStatus;
        Map<OrigemPedido, Long> pedidosPorOrigem;
        long emailsEnviados;
        long emailsComFalha;
        long pedidosSeed;

        // Series dos graficos (serializadas para JSON no template)
        List<String> dias;
        List<Double> faturamentoPorDia;
        List<Double> lucroPorDia;
        List<String> setores;
        List<Double> faturamentoPorSetor;
        List<String> topProdutos;
        List<Long> topProdutosQuantidade;
        List<String> estoqueProdutos;
        List<Integer> estoqueQuantidade;

        List<Pedido> ultimosPedidos;
        List<Mercado> estoqueBaixo;
        List<EmailEnviado> ultimosEmails;
    }

    @Transactional(readOnly = true)
    public Dashboard montar() {
        List<Pedido> todos = pedidoRepository.findAllByOrderByDataCriacaoDesc();
        List<Pedido> pagos = todos.stream().filter(p -> p.getStatus() == StatusPedido.PAGO).toList();

        double faturamento = pagos.stream().mapToDouble(Pedido::getValorTotal).sum();
        double lucro = pagos.stream().mapToDouble(Pedido::getLucro).sum();
        long itensVendidos = pagos.stream().mapToLong(Pedido::getQuantidadeItens).sum();

        Map<StatusPedido, Long> porStatus = new EnumMap<>(StatusPedido.class);
        for (StatusPedido s : StatusPedido.values()) {
            porStatus.put(s, 0L);
        }
        todos.forEach(p -> porStatus.merge(p.getStatus(), 1L, Long::sum));

        Map<OrigemPedido, Long> porOrigem = new EnumMap<>(OrigemPedido.class);
        for (OrigemPedido o : OrigemPedido.values()) {
            porOrigem.put(o, 0L);
        }
        todos.forEach(p -> porOrigem.merge(p.getOrigem(), 1L, Long::sum));

        long respondidos = porStatus.get(StatusPedido.PAGO) + porStatus.get(StatusPedido.RECUSADO);

        // ---- Vendas e lucro por dia (ultimos 14 dias, dias sem venda aparecem com zero) ----
        LocalDate hoje = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        Map<LocalDate, double[]> porDia = new LinkedHashMap<>();
        for (int i = DIAS_GRAFICO - 1; i >= 0; i--) {
            porDia.put(hoje.minusDays(i), new double[2]);
        }
        for (Pedido p : pagos) {
            double[] acumulado = porDia.get(p.getDataCriacao().toLocalDate());
            if (acumulado != null) {
                acumulado[0] += p.getValorTotal();
                acumulado[1] += p.getLucro();
            }
        }

        // ---- Faturamento por setor e produtos mais vendidos ----
        Map<String, Double> porSetor = new LinkedHashMap<>();
        Map<String, Long> porProduto = new LinkedHashMap<>();
        for (Pedido p : pagos) {
            for (ItemPedido item : p.getItens()) {
                porSetor.merge(item.getProdutoSetor(), item.getSubtotal(), Double::sum);
                porProduto.merge(item.getProdutoNome(), (long) item.getQuantidade(), Long::sum);
            }
        }
        List<Map.Entry<String, Double>> setores = porSetor.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();
        List<Map.Entry<String, Long>> top = porProduto.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5).toList();

        // ---- Estoque atual ----
        List<Mercado> produtos = new ArrayList<>(mercadoRepository.findAllByOrderByIdAsc());
        produtos.sort(Comparator.comparing(Mercado::getEstoque));

        return Dashboard.builder()
                .faturamento(PedidoService.arredondar(faturamento))
                .lucro(PedidoService.arredondar(lucro))
                .margemPercentual(faturamento == 0 ? 0 : PedidoService.arredondar(lucro / faturamento * 100))
                .ticketMedio(pagos.isEmpty() ? 0 : PedidoService.arredondar(faturamento / pagos.size()))
                .pedidosPagos(pagos.size())
                .itensVendidos(itensVendidos)
                .taxaAprovacao(respondidos == 0 ? 0
                        : PedidoService.arredondar(porStatus.get(StatusPedido.PAGO) * 100.0 / respondidos))
                .pedidosPorStatus(porStatus)
                .pedidosPorOrigem(porOrigem)
                .emailsEnviados(emailRepository.countByStatus(StatusEmail.ENVIADO))
                .emailsComFalha(emailRepository.countByStatus(StatusEmail.FALHA))
                .pedidosSeed(porOrigem.get(OrigemPedido.SEED))
                .dias(porDia.keySet().stream().map(fmt::format).toList())
                .faturamentoPorDia(porDia.values().stream().map(v -> PedidoService.arredondar(v[0])).toList())
                .lucroPorDia(porDia.values().stream().map(v -> PedidoService.arredondar(v[1])).toList())
                .setores(setores.stream().map(Map.Entry::getKey).toList())
                .faturamentoPorSetor(setores.stream().map(e -> PedidoService.arredondar(e.getValue())).toList())
                .topProdutos(top.stream().map(Map.Entry::getKey).toList())
                .topProdutosQuantidade(top.stream().map(Map.Entry::getValue).toList())
                .estoqueProdutos(produtos.stream().map(Mercado::getNome).toList())
                .estoqueQuantidade(produtos.stream().map(Mercado::getEstoque).toList())
                .ultimosPedidos(todos.stream().limit(10).collect(Collectors.toList()))
                .estoqueBaixo(produtos.stream().filter(m -> m.getEstoque() < ESTOQUE_BAIXO).toList())
                .ultimosEmails(emailRepository.findTop20ByOrderByDataEnvioDesc())
                .build();
    }

}
