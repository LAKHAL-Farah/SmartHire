package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.MLScenarioAnswer;

import java.util.List;

public interface MLScenarioAnswerService {
    MLScenarioAnswer extractAndSave(Long answerId, String transcript);
    void extractAndSaveAsync(Long answerId, String transcript);
    MLScenarioAnswer getByAnswer(Long answerId);
    String generateFollowUp(Long answerId);
    void scoreMLAnswer(Long answerId);
    List<MLScenarioAnswer> getBySession(Long sessionId);
}
