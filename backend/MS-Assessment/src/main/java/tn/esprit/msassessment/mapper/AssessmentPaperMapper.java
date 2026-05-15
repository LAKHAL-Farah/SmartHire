package tn.esprit.msassessment.mapper;

import org.springframework.stereotype.Component;
import tn.esprit.msassessment.dto.response.ChoiceView;
import tn.esprit.msassessment.dto.response.QuestionPaperItem;
import tn.esprit.msassessment.entity.AnswerChoice;
import tn.esprit.msassessment.entity.Question;

import java.util.Comparator;
import java.util.List;

@Component
public class AssessmentPaperMapper {

    public QuestionPaperItem toPaperItem(Question q) {
        List<ChoiceView> choices = q.getChoices().stream()
                .sorted(Comparator.comparing(AnswerChoice::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(c -> new ChoiceView(c.getId(), c.getLabel()))
                .toList();
        return new QuestionPaperItem(q.getId(), q.getPrompt(), q.getDifficulty(), q.getPoints(), choices);
    }
}
