package com.fiap.mercadoexpresssec.repository;

import com.fiap.mercadoexpresssec.model.EmailEnviado;
import com.fiap.mercadoexpresssec.model.StatusEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailEnviadoRepository extends JpaRepository<EmailEnviado, Long> {

    boolean existsByPedidoCodigoAndStatus(String pedidoCodigo, StatusEmail status);

    List<EmailEnviado> findByPedidoCodigoOrderByDataEnvioDesc(String pedidoCodigo);

    List<EmailEnviado> findTop20ByOrderByDataEnvioDesc();

    long countByStatus(StatusEmail status);

}
