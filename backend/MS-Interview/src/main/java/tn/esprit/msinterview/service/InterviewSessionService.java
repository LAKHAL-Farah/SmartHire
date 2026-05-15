package tn.esprit.msinterview.service;

import tn.esprit.msinterview.dto.LiveSessionStartRequest;
import tn.esprit.msinterview.dto.LiveSessionStartResponse;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.enumerated.InterviewMode;
import tn.esprit.msinterview.entity.enumerated.InterviewType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionService {

    InterviewSession startSession(Long userId,
                                  Long careerPathId,
                                  RoleType role,
                                  InterviewMode mode,
                                  InterviewType type,
                                  Integer questionCount);

    InterviewSession getSessionById(Long id);

    List<InterviewSession> getSessionsByUser(Long userId);

    List<InterviewSession> getSessionsByUserAndStatus(Long userId, SessionStatus status);

    InterviewSession pauseSession(Long id);

    InterviewSession resumeSession(Long id);

    InterviewSession completeSession(Long id);

    InterviewSession abandonSession(Long id);

    Optional<InterviewSession> getActiveSession(Long userId);

    void incrementPressureEventCount(Long sessionId);

    LiveSessionStartResponse startLiveSession(LiveSessionStartRequest request);
}

