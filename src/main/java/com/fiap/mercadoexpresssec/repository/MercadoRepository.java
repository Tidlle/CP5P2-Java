package com.fiap.mercadoexpresssec.repository;

import com.fiap.mercadoexpresssec.model.Mercado;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MercadoRepository extends JpaRepository<Mercado, Long> {

    List<Mercado> findAllByOrderByIdAsc();

    List<Mercado> findByNomeContainingIgnoreCaseOrderByIdAsc(String nome);

    // Trava a linha do produto enquanto o estoque e reservado/devolvido (evita vender o mesmo item duas vezes)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mercado m where m.id = :id")
    Optional<Mercado> findByIdParaAtualizarEstoque(@Param("id") Long id);

    List<Mercado> findByCustoIsNull();

}
