package com.fiap.mercadoexpresssec.service;

import com.fiap.mercadoexpresssec.model.Mercado;
import com.fiap.mercadoexpresssec.repository.MercadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MercadoService {

    private final MercadoRepository mercadoRepository;

    public MercadoService(MercadoRepository mercadoRepository) {
        this.mercadoRepository = mercadoRepository;
    }

    @Transactional(readOnly = true)
    public List<Mercado> listar(String busca) {
        if (busca == null || busca.isBlank()) {
            return mercadoRepository.findAllByOrderByIdAsc();
        }
        return mercadoRepository.findByNomeContainingIgnoreCaseOrderByIdAsc(busca.trim());
    }

    @Transactional(readOnly = true)
    public Mercado buscarPorId(Long id) {
        return mercadoRepository.findById(id)
                .orElseThrow(() -> new MercadoNaoEncontradoException(id));
    }

    @Transactional
    public Mercado salvar(Mercado mercado) {
        mercado.setId(null);
        return mercadoRepository.save(mercado);
    }

    @Transactional
    public Mercado atualizar(Long id, Mercado dados) {
        Mercado mercado = buscarPorId(id);
        mercado.setNome(dados.getNome());
        mercado.setTipo(dados.getTipo());
        mercado.setSetor(dados.getSetor());
        mercado.setTamanho(dados.getTamanho());
        mercado.setPreco(dados.getPreco());
        mercado.setCusto(dados.getCusto());
        mercado.setEstoque(dados.getEstoque());
        return mercadoRepository.save(mercado);
    }

    @Transactional
    public void excluir(Long id) {
        if (!mercadoRepository.existsById(id)) {
            throw new MercadoNaoEncontradoException(id);
        }
        mercadoRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long contar() {
        return mercadoRepository.count();
    }

    public static class MercadoNaoEncontradoException extends RuntimeException {
        public MercadoNaoEncontradoException(Long id) {
            super("Produto não encontrado para o id: " + id);
        }
    }

}
