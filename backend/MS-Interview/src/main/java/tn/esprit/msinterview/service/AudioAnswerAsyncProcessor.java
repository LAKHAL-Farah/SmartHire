package tn.esprit.msinterview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.WhisperClient;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.repository.SessionAnswerRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioAnswerAsyncProcessor {

    private final SessionAnswerRepository answerRepository;
    private final WhisperClient whisperClient;
    private final AnswerEvaluationService evaluationService;
    private final MLScenarioAnswerService mlScenarioAnswerService;

    @Async("liveExecutor")
    @Transactional
    public void processAudioAnswerAsync(Long answerId, String audioFilePath) {
        try {
            String transcript = whisperClient.transcribeFromWebm(audioFilePath);
            String normalizedTranscript = transcript == null ? "" : transcript.trim();

            SessionAnswer answer = answerRepository.findById(answerId)
                    .orElseThrow(() -> new IllegalStateException("Answer not found for async processing: " + answerId));

            answer.setAnswerText(normalizedTranscript);
            answerRepository.save(answer);

            evaluationService.triggerEvaluation(answerId);

            if (answer.getSession() != null
                    && answer.getSession().getRoleType() == RoleType.AI
                    && !normalizedTranscript.isBlank()) {
                mlScenarioAnswerService.extractAndSaveAsync(answerId, normalizedTranscript);
            }

            log.info("Async audio processing completed for answerId={}", answerId);
        } catch (Exception ex) {
            log.error("Async audio processing failed for answerId={}: {}", answerId, ex.getMessage(), ex);
        }
    }
}
