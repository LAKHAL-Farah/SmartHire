package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.StudyBuddyMessage;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.StudyBuddyMessageRepository;
import tn.esprit.msroadmap.Repositories.RoadmapStepRepository;
import tn.esprit.msroadmap.ServicesImpl.IStudyBuddyService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class StudyBuddyServiceImpl implements IStudyBuddyService {

    private final StudyBuddyMessageRepository repository;
    private final RoadmapStepRepository stepRepository;

    @Override
    public StudyBuddyMessage chat(Long userId, Long stepId, String userMessage) {
        var step = stepRepository.findById(stepId).orElseThrow(() -> new ResourceNotFoundException("Step not found"));
        StudyBuddyMessage u = new StudyBuddyMessage();
        u.setUserId(userId);
        u.setStep(step);
        u.setRole("user");
        u.setContent(userMessage);
        u.setCreatedAt(LocalDateTime.now());
        repository.save(u);

        StudyBuddyMessage a = new StudyBuddyMessage();
        a.setUserId(userId);
        a.setStep(step);
        a.setRole("assistant");
        a.setContent("Echo: " + userMessage);
        a.setCreatedAt(LocalDateTime.now());
        return repository.save(a);
    }

    @Override
    public List<StudyBuddyMessage> getChatHistory(Long userId, Long stepId) {
        return repository.findByUserIdAndStepIdOrderByCreatedAtAsc(userId, stepId);
    }

    @Override
    public void clearChatHistory(Long userId, Long stepId) {
        repository.deleteByUserIdAndStepId(userId, stepId);
    }
}
