package com.fiap.mercadoexpresssec.controller.api;

import com.fiap.mercadoexpresssec.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Erros da API em JSON (RFC 7807 - ProblemDetail).
 */
@RestControllerAdvice(assignableTypes = CompraApiController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(PedidoService.CompraInvalidaException.class)
    public ProblemDetail compraInvalida(PedidoService.CompraInvalidaException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Compra inválida", ex.getMessage());
    }

    @ExceptionHandler(PedidoService.PedidoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(PedidoService.PedidoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Pedido não encontrado", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail acessoNegado(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", String.join("; ", erros));
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(HttpMessageNotReadableException ex) {
        return problema(HttpStatus.BAD_REQUEST, "JSON inválido",
                "Verifique o corpo da requisição. formaPagamento aceita PIX, CARTAO_CREDITO ou CARTAO_DEBITO.");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

}
