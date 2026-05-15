package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;

import java.util.List;
import java.util.Map;

public interface InterviewQuestionService {

    List<InterviewQuestion> getAllActive();

    InterviewQuestion getById(Long id);

    List<InterviewQuestion> getByCareerPath(Long careerPathId);

    List<InterviewQuestion> filterForAdaptiveEngine(RoleType role,
                                                    QuestionType type,
                                                    DifficultyLevel difficulty,
                                                    List<Long> excludeIds);

    InterviewQuestion createQuestion(InterviewQuestion question);

    InterviewQuestion updateQuestion(Long id, InterviewQuestion updated);

    void softDeleteQuestion(Long id);

    InterviewQuestion addTag(Long questionId, String tag);

    Map<String, Long> checkBankCoverage();
}

