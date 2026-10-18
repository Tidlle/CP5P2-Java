package com.fiap.mercadoexpresssec.service;

import com.fiap.mercadoexpresssec.model.EmailEnviado;
import com.fiap.mercadoexpresssec.model.Pedido;
import com.fiap.mercadoexpresssec.model.StatusEmail;
import com.fiap.mercadoexpresssec.model.StatusPedido;
import com.fiap.mercadoexpresssec.repository.EmailEnviadoRepository;
import com.fiap.mercadoexpresssec.repository.PedidoRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final PedidoRepository pedidoRepository;
    private final EmailEnviadoRepository emailRepository;

    @Value("${app.mail.remetente}")
    private String remetente;

    public EmailService(JavaMailSender mailSender, ITemplateEngine templateEngine,
                        PedidoRepository pedidoRepository, EmailEnviadoRepository emailRepository) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.pedidoRepository = pedidoRepository;
        this.emailRepository = emailRepository;
    }

    @Transactional
    public EmailEnviado enviarConfirmacao(String pedidoCodigo, boolean reenvio) {
        Pedido pedido = pedidoRepository.findByCodigo(pedidoCodigo).orElse(null);
        if (pedido == null || pedido.getStatus() != StatusPedido.PAGO) {
            log.warn("E-mail nao enviado: pedido {} inexistente ou nao pago", pedidoCodigo);
            return null;
        }
        // Idempotencia: a mesma mensagem entregue duas vezes nao gera dois e-mails
        if (!reenvio && emailRepository.existsByPedidoCodigoAndStatus(pedidoCodigo, StatusEmail.ENVIADO)) {
            log.info("E-mail do pedido {} ja tinha sido enviado; mensagem ignorada", pedidoCodigo);
            return null;
        }

        String assunto = "Pagamento confirmado - Pedido " + codigoCurto(pedidoCodigo) + " | Mercado Express";
        Context context = new Context(Locale.forLanguageTag("pt-BR"));
        context.setVariable("pedido", pedido);
        context.setVariable("codigoCurto", codigoCurto(pedidoCodigo));
        String corpo = templateEngine.process("email/confirmacao-pagamento", context);

        EmailEnviado registro = EmailEnviado.builder()
                .pedidoCodigo(pedidoCodigo)
                .destinatario(pedido.getClienteEmail())
                .assunto(assunto)
                .corpo(corpo)
                .build();
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente);
            helper.setTo(pedido.getClienteEmail());
            helper.setSubject(assunto);
            helper.setText(corpo, true);
            mailSender.send(mensagem);

            registro.setStatus(StatusEmail.ENVIADO);
            log.info("[E-mail] Confirmacao do pedido {} enviada para {}", pedidoCodigo, pedido.getClienteEmail());
        } catch (MailException | MessagingException ex) {
            // Fica registrado como FALHA; o ADMIN pode reenviar pelo dashboard
            registro.setStatus(StatusEmail.FALHA);
            registro.setErro(limitar(ex.getMessage(), 500));
            log.error("[E-mail] Falha ao enviar a confirmacao do pedido {}: {}", pedidoCodigo, ex.getMessage());
        }
        return emailRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public List<EmailEnviado> listarDoPedido(String pedidoCodigo) {
        return emailRepository.findByPedidoCodigoOrderByDataEnvioDesc(pedidoCodigo);
    }

    @Transactional(readOnly = true)
    public List<EmailEnviado> listarRecentes() {
        return emailRepository.findTop20ByOrderByDataEnvioDesc();
    }

    public static String codigoCurto(String codigo) {
        return codigo == null ? "" : codigo.substring(0, Math.min(8, codigo.length())).toUpperCase(Locale.ROOT);
    }

    private static String limitar(String texto, int max) {
        if (texto == null) {
            return "Erro desconhecido";
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

}
