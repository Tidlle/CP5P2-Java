package com.fiap.mercadoexpresssec.service;

import com.fiap.mercadoexpresssec.dto.CadastroForm;
import com.fiap.mercadoexpresssec.model.Role;
import com.fiap.mercadoexpresssec.model.Usuario;
import com.fiap.mercadoexpresssec.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public boolean emailCadastrado(String email) {
        return usuarioRepository.existsByEmailIgnoreCase(normalizarEmail(email));
    }

    /**
     * Cadastro publico (Sign Up): todo novo usuario entra como CLIENTE.
     */
    @Transactional
    public Usuario cadastrarCliente(CadastroForm form) {
        return criar(form.getNome(), form.getEmail(), form.getSenha(), Role.CLIENTE);
    }

    @Transactional
    public Usuario criar(String nome, String email, String senhaPura, Role role) {
        String emailNormalizado = normalizarEmail(email);
        if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailJaCadastradoException(emailNormalizado);
        }
        Usuario usuario = Usuario.builder()
                .nome(nome.trim())
                .email(emailNormalizado)
                .senha(passwordEncoder.encode(senhaPura))
                .role(role)
                .build();
        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmailIgnoreCase(normalizarEmail(email))
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado: " + email));
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public long contarPorRole(Role role) {
        return usuarioRepository.countByRole(role);
    }

    public static class EmailJaCadastradoException extends RuntimeException {
        public EmailJaCadastradoException(String email) {
            super("Já existe um usuário cadastrado com o e-mail " + email);
        }
    }

}
