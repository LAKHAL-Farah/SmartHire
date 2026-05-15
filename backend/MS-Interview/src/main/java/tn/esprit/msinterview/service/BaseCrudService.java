package tn.esprit.msinterview.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public abstract class BaseCrudService<T> {

    protected abstract JpaRepository<T, Long> getRepository();

    @Transactional(readOnly = true)
    public List<T> findAll() {
        return getRepository().findAll();
    }

    @Transactional(readOnly = true)
    public T findById(Long id) {
        return getRepository().findById(id).orElse(null);
    }

    public T save(T entity) {
        return getRepository().save(entity);
    }

    public void deleteById(Long id) {
        getRepository().deleteById(id);
    }
}
