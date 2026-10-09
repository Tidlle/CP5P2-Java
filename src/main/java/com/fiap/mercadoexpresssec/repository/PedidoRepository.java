package com.fiap.mercadoexpresssec.repository;

import com.fiap.mercadoexpresssec.model.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "itens")
    Optional<Pedido> findByCodigo(String codigo);

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findByClienteEmailIgnoreCaseOrderByDataCriacaoDesc(String clienteEmail);

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findAllByOrderByDataCriacaoDesc();

}
