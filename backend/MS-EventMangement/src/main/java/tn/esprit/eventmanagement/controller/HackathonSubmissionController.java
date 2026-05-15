package tn.esprit.eventmanagement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.DTO.submission.HackathonSubmissionDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.HackathonSubmission;
import tn.esprit.eventmanagement.service.HackathonSubmissionService;
import tn.esprit.eventmanagement.service.HackathonSubmissionServiceImpl;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@CrossOrigin
public class HackathonSubmissionController {

    private final HackathonSubmissionServiceImpl submissionService;

    @Autowired
    public HackathonSubmissionController(HackathonSubmissionServiceImpl submissionService) {
        this.submissionService = submissionService;
    }

    // ✅ Add submission
    @PostMapping
    public HackathonSubmissionDTO addSubmission(@RequestBody HackathonSubmissionDTO dto) {
        return submissionService.addSubmission(dto);
    }

    // ✅ Get all
    @GetMapping
    public List<HackathonSubmissionDTO> getAllSubmissions() {
        return submissionService.getAllSubmissions();
    }

    // ✅ Get by ID
    @GetMapping("/{id}")
    public HackathonSubmissionDTO getSubmissionById(@PathVariable Long id) {
        return submissionService.getSubmissionById(id);
    }

    // ✅ Get by Event
    @GetMapping("/event/{eventId}")
    public List<HackathonSubmissionDTO> getByEvent(@PathVariable Long eventId) {
        return submissionService.getSubmissionsByEvent(eventId);
    }

    // ✅ Get by User
    @GetMapping("/user/{userId}")
    public List<HackathonSubmissionDTO> getByUser(@PathVariable Long userId) {
        return submissionService.getSubmissionsByUser(userId);
    }

    // ✅ Update
    @PutMapping("/update/{id}")
    public HackathonSubmissionDTO updateSubmission(@PathVariable Long id,
                                                   @RequestBody HackathonSubmissionDTO dto) {
        return submissionService.updateSubmission(id, dto);
    }

    // ✅ Delete
    @DeleteMapping("/delete/{id}")
    public void deleteSubmission(@PathVariable Long id) {
        submissionService.deleteSubmission(id);
    }
    @PostMapping("/{id}/evaluate")
    public ResponseEntity<HackathonSubmission> evaluate(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.submitAndAutoEvaluate(id));
    }
    @GetMapping("/eventSub/{id}")
    public Event getEventById(@PathVariable Long id ){
        return submissionService.getEventById(id);
    }
}