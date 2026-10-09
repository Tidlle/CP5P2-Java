package com.fiap.mercadoexpresssec.repository;

import com.fiap.mercadoexpresssec.model.Role;
import com.fiap.mercadoexpresssec.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    List<Usuario> findAllByOrderByIdAsc();

}
