package tn.esprit.eventmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.DTO.AI.AIResponseDTO;
import tn.esprit.eventmanagement.DTO.event.EventDTO;
import tn.esprit.eventmanagement.DTO.submission.HackathonSubmissionDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.HackathonSubmission;
import tn.esprit.eventmanagement.entities.SubmissionStatus;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.repository.HackathonSubmissionRepository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HackathonSubmissionServiceImpl implements HackathonSubmissionService {
    @Autowired
    private final HackathonSubmissionRepository submissionRepository;
    @Autowired
    private final EventRepository eventRepository;
    @Autowired
    private final AIService aiService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public HackathonSubmissionServiceImpl(HackathonSubmissionRepository submissionRepository, EventRepository eventRepository, AIService aiService, SimpMessagingTemplate messagingTemplate) {
        this.submissionRepository = submissionRepository;
        this.eventRepository = eventRepository;
        this.aiService = aiService;
        this.messagingTemplate = messagingTemplate;
    }







    // 🔄 Entity → DTO
    private HackathonSubmissionDTO mapToDTO(HackathonSubmission sub) {

        HackathonSubmissionDTO dto = new HackathonSubmissionDTO();

        dto.setId(sub.getIdLong());
        dto.setUserId(sub.getUserId());
        dto.setProjectTitle(sub.getProjectTitle());
        dto.setProjectDescription(sub.getProjectDescription());
        dto.setRepoUrl(sub.getRepoUrl());
        dto.setDemoUrl(sub.getDemoUrl());

        dto.setStatus(sub.getStatus() != null ? sub.getStatus().name() : null);

        dto.setOriginalityScore(sub.getOriginalityScore());
        dto.setFeasibilityScore(sub.getFeasibilityScore());
        dto.setTechnicalScore(sub.getTechnicalScore());
        dto.setOverallScore(sub.getOverallScore());

        dto.setAiFeedback(sub.getAiFeedback());
        dto.setRanking(sub.getRanking());

        dto.setSubmittedAt(sub.getSubmittedAt());
        dto.setEvaluatedAt(sub.getEvaluatedAt());

        // ✅ SAFE FIX (important)
        dto.setEventId(
                sub.getEvent() != null ? sub.getEvent().getId() : null
        );

        return dto;
    }
    // 🔄 DTO → Entity
    private HackathonSubmission mapToEntity(HackathonSubmissionDTO dto) {

        HackathonSubmission sub = new HackathonSubmission();

        sub.setIdLong(dto.getId());
        sub.setUserId(dto.getUserId());
        sub.setProjectTitle(dto.getProjectTitle());
        sub.setProjectDescription(dto.getProjectDescription());
        sub.setRepoUrl(dto.getRepoUrl());
        sub.setDemoUrl(dto.getDemoUrl());

        sub.setStatus(SubmissionStatus.valueOf(dto.getStatus()));

        sub.setOriginalityScore(dto.getOriginalityScore());
        sub.setFeasibilityScore(dto.getFeasibilityScore());
        sub.setTechnicalScore(dto.getTechnicalScore());
        sub.setOverallScore(dto.getOverallScore());

        sub.setAiFeedback(dto.getAiFeedback());
        sub.setRanking(dto.getRanking());

        sub.setSubmittedAt(dto.getSubmittedAt());
        sub.setEvaluatedAt(dto.getEvaluatedAt());

        Event event = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        sub.setEvent(event);

        return sub;
    }

    @Override
    public HackathonSubmissionDTO addSubmission(HackathonSubmissionDTO dto) {

        HackathonSubmission sub = mapToEntity(dto);

        sub.setSubmittedAt(LocalDateTime.now());
        sub.setStatus(SubmissionStatus.SUBMITTED);

        return mapToDTO(submissionRepository.save(sub));
    }

    @Override
    public List<HackathonSubmissionDTO> getAllSubmissions() {
        return submissionRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public HackathonSubmissionDTO getSubmissionById(Long id) {
        return submissionRepository.findById(id)
                .map(this::mapToDTO)
                .orElse(null);
    }

    @Override
    public List<HackathonSubmissionDTO> getSubmissionsByEvent(Long eventId) {
        return submissionRepository.findByEventId(eventId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<HackathonSubmissionDTO> getSubmissionsByUser(Long userId) {
        return submissionRepository.findByUserId(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    private AIResponseDTO parseJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, AIResponseDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Erreur parsing JSON IA", e);
        }
    }
    @Override
    public HackathonSubmissionDTO updateSubmission(Long id, HackathonSubmissionDTO dto) {

        HackathonSubmission sub = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        sub.setProjectTitle(dto.getProjectTitle());
        sub.setProjectDescription(dto.getProjectDescription());
        sub.setRepoUrl(dto.getRepoUrl());
        sub.setDemoUrl(dto.getDemoUrl());

        sub.setStatus(SubmissionStatus.valueOf(dto.getStatus()));

        sub.setOriginalityScore(dto.getOriginalityScore());
        sub.setFeasibilityScore(dto.getFeasibilityScore());
        sub.setTechnicalScore(dto.getTechnicalScore());
        sub.setOverallScore(dto.getOverallScore());

        sub.setAiFeedback(dto.getAiFeedback());
        sub.setRanking(dto.getRanking());
        sub.setEvaluatedAt(dto.getEvaluatedAt());

        return mapToDTO(submissionRepository.save(sub));
    }

    @Override
    public void deleteSubmission(Long id) {
        submissionRepository.deleteById(id);
    }

    private String buildNotificationMessage(HackathonSubmission sub) {
        return "🎉 Ton projet '" + sub.getProjectTitle() +
                "' a été évalué ! Score: " + sub.getOverallScore() +
                " | Status: " + sub.getStatus();
    }
    @Transactional
    public HackathonSubmission submitAndAutoEvaluate(Long submissionId) {

        // 1. Récupérer la submission depuis la DB par ID
        HackathonSubmission entity = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found with id: " + submissionId));

        // 2. Mettre à jour le statut et la date de soumission
        entity.setStatus(SubmissionStatus.SUBMITTED);
        entity.setSubmittedAt(LocalDateTime.now());

        // 3. Appel IA
        String aiJson = aiService.evaluateProject(
                entity.getProjectTitle(),
                entity.getProjectDescription()
        );

        AIResponseDTO ai = parseJson(aiJson);

        // 4. Appliquer les scores
        entity.setOriginalityScore(ai.getOriginality());
        entity.setTechnicalScore(ai.getTechnical());
        entity.setFeasibilityScore(ai.getFeasibility());
        entity.setAiFeedback(ai.getFeedback());

        double overall =
                ai.getOriginality() * 0.4 +
                        ai.getTechnical()   * 0.4 +
                        ai.getFeasibility() * 0.2;

        entity.setOverallScore(overall);
        entity.setStatus(overall >= 7
                ? SubmissionStatus.ACCEPTED
                : SubmissionStatus.UNDER_REVIEW);
        entity.setEvaluatedAt(LocalDateTime.now());

        // 5. Un seul save final
        HackathonSubmission finalSaved = submissionRepository.save(entity);
        System.out.println("notification send successfully ");
        // 6. Notification WebSocket
        messagingTemplate.convertAndSend(
                "/topic/results/" + finalSaved.getUserId(),
                buildNotificationMessage(finalSaved)
        );

        return finalSaved;
    }
    @Override
    public Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));
    }
}