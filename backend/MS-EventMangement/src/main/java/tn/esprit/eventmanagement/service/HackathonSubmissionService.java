package tn.esprit.eventmanagement.service;

import tn.esprit.eventmanagement.DTO.event.EventDTO;
import tn.esprit.eventmanagement.DTO.submission.HackathonSubmissionDTO;
import tn.esprit.eventmanagement.entities.Event;

import java.util.List;

public interface HackathonSubmissionService {

    HackathonSubmissionDTO addSubmission(HackathonSubmissionDTO dto);

    List<HackathonSubmissionDTO> getAllSubmissions();

    HackathonSubmissionDTO getSubmissionById(Long id);

    List<HackathonSubmissionDTO> getSubmissionsByEvent(Long eventId);

    List<HackathonSubmissionDTO> getSubmissionsByUser(Long userId);

    HackathonSubmissionDTO updateSubmission(Long id, HackathonSubmissionDTO dto);

    void deleteSubmission(Long id);

    Event getEventById(Long id);
}