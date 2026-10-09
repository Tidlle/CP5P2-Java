package com.fiap.mercadoexpresssec.controller;

import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.service.MercadoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mercado")
public class MercadoController {

    private final MercadoService mercadoService;

    public MercadoController(MercadoService mercadoService) {
        this.mercadoService = mercadoService;
    }

    // Read (lista + busca por nome) - ADMIN e CLIENTE
    @GetMapping
    public String listar(@RequestParam(required = false) String busca, Model model) {
        model.addAttribute("mercados", mercadoService.listar(busca));
        model.addAttribute("busca", busca);
        return "mercado/list";
    }

    // Read (detalhe) - ADMIN e CLIENTE
    @GetMapping("/{id:[0-9]+}")
    public String visualizar(@PathVariable Long id, Model model) {
        model.addAttribute("mercado", mercadoService.buscarPorId(id));
        return "mercado/view";
    }

    // Create - formulario - somente ADMIN
    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("mercado", new Mercado());
        model.addAttribute("modoEdicao", false);
        return "mercado/form";
    }

    // Create - submit - somente ADMIN
    @PostMapping
    public String criar(@Valid @ModelAttribute("mercado") Mercado mercado, BindingResult result,
                        Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("modoEdicao", false);
            return "mercado/form";
        }
        mercadoService.salvar(mercado);
        redirectAttributes.addFlashAttribute("sucesso", "Produto cadastrado com sucesso.");
        return "redirect:/mercado";
    }

    // Update - formulario - somente ADMIN
    @GetMapping("/{id:[0-9]+}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("mercado", mercadoService.buscarPorId(id));
        model.addAttribute("modoEdicao", true);
        return "mercado/form";
    }

    // Update - submit - somente ADMIN
    @PostMapping("/{id:[0-9]+}")
    public String atualizar(@PathVariable Long id, @Valid @ModelAttribute("mercado") Mercado mercado,
                            BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            mercado.setId(id);
            model.addAttribute("modoEdicao", true);
            return "mercado/form";
        }
        mercadoService.atualizar(id, mercado);
        redirectAttributes.addFlashAttribute("sucesso", "Produto atualizado com sucesso.");
        return "redirect:/mercado";
    }

    // Delete - somente ADMIN
    @PostMapping("/{id:[0-9]+}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        mercadoService.excluir(id);
        redirectAttributes.addFlashAttribute("sucesso", "Produto excluído com sucesso.");
        return "redirect:/mercado";
    }

    @ExceptionHandler(MercadoService.MercadoNaoEncontradoException.class)
    public String tratarNaoEncontrado(MercadoService.MercadoNaoEncontradoException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("erro", ex.getMessage());
        return "redirect:/mercado";
    }

}
