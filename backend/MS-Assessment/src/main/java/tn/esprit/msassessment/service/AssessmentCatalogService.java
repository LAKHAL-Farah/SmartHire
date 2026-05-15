package tn.esprit.msassessment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.dto.response.CategoryResponse;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentCatalogService {

    private final QuestionCategoryRepository categoryRepository;

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getCode(), c.getTitle(), c.getDescription()))
                .toList();
    }
}
