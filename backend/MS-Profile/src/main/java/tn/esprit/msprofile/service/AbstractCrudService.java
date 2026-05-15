package tn.esprit.msprofile.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public abstract class AbstractCrudService<E, R> {

    protected abstract JpaRepository<E, UUID> repository();

    protected abstract R toResponse(E entity);

    protected abstract String resourceName();

    public List<R> findAll() {
        return repository().findAll().stream().map(this::toResponse).toList();
    }

    public R findById(UUID id) {
        return toResponse(requireEntity(id));
    }

    @Transactional
    public void delete(UUID id) {
        repository().delete(requireEntity(id));
    }

    protected E requireEntity(UUID id) {
        return repository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName() + " not found with id=" + id));
    }


    protected String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

