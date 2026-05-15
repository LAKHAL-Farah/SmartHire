package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.ProjectSubmission;

import java.util.List;

public interface IProjectSubmissionService {
    ProjectSubmission submitProject(Long userId, Long suggestionId, String repoUrl);
    List<ProjectSubmission> getSubmissionsByUserId(Long userId);
    ProjectSubmission getSubmissionById(Long submissionId);
    ProjectSubmission retrySubmission(Long submissionId, String newRepoUrl);
    String getAiReviewResult(Long submissionId);
}
