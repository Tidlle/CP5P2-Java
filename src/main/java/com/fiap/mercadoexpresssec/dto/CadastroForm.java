package com.fiap.mercadoexpresssec.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * Dados enviados pela tela de Sign Up (/cadastro).
 */
@Data
public class CadastroForm {

    @NotBlank(message = "Informe seu nome")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    private String nome;

    @NotBlank(message = "Informe seu e-mail")
    @Email(message = "E-mail inválido")
    @Size(max = 120)
    private String email;

    @ToString.Exclude
    @NotBlank(message = "Informe uma senha")
    @Size(min = 6, max = 60, message = "A senha deve ter entre 6 e 60 caracteres")
    private String senha;

    @ToString.Exclude
    @NotBlank(message = "Confirme a senha")
    private String confirmarSenha;

}
