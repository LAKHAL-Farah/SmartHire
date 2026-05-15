package tn.esprit.msinterview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.dto.InterviewReportDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.InterviewReport;
import tn.esprit.msinterview.service.InterviewReportService;
import tn.esprit.msinterview.service.InterviewSessionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class InterviewReportController {

    private final InterviewReportService interviewReportService;
    private final InterviewSessionService interviewSessionService;
    private final InterviewRequestUserResolver requestUserResolver;

    @PostMapping("/generate/{sessionId}")
    public ResponseEntity<InterviewReportDTO> generateReport(HttpServletRequest request, @PathVariable Long sessionId) {
        Long ownerUserId = interviewSessionService.getSessionById(sessionId).getUserId();
        requestUserResolver.assertCurrentUserOwnsUserId(ownerUserId, request, "Session");

        InterviewReport report = interviewReportService.generateReport(sessionId);
        assertReportOwner(report, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DTOMapper.toReportDTO(report));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<InterviewReportDTO> getReportBySession(HttpServletRequest request, @PathVariable Long sessionId) {
        Long ownerUserId = interviewSessionService.getSessionById(sessionId).getUserId();
        requestUserResolver.assertCurrentUserOwnsUserId(ownerUserId, request, "Session");

        InterviewReport report = interviewReportService.getReportBySession(sessionId);
        assertReportOwner(report, request);
        return ResponseEntity.ok(DTOMapper.toReportDTO(report));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InterviewReportDTO>> getReportsByUser(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        List<InterviewReport> reports = interviewReportService.getReportsByUser(resolvedUserId);
        return ResponseEntity.ok(reports.stream().map(DTOMapper::toReportDTO).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewReportDTO> getReportById(HttpServletRequest request, @PathVariable Long id) {
        InterviewReport report = interviewReportService.getReportById(id);
        assertReportOwner(report, request);
        return ResponseEntity.ok(DTOMapper.toReportDTO(report));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<String> getPdfReport(HttpServletRequest request, @PathVariable Long id) {
        InterviewReport report = interviewReportService.getReportById(id);
        assertReportOwner(report, request);
        String pdfUrl = interviewReportService.generatePdf(id);
        return ResponseEntity.ok(pdfUrl);
    }

    private void assertReportOwner(InterviewReport report, HttpServletRequest request) {
        requestUserResolver.assertCurrentUserOwnsUserId(report.getUserId(), request, "Report");
    }
}
