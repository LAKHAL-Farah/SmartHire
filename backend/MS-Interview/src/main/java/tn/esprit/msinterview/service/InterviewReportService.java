package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.InterviewReport;

import java.util.List;

public interface InterviewReportService {
    InterviewReport generateReport(Long sessionId);
    InterviewReport getReportBySession(Long sessionId);
    List<InterviewReport> getReportsByUser(Long userId);
    InterviewReport getReportById(Long id);
    int computePercentileRank(Long sessionId, Double finalScore);
    String generatePdf(Long reportId);
}
