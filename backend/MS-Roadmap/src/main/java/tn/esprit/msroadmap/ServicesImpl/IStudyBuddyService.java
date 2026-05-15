package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.StudyBuddyMessage;

import java.util.List;

public interface IStudyBuddyService {
    StudyBuddyMessage chat(Long userId, Long stepId, String userMessage);
    List<StudyBuddyMessage> getChatHistory(Long userId, Long stepId);
    void clearChatHistory(Long userId, Long stepId);
}
