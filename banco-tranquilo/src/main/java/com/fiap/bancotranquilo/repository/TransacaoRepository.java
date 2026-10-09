package com.fiap.bancotranquilo.repository;

import com.fiap.bancotranquilo.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransacaoRepository extends JpaRepository<Transacao, String> {

    Optional<Transacao> findByPedidoCodigo(String pedidoCodigo);

    List<Transacao> findAllByOrderByDataProcessamentoDesc();

}
