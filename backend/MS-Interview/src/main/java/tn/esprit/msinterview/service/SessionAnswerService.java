package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SessionAnswerService {
    SessionAnswer submitAnswer(Long sessionId, Long questionId, String answerText, String videoUrl, String audioUrl, String codeAnswer);
    SessionAnswer submitCodeAnswer(Long sessionId, Long questionId, String code, CodeLanguage language);
    SessionAnswer retryAnswer(Long sessionId, Long questionId, String answerText, String videoUrl, String audioUrl, String codeAnswer);
    SessionAnswer submitFollowUpAnswer(Long sessionId, Long questionId, Long parentAnswerId, String answerText);
    List<SessionAnswer> getAnswersBySession(Long sessionId);
    SessionAnswer getAnswerById(Long id);
    List<SessionAnswer> getPrimaryAnswersBySession(Long sessionId);

    SessionAnswer submitAudioAnswer(Long sessionId, Long questionId, MultipartFile audioFile, String videoUrl);
    String transcribeAudio(MultipartFile audioFile);
}
